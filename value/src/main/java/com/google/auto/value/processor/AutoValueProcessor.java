/*
 * Copyright (C) 2012 Google, Inc.
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

import static com.google.auto.common.GeneratedAnnotations.generatedAnnotation;
import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.value.processor.ClassNames.AUTO_VALUE_NAME;
import static com.google.auto.value.processor.ClassNames.COPY_ANNOTATIONS_NAME;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.collect.Sets.union;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see AutoValue
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(AUTO_VALUE_NAME)
@SupportedOptions("com.google.auto.value.OmitIdentifiers")
public class AutoValueProcessor extends AutoValueOrOneOfProcessor {
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
    this.extensions = ImmutableList.<AutoValueExtension>copyOf(extensions);
    this.loaderForExtensions = null;
  }

  /**
   * Used to test whether a fully-qualified name is AutoValue.class.getCanonicalName() or one of its
   * nested annotations.
   */
  private static final Pattern AUTO_VALUE_CLASSNAME_PATTERN =
      Pattern.compile(Pattern.quote(AUTO_VALUE_NAME) + "(\\..*)?");

  // Depending on how this AutoValueProcessor was constructed, we might already have a list of
  // extensions when init() is run, or, if `extensions` is null, we have a ClassLoader that will be
  // used to get the list using the ServiceLoader API.
  private ImmutableList<AutoValueExtension> extensions;
  private final ClassLoader loaderForExtensions;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    if (extensions == null) {
      try {
        extensions = ImmutableList.copyOf(
            ServiceLoader.load(AutoValueExtension.class, loaderForExtensions));
        // ServiceLoader.load returns a lazily-evaluated Iterable, so evaluate it eagerly now
        // to discover any exceptions.
      } catch (Throwable t) {
        StringBuilder warning = new StringBuilder();
        warning.append(
            "An exception occurred while looking for AutoValue extensions. "
                + "No extensions will function.");
        if (t instanceof ServiceConfigurationError) {
          warning.append(" This may be due to a corrupt jar file in the compiler's classpath.");
        }
        warning.append(" Exception: ").append(t);
        errorReporter().reportWarning(warning.toString(), null);
        extensions = ImmutableList.of();
      }
    }
  }

  private static String generatedSubclassName(TypeElement type, int depth) {
    return generatedClassName(type, Strings.repeat("$", depth) + "AutoValue_");
  }

  @Override
  void processType(TypeElement type) {
    if (!hasAnnotationMirror(type, AUTO_VALUE_NAME)) {
      // This shouldn't happen unless the compilation environment is buggy,
      // but it has happened in the past and can crash the compiler.
      errorReporter().abortWithError("annotation processor for @AutoValue was invoked with a type"
          + " that does not have that annotation; this is probably a compiler bug", type);
    }
    if (type.getKind() != ElementKind.CLASS) {
      errorReporter().abortWithError("@AutoValue only applies to classes", type);
    }
    if (ancestorIsAutoValue(type)) {
      errorReporter().abortWithError("One @AutoValue class may not extend another", type);
    }
    if (implementsAnnotation(type)) {
      errorReporter().abortWithError("@AutoValue may not be used to implement an annotation"
          + " interface; try using @AutoAnnotation instead", type);
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

    ImmutableSet<ExecutableElement> methods = getLocalAndInheritedMethods(
        type, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
    ImmutableSet<ExecutableElement> abstractMethods = abstractMethodsIn(methods);

    BuilderSpec builderSpec = new BuilderSpec(type, processingEnv, errorReporter());
    Optional<BuilderSpec.Builder> builder = builderSpec.getBuilder();
    ImmutableSet<ExecutableElement> toBuilderMethods;
    if (builder.isPresent()) {
      toBuilderMethods = builder.get().toBuilderMethods(typeUtils(), abstractMethods);
    } else {
      toBuilderMethods = ImmutableSet.of();
    }

    ImmutableSet<ExecutableElement> propertyMethods =
        propertyMethodsIn(immutableSetDifference(abstractMethods, toBuilderMethods));
    ImmutableBiMap<String, ExecutableElement> properties = propertyNameToMethodMap(propertyMethods);

    ExtensionContext context =
        new ExtensionContext(processingEnv, type, properties, abstractMethods);
    ImmutableList<AutoValueExtension> applicableExtensions = applicableExtensions(type, context);
    ImmutableSet<ExecutableElement> consumedMethods = methodsConsumedByExtensions(
        type, applicableExtensions, context, abstractMethods, properties);

    if (!consumedMethods.isEmpty()) {
      ImmutableSet<ExecutableElement> allAbstractMethods = abstractMethods;
      abstractMethods = immutableSetDifference(abstractMethods, consumedMethods);
      toBuilderMethods = immutableSetDifference(toBuilderMethods, consumedMethods);
      propertyMethods =
          propertyMethodsIn(immutableSetDifference(abstractMethods, toBuilderMethods));
      properties = propertyNameToMethodMap(propertyMethods);
      context = new ExtensionContext(processingEnv, type, properties, allAbstractMethods);
    }

    boolean extensionsPresent = !applicableExtensions.isEmpty();
    validateMethods(type, abstractMethods, toBuilderMethods, propertyMethods, extensionsPresent);

    String finalSubclass = generatedSubclassName(type, 0);
    AutoValueTemplateVars vars = new AutoValueTemplateVars();
    vars.pkg = TypeSimplifier.packageNameOf(type);
    vars.origClass = TypeSimplifier.classNameOf(type);
    vars.simpleClassName = TypeSimplifier.simpleNameOf(vars.origClass);
    vars.finalSubclass = TypeSimplifier.simpleNameOf(finalSubclass);
    vars.types = processingEnv.getTypeUtils();
    vars.identifiers =
        !processingEnv.getOptions().containsKey("com.google.auto.value.OmitIdentifiers");
    Map<ObjectMethod, ExecutableElement> methodsToGenerate =
        determineObjectMethodsToGenerate(methods);
    vars.toString = methodsToGenerate.containsKey(ObjectMethod.TO_STRING);
    vars.hashCode = methodsToGenerate.containsKey(ObjectMethod.HASH_CODE);
    vars.equals = methodsToGenerate.containsKey(ObjectMethod.EQUALS);
    vars.equalsParameterType = equalsParameterType(methodsToGenerate);
    defineVarsForType(type, vars, toBuilderMethods, propertyMethods, builder);

    // Only copy annotations from a class if it has @AutoValue.CopyAnnotations.
    if (hasAnnotationMirror(type, COPY_ANNOTATIONS_NAME)) {
      Set<String> excludedAnnotations =
          union(getExcludedClasses(type), getAnnotationsMarkedWithInherited(type));

      vars.annotations = copyAnnotations(type, type, excludedAnnotations);
    } else {
      vars.annotations = ImmutableList.of();
    }

    GwtCompatibility gwtCompatibility = new GwtCompatibility(type);
    vars.gwtCompatibleAnnotation = gwtCompatibility.gwtCompatibleAnnotationString();

    int subclassDepth = writeExtensions(type, context, applicableExtensions);
    String subclass = generatedSubclassName(type, subclassDepth);
    vars.subclass = TypeSimplifier.simpleNameOf(subclass);
    vars.isFinal = (subclassDepth == 0);

    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, type.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(subclass, text, type);
    GwtSerialization gwtSerialization = new GwtSerialization(gwtCompatibility, processingEnv, type);
    gwtSerialization.maybeWriteGwtSerializer(vars);
  }

  /** Implements the semantics of {@code AutoValue.CopyAnnotations}; see its javadoc. */
  private ImmutableList<String> copyAnnotations(
      Element autoValueType,
      Element typeOrMethod,
      Set<String> excludedAnnotations) {
    ImmutableList<AnnotationMirror> annotationsToCopy =
        annotationsToCopy(autoValueType, typeOrMethod, excludedAnnotations);
    return annotationStrings(annotationsToCopy);
  }

  /** Implements the semantics of {@code AutoValue.CopyAnnotations}; see its javadoc. */
  private ImmutableList<AnnotationMirror> annotationsToCopy(
      Element autoValueType,
      Element typeOrMethod,
      Set<String> excludedAnnotations) {
    ImmutableList.Builder<AnnotationMirror> result = ImmutableList.builder();
    for (AnnotationMirror annotation : typeOrMethod.getAnnotationMirrors()) {
      String annotationFqName = getAnnotationFqName(annotation);
      // To be included, the annotation should not be @AutoValue or any of its nested annotations,
      // and it should not be in the excludedAnnotations set.
      if (!AUTO_VALUE_CLASSNAME_PATTERN.matcher(annotationFqName).matches()
          && !excludedAnnotations.contains(annotationFqName)
          && annotationVisibleFrom(annotation, autoValueType)) {
        result.add(annotation);
      }
    }

    return result.build();
  }

  private boolean annotationVisibleFrom(AnnotationMirror annotation, Element from) {
    Element annotationElement = annotation.getAnnotationType().asElement();
    Visibility visibility = Visibility.effectiveVisibilityOfElement(annotationElement);
    switch (visibility) {
      case PUBLIC:
        return true;
      case PROTECTED:
        // If the annotation is protected, it must be inside another class, call it C. If our
        // @AutoValue class is Foo then, for the annotation to be visible, either Foo must be in the
        // same package as C or Foo must be a subclass of C. If the annotation is visible from Foo
        // then it is also visible from our generated subclass AutoValue_Foo.
        // The protected case only applies to method annotations. An annotation on the AutoValue_Foo
        // class itself can't be protected, even if AutoValue_Foo ultimately inherits from the
        // class that defines the annotation. The JLS says "Access is permitted only within the
        // body of a subclass":
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6.2.1
        // AutoValue_Foo is a top-level class, so an annotation on it cannot be in the body of a
        // subclass of anything.
        return getPackage(annotationElement).equals(getPackage(from))
            || typeUtils().isSubtype(
                from.asType(), annotationElement.getEnclosingElement().asType());
      case DEFAULT:
        return getPackage(annotationElement).equals(getPackage(from));
      default:
        return false;
    }
  }

  /**
   * Returns the fully-qualified name of an annotation-mirror, e.g.
   * "com.google.auto.value.AutoValue".
   */
  private static String getAnnotationFqName(AnnotationMirror annotation) {
    return ((QualifiedNameable) annotation.getAnnotationType().asElement())
        .getQualifiedName().toString();
  }

  /**
   * Returns the contents of the {@code AutoValue.CopyAnnotations.exclude} element, as a list of
   * strings that are fully-qualified class names.
   */
  private Set<String> getExcludedClasses(Element element) {
    Optional<AnnotationMirror> maybeAnnotation =
        getAnnotationMirror(element, COPY_ANNOTATIONS_NAME);
    if (!maybeAnnotation.isPresent()) {
      return ImmutableSet.of();
    }

    @SuppressWarnings("unchecked")
    List<AnnotationValue> excludedClasses = (List<AnnotationValue>)
        AnnotationMirrors.getAnnotationValue(maybeAnnotation.get(), "exclude").getValue();
    return excludedClasses
        .stream()
        .map(annotationValue -> MoreTypes.asTypeElement((DeclaredType) annotationValue.getValue()))
        .map(typeElement -> typeElement.getQualifiedName().toString())
        .collect(toSet());
  }

  private static Set<String> getAnnotationsMarkedWithInherited(Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(a -> isAnnotationPresent(a.getAnnotationType().asElement(), Inherited.class))
        .map(a -> getAnnotationFqName(a))
        .collect(toSet());
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
        errorReporter().reportError(
            "More than one extension wants to generate the final class: "
                + finalExtensions.stream().map(this::extensionName).collect(joining(", ")),
            type);
        break;
    }
    return ImmutableList.copyOf(applicableExtensions);
  }

  private ImmutableSet<ExecutableElement> methodsConsumedByExtensions(
      TypeElement type,
      ImmutableList<AutoValueExtension> applicableExtensions,
      ExtensionContext context,
      ImmutableSet<ExecutableElement> abstractMethods,
      ImmutableBiMap<String, ExecutableElement> properties) {
    Set<ExecutableElement> consumed = new HashSet<>();
    for (AutoValueExtension extension : applicableExtensions) {
      Set<ExecutableElement> consumedHere = new HashSet<>();
      for (String consumedProperty : extension.consumeProperties(context)) {
        ExecutableElement propertyMethod = properties.get(consumedProperty);
        if (propertyMethod == null) {
          errorReporter().reportError(
              "Extension " + extensionName(extension)
                  + " wants to consume a property that does not exist: " + consumedProperty,
              type);
        } else {
          consumedHere.add(propertyMethod);
        }
      }
      for (ExecutableElement consumedMethod : extension.consumeMethods(context)) {
        if (!abstractMethods.contains(consumedMethod)) {
          errorReporter().reportError(
              "Extension " + extensionName(extension)
                  + " wants to consume a method that is not one of the abstract methods in this"
                  + " class: " + consumedMethod,
              type);
        } else {
          consumedHere.add(consumedMethod);
        }
      }
      for (ExecutableElement repeat : intersection(consumed, consumedHere)) {
        errorReporter().reportError(
            "Extension " + extensionName(extension) + " wants to consume a method that was already"
                + " consumed by another extension", repeat);
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
        String message = "Abstract method is neither a property getter nor a Builder converter";
        if (extensionsPresent) {
          message += ", and no extension consumed it";
        }
        errorReporter().reportWarning(message, method);
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
      ImmutableSet<ExecutableElement> propertyMethods,
      Optional<BuilderSpec.Builder> builder) {
    // We can't use ImmutableList.toImmutableList() for obscure Google-internal reasons.
    vars.toBuilderMethods =
        ImmutableList.copyOf(toBuilderMethods.stream().map(SimpleMethod::new).collect(toList()));
    vars.generated =
        generatedAnnotation(processingEnv.getElementUtils(), processingEnv.getSourceVersion())
            .map(annotation -> TypeEncoder.encode(annotation.asType()))
            .orElse("");
    ImmutableBiMap<ExecutableElement, String> methodToPropertyName =
        propertyNameToMethodMap(propertyMethods).inverse();
    ImmutableMap<ExecutableElement, ImmutableList<AnnotationMirror>> annotatedPropertyMethods =
        propertyMethodAnnotationMap(type, propertyMethods);
    vars.props = propertySet(type, annotatedPropertyMethods);
    vars.serialVersionUID = getSerialVersionUID(type);
    vars.formalTypes = TypeEncoder.formalTypeParametersString(type);
    vars.actualTypes = TypeSimplifier.actualTypeParametersString(type);
    vars.wildcardTypes = wildcardTypeParametersString(type);
    // Check for @AutoValue.Builder and add appropriate variables if it is present.
    if (builder.isPresent()) {
      builder.get().defineVars(vars, methodToPropertyName);
    }
  }

  private ImmutableMap<ExecutableElement, ImmutableList<AnnotationMirror>>
      propertyMethodAnnotationMap(
          TypeElement type, ImmutableSet<ExecutableElement> propertyMethods) {
    ImmutableSetMultimap<ExecutableElement, String> excludedAnnotationsMap =
        allMethodExcludedAnnotations(propertyMethods);
    ImmutableMap.Builder<ExecutableElement, ImmutableList<AnnotationMirror>> builder =
        ImmutableMap.builder();
    for (ExecutableElement propertyMethod : propertyMethods) {
      builder.put(propertyMethod,
          propertyMethodAnnotations(type, propertyMethod, excludedAnnotationsMap));
    }
    return builder.build();
  }

  @Override
  Optional<String> nullableAnnotationForMethod(
      ExecutableElement propertyMethod, ImmutableList<AnnotationMirror> methodAnnotations) {
    OptionalInt nullableAnnotationIndex = nullableAnnotationIndex(methodAnnotations);
    if (nullableAnnotationIndex.isPresent()) {
      ImmutableList<String> annotations = annotationStrings(methodAnnotations);
      return Optional.of(annotations.get(nullableAnnotationIndex.getAsInt()));
    } else {
      List<? extends AnnotationMirror> typeAnnotations =
          propertyMethod.getReturnType().getAnnotationMirrors();
      return
          nullableAnnotationIndex(typeAnnotations).isPresent()
              ? Optional.of("")
              : Optional.empty();
    }
  }

  private static OptionalInt nullableAnnotationIndex(List<? extends AnnotationMirror> annotations) {
    for (int i = 0; i < annotations.size(); i++) {
      if (isNullable(annotations.get(i))) {
        return OptionalInt.of(i);
      }
    }
    return OptionalInt.empty();
  }

  private static boolean isNullable(AnnotationMirror annotation) {
    return annotation.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable");
  }

  private ImmutableList<AnnotationMirror> propertyMethodAnnotations(
      TypeElement type,
      ExecutableElement method,
      ImmutableSetMultimap<ExecutableElement, String> excludedAnnotationsMap) {
    ImmutableSet<String> excludedAnnotations =
        ImmutableSet.<String>builder()
            .addAll(excludedAnnotationsMap.get(method))
            .add(Override.class.getCanonicalName())
            .build();

    // We need to exclude type annotations from the ones being output on the method, since
    // they will be output as part of the method's return type.
    Set<String> typeAnnotations = method.getReturnType().getAnnotationMirrors().stream()
        .map(a -> a.getAnnotationType().asElement())
        .map(MoreElements::asType)
        .map(e -> e.getQualifiedName().toString())
        .collect(toSet());
    Set<String> excluded = union(excludedAnnotations, typeAnnotations);
    return annotationsToCopy(type, method, excluded);
  }

  private ImmutableSetMultimap<ExecutableElement, String> allMethodExcludedAnnotations(
      Iterable<ExecutableElement> methods) {
    ImmutableSetMultimap.Builder<ExecutableElement, String> result = ImmutableSetMultimap.builder();
    for (ExecutableElement method : methods) {
      result.putAll(method, getExcludedClasses(method));
    }
    return result.build();
  }

  static ImmutableSet<ExecutableElement> prefixedGettersIn(Iterable<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> getters = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      String name = method.getSimpleName().toString();
      // TODO(emcmanus): decide whether getfoo() (without a capital) is a getter. Currently it is.
      boolean get = name.startsWith("get") && !name.equals("get");
      boolean is = name.startsWith("is") && !name.equals("is")
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
