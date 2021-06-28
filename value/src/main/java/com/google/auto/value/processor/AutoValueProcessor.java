/*
 * Copyright 2012 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.value.processor;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.value.processor.ClassNames.AUTO_VALUE_NAME;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;

import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(AUTO_VALUE_NAME)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.DYNAMIC)
public class AutoValueProcessor extends AutoValueishProcessor {
  static final String OMIT_IDENTIFIERS_OPTION = "com.google.auto.value.OmitIdentifiers";

  // We moved MemoizeExtension to a different package, which had an unexpected effect:
  // now if an old version of AutoValue is in the class path, ServiceLoader can pick up both the
  // old and the new versions of MemoizeExtension. So we exclude the old version if we see it.
  // The new version will be bundled with this processor so we should always find it.
  private static final String OLD_MEMOIZE_EXTENSION =
      "com.google.auto.value.extension.memoized.MemoizeExtension";

  public AutoValueProcessor() {
    this(AutoValueProcessor.class.getClassLoader());
  }

  @VisibleForTesting
  AutoValueProcessor(ClassLoader loaderForExtensions) {
    super(AUTO_VALUE_NAME);
    this.extensions = null;
    this.loaderForExtensions = loaderForExtensions;
  }

  @VisibleForTesting
  public AutoValueProcessor(Iterable<? extends AutoValueExtension> extensions) {
    super(AUTO_VALUE_NAME);
    this.extensions = ImmutableList.copyOf(extensions);
    this.loaderForExtensions = null;
  }

  // Depending on how this AutoValueProcessor was constructed, we might already have a list of
  // extensions when init() is run, or, if `extensions` is null, we have a ClassLoader that will be
  // used to get the list using the ServiceLoader API.
  private ImmutableList<AutoValueExtension> extensions;
  private final ClassLoader loaderForExtensions;

  @VisibleForTesting
  static ImmutableList<AutoValueExtension> extensionsFromLoader(ClassLoader loader) {
    return SimpleServiceLoader.load(AutoValueExtension.class, loader).stream()
        .filter(ext -> !ext.getClass().getName().equals(OLD_MEMOIZE_EXTENSION))
        .collect(toImmutableList());
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    if (extensions == null) {
      try {
        extensions = extensionsFromLoader(loaderForExtensions);
      } catch (RuntimeException | Error e) {
        String explain =
            (e instanceof ServiceConfigurationError)
                ? " This may be due to a corrupt jar file in the compiler's classpath."
                : "";
        errorReporter()
            .reportWarning(
                null,
                "[AutoValueExtensionsException] An exception occurred while looking for AutoValue"
                    + " extensions. No extensions will function.%s\n%s",
                explain,
                Throwables.getStackTraceAsString(e));
        extensions = ImmutableList.of();
      }
    }
  }

  @Override
  public ImmutableSet<String> getSupportedOptions() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    AutoValueExtension.IncrementalExtensionType incrementalType =
        extensions.stream()
            .map(e -> e.incrementalType(processingEnv))
            .min(naturalOrder())
            .orElse(AutoValueExtension.IncrementalExtensionType.ISOLATING);
    builder
        .add(OMIT_IDENTIFIERS_OPTION)
        .add(Nullables.NULLABLE_OPTION)
        .addAll(optionsFor(incrementalType));
    for (AutoValueExtension extension : extensions) {
      builder.addAll(extension.getSupportedOptions());
    }
    return builder.build();
  }

  private static ImmutableSet<String> optionsFor(
      AutoValueExtension.IncrementalExtensionType incrementalType) {
    switch (incrementalType) {
      case ISOLATING:
        return ImmutableSet.of(IncrementalAnnotationProcessorType.ISOLATING.getProcessorOption());
      case AGGREGATING:
        return ImmutableSet.of(IncrementalAnnotationProcessorType.AGGREGATING.getProcessorOption());
      case UNKNOWN:
        return ImmutableSet.of();
    }
    throw new AssertionError(incrementalType);
  }

  static String generatedSubclassName(TypeElement type, int depth) {
    return generatedClassName(type, Strings.repeat("$", depth) + "AutoValue_");
  }

  @Override
  void processType(TypeElement type) {
    if (type.getKind() != ElementKind.CLASS) {
      errorReporter()
          .abortWithError(type, "[AutoValueNotClass] @AutoValue only applies to classes");
    }
    if (ancestorIsAutoValue(type)) {
      errorReporter()
          .abortWithError(type, "[AutoValueExtend] One @AutoValue class may not extend another");
    }
    if (implementsAnnotation(type)) {
      errorReporter()
          .abortWithError(
              type,
              "[AutoValueImplAnnotation] @AutoValue may not be used to implement an annotation"
                  + " interface; try using @AutoAnnotation instead");
    }
    checkModifiersIfNested(type);

    // We are going to classify the methods of the @AutoValue class into several categories.
    // This covers the methods in the class itself and the ones it inherits from supertypes.
    // First, the only concrete (non-abstract) methods we are interested in are overrides of
    // Object methods (equals, hashCode, toString), which signal that we should not generate
    // an implementation of those methods.
    // Then, each abstract method is one of the following:
    // (1) A property getter, like "abstract String foo()" or "abstract String getFoo()".
    // (2) A toBuilder() method, which is any abstract no-arg method returning the Builder for
    //     this @AutoValue class.
    // (3) An abstract method that will be consumed by an extension, such as
    //     Parcelable.describeContents() or Parcelable.writeToParcel(Parcel, int).
    // The describeContents() example shows a quirk here: initially we will identify it as a
    // property, which means that we need to reconstruct the list of properties after allowing
    // extensions to consume abstract methods.
    // If there are abstract methods that don't fit any of the categories above, that is an error
    // which we signal explicitly to avoid confusion.

    ImmutableSet<ExecutableElement> methods =
        getLocalAndInheritedMethods(
            type, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
    ImmutableSet<ExecutableElement> abstractMethods = abstractMethodsIn(methods);

    BuilderSpec builderSpec = new BuilderSpec(type, processingEnv, errorReporter());
    Optional<BuilderSpec.Builder> builder = builderSpec.getBuilder();
    ImmutableSet<ExecutableElement> toBuilderMethods;
    if (builder.isPresent()) {
      toBuilderMethods = builder.get().toBuilderMethods(typeUtils(), type, abstractMethods);
    } else {
      toBuilderMethods = ImmutableSet.of();
    }

    ImmutableMap<ExecutableElement, TypeMirror> propertyMethodsAndTypes =
        propertyMethodsIn(immutableSetDifference(abstractMethods, toBuilderMethods), type);
    ImmutableMap<String, ExecutableElement> properties =
        propertyNameToMethodMap(propertyMethodsAndTypes.keySet());

    ExtensionContext context =
        new ExtensionContext(
            processingEnv, type, properties, propertyMethodsAndTypes, abstractMethods);
    ImmutableList<AutoValueExtension> applicableExtensions = applicableExtensions(type, context);
    ImmutableSet<ExecutableElement> consumedMethods =
        methodsConsumedByExtensions(
            type, applicableExtensions, context, abstractMethods, properties);

    if (!consumedMethods.isEmpty()) {
      ImmutableSet<ExecutableElement> allAbstractMethods = abstractMethods;
      abstractMethods = immutableSetDifference(abstractMethods, consumedMethods);
      toBuilderMethods = immutableSetDifference(toBuilderMethods, consumedMethods);
      propertyMethodsAndTypes =
          propertyMethodsIn(immutableSetDifference(abstractMethods, toBuilderMethods), type);
      properties = propertyNameToMethodMap(propertyMethodsAndTypes.keySet());
      context =
          new ExtensionContext(
              processingEnv, type, properties, propertyMethodsAndTypes, allAbstractMethods);
    }

    ImmutableSet<ExecutableElement> propertyMethods = propertyMethodsAndTypes.keySet();
    boolean extensionsPresent = !applicableExtensions.isEmpty();
    validateMethods(type, abstractMethods, toBuilderMethods, propertyMethods, extensionsPresent);

    String finalSubclass = TypeSimplifier.simpleNameOf(generatedSubclassName(type, 0));
    AutoValueTemplateVars vars = new AutoValueTemplateVars();
    vars.types = processingEnv.getTypeUtils();
    vars.identifiers = !processingEnv.getOptions().containsKey(OMIT_IDENTIFIERS_OPTION);
    defineSharedVarsForType(type, methods, vars);
    defineVarsForType(type, vars, toBuilderMethods, propertyMethodsAndTypes, builder);
    vars.builtType = vars.origClass + vars.actualTypes;
    vars.build = "new " + finalSubclass + vars.actualTypes;

    // If we've encountered problems then we might end up invoking extensions with inconsistent
    // state. Anyway we probably don't want to generate code which is likely to provoke further
    // compile errors to add to the ones we've already seen.
    errorReporter().abortIfAnyError();

    GwtCompatibility gwtCompatibility = new GwtCompatibility(type);
    vars.gwtCompatibleAnnotation = gwtCompatibility.gwtCompatibleAnnotationString();

    builder.ifPresent(context::setBuilderContext);
    int subclassDepth = writeExtensions(type, context, applicableExtensions);
    String subclass = generatedSubclassName(type, subclassDepth);
    vars.subclass = TypeSimplifier.simpleNameOf(subclass);
    vars.isFinal = (subclassDepth == 0);
    vars.modifiers = vars.isFinal ? "final " : "abstract ";

    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, type.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(subclass, text, type);
    GwtSerialization gwtSerialization = new GwtSerialization(gwtCompatibility, processingEnv, type);
    gwtSerialization.maybeWriteGwtSerializer(vars, finalSubclass);
  }

  // Invokes each of the given extensions to generate its subclass, and returns the number of
  // hierarchy classes that extensions generated. This number is then the number of $ characters
  // that should precede the name of the AutoValue implementation class.
  // Assume the @AutoValue class is com.example.Foo.Bar. Then if there are no
  // extensions the returned value will be 0, so the AutoValue implementation will be
  // com.example.AutoValue_Foo_Bar. If there is one extension, it will be asked to
  // generate AutoValue_Foo_Bar with parent $AutoValue_Foo_Bar. If it does so (returns
  // non-null) then the returned value will be 1, so the AutoValue implementation will be
  // com.example.$AutoValue_Foo_Bar. Otherwise, the returned value will still be 0. Likewise,
  // if there is a second extension and both extensions return non-null, the first one will
  // generate AutoValue_Foo_Bar with parent $AutoValue_Foo_Bar, the second will generate
  // $AutoValue_Foo_Bar with parent $$AutoValue_Foo_Bar, and the returned value will be 2 for
  // com.example.$$AutoValue_Foo_Bar.
  private int writeExtensions(
      TypeElement type,
      ExtensionContext context,
      ImmutableList<AutoValueExtension> applicableExtensions) {
    int writtenSoFar = 0;
    for (AutoValueExtension extension : applicableExtensions) {
      String parentFqName = generatedSubclassName(type, writtenSoFar + 1);
      String parentSimpleName = TypeSimplifier.simpleNameOf(parentFqName);
      String classFqName = generatedSubclassName(type, writtenSoFar);
      String classSimpleName = TypeSimplifier.simpleNameOf(classFqName);
      boolean isFinal = (writtenSoFar == 0);
      String source = extension.generateClass(context, classSimpleName, parentSimpleName, isFinal);
      if (source != null) {
        source = Reformatter.fixup(source);
        writeSourceFile(classFqName, source, type);
        writtenSoFar++;
      }
    }
    return writtenSoFar;
  }

  private ImmutableList<AutoValueExtension> applicableExtensions(
      TypeElement type, ExtensionContext context) {
    List<AutoValueExtension> applicableExtensions = new ArrayList<>();
    List<AutoValueExtension> finalExtensions = new ArrayList<>();
    for (AutoValueExtension extension : extensions) {
      if (extension.applicable(context)) {
        if (extension.mustBeFinal(context)) {
          finalExtensions.add(extension);
        } else {
          applicableExtensions.add(extension);
        }
      }
    }
    switch (finalExtensions.size()) {
      case 0:
        break;
      case 1:
        applicableExtensions.add(0, finalExtensions.get(0));
        break;
      default:
        errorReporter()
            .reportError(
                type,
                "[AutoValueMultiFinal] More than one extension wants to generate the final class:"
                    + " %s",
                finalExtensions.stream().map(this::extensionName).collect(joining(", ")));
        break;
    }
    return ImmutableList.copyOf(applicableExtensions);
  }

  private ImmutableSet<ExecutableElement> methodsConsumedByExtensions(
      TypeElement type,
      ImmutableList<AutoValueExtension> applicableExtensions,
      ExtensionContext context,
      ImmutableSet<ExecutableElement> abstractMethods,
      ImmutableMap<String, ExecutableElement> properties) {
    Set<ExecutableElement> consumed = new HashSet<>();
    for (AutoValueExtension extension : applicableExtensions) {
      Set<ExecutableElement> consumedHere = new HashSet<>();
      for (String consumedProperty : extension.consumeProperties(context)) {
        ExecutableElement propertyMethod = properties.get(consumedProperty);
        if (propertyMethod == null) {
          errorReporter()
              .reportError(
                  type,
                  "[AutoValueConsumeNonexist] Extension %s wants to consume a property that does"
                      + " not exist: %s",
                  extensionName(extension),
                  consumedProperty);
        } else {
          consumedHere.add(propertyMethod);
        }
      }
      for (ExecutableElement consumedMethod : extension.consumeMethods(context)) {
        if (!abstractMethods.contains(consumedMethod)) {
          errorReporter()
              .reportError(
                  type,
                  "[AutoValueConsumeNotAbstract] Extension %s wants to consume a method that is"
                      + " not one of the abstract methods in this class: %s",
                  extensionName(extension),
                  consumedMethod);
        } else {
          consumedHere.add(consumedMethod);
        }
      }
      for (ExecutableElement repeat : intersection(consumed, consumedHere)) {
        errorReporter()
            .reportError(
                repeat,
                "[AutoValueMultiConsume] Extension %s wants to consume a method that was already"
                    + " consumed by another extension",
                extensionName(extension));
      }
      consumed.addAll(consumedHere);
    }
    return ImmutableSet.copyOf(consumed);
  }

  private void validateMethods(
      TypeElement type,
      ImmutableSet<ExecutableElement> abstractMethods,
      ImmutableSet<ExecutableElement> toBuilderMethods,
      ImmutableSet<ExecutableElement> propertyMethods,
      boolean extensionsPresent) {
    for (ExecutableElement method : abstractMethods) {
      if (propertyMethods.contains(method)) {
        checkReturnType(type, method);
      } else if (!toBuilderMethods.contains(method)
          && objectMethodToOverride(method) == ObjectMethod.NONE) {
        // This could reasonably be an error, were it not for an Eclipse bug in
        // ElementUtils.override that sometimes fails to recognize that one method overrides
        // another, and therefore leaves us with both an abstract method and the subclass method
        // that overrides it. This shows up in AutoValueTest.LukesBase for example.
        String extensionMessage = extensionsPresent ? ", and no extension consumed it" : "";
        errorReporter()
            .reportWarning(
                method,
                "[AutoValueBuilderWhat] Abstract method is neither a property getter nor a Builder"
                    + " converter%s",
                extensionMessage);
      }
    }
    errorReporter().abortIfAnyError();
  }

  private String extensionName(AutoValueExtension extension) {
    return extension.getClass().getName();
  }

  private void defineVarsForType(
      TypeElement type,
      AutoValueTemplateVars vars,
      ImmutableSet<ExecutableElement> toBuilderMethods,
      ImmutableMap<ExecutableElement, TypeMirror> propertyMethodsAndTypes,
      Optional<BuilderSpec.Builder> maybeBuilder) {
    ImmutableSet<ExecutableElement> propertyMethods = propertyMethodsAndTypes.keySet();
    vars.toBuilderMethods =
        toBuilderMethods.stream().map(SimpleMethod::new).collect(toImmutableList());
    vars.toBuilderConstructor = !vars.toBuilderMethods.isEmpty();
    ImmutableListMultimap<ExecutableElement, AnnotationMirror> annotatedPropertyFields =
        propertyFieldAnnotationMap(type, propertyMethods);
    ImmutableListMultimap<ExecutableElement, AnnotationMirror> annotatedPropertyMethods =
        propertyMethodAnnotationMap(type, propertyMethods);
    vars.props =
        propertySet(propertyMethodsAndTypes, annotatedPropertyFields, annotatedPropertyMethods);
    // Check for @AutoValue.Builder and add appropriate variables if it is present.
    maybeBuilder.ifPresent(
        builder -> {
          ImmutableBiMap<ExecutableElement, String> methodToPropertyName =
              propertyNameToMethodMap(propertyMethods).inverse();
          builder.defineVarsForAutoValue(vars, methodToPropertyName);
          vars.builderName = "Builder";
          vars.builderAnnotations = copiedClassAnnotations(builder.builderType());
        });
  }

  @Override
  Optional<String> nullableAnnotationForMethod(ExecutableElement propertyMethod) {
    return nullableAnnotationFor(propertyMethod, propertyMethod.getReturnType());
  }

  static ImmutableSet<ExecutableElement> prefixedGettersIn(Iterable<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> getters = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      String name = method.getSimpleName().toString();
      // TODO(emcmanus): decide whether getfoo() (without a capital) is a getter. Currently it is.
      boolean get = name.startsWith("get") && !name.equals("get");
      boolean is =
          name.startsWith("is")
              && !name.equals("is")
              && method.getReturnType().getKind() == TypeKind.BOOLEAN;
      if (get || is) {
        getters.add(method);
      }
    }
    return getters.build();
  }

  private boolean ancestorIsAutoValue(TypeElement type) {
    while (true) {
      TypeMirror parentMirror = type.getSuperclass();
      if (parentMirror.getKind() == TypeKind.NONE) {
        return false;
      }
      TypeElement parentElement = (TypeElement) typeUtils().asElement(parentMirror);
      if (hasAnnotationMirror(parentElement, AUTO_VALUE_NAME)) {
        return true;
      }
      type = parentElement;
    }
  }

  private boolean implementsAnnotation(TypeElement type) {
    return typeUtils().isAssignable(type.asType(), getTypeMirror(Annotation.class));
  }

  private TypeMirror getTypeMirror(Class<?> c) {
    return processingEnv.getElementUtils().getTypeElement(c.getName()).asType();
  }

  private static <E> ImmutableSet<E> immutableSetDifference(ImmutableSet<E> a, ImmutableSet<E> b) {
    if (Collections.disjoint(a, b)) {
      return a;
    } else {
      return ImmutableSet.copyOf(difference(a, b));
    }
  }
}
