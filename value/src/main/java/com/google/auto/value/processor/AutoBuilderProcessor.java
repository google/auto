/*
 * Copyright 2021 Google LLC
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
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableMap;
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.auto.value.processor.AutoValueProcessor.OMIT_IDENTIFIERS_OPTION;
import static com.google.auto.value.processor.ClassNames.AUTO_ANNOTATION_NAME;
import static com.google.auto.value.processor.ClassNames.AUTO_BUILDER_NAME;
import static com.google.auto.value.processor.ClassNames.KOTLIN_METADATA_NAME;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.AnnotationValues;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.service.AutoService;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import kotlinx.metadata.Flag;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * Javac annotation processor (compiler plugin) for builders; user code never references this class.
 *
 * @see <a href="https://github.com/google/auto/tree/main/value">AutoValue User's Guide</a>
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(AUTO_BUILDER_NAME)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
public class AutoBuilderProcessor extends AutoValueishProcessor {
  private static final String ALLOW_OPTION = "com.google.auto.value.AutoBuilderIsUnstable";
  private static final String AUTO_ANNOTATION_CLASS_PREFIX = "AutoBuilderAnnotation_";

  public AutoBuilderProcessor() {
    super(AUTO_BUILDER_NAME, /* appliesToInterfaces= */ true);
  }

  @Override
  public Set<String> getSupportedOptions() {
    return ImmutableSet.of(OMIT_IDENTIFIERS_OPTION, ALLOW_OPTION);
  }

  private TypeMirror javaLangVoid;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    javaLangVoid = elementUtils().getTypeElement("java.lang.Void").asType();
  }

  // The handling of @AutoBuilder to generate annotation implementations needs some explanation.
  // Suppose we have this:
  //
  //   public class Annotations {
  //     @interface MyAnnot {...}
  //
  //     @AutoBuilder(ofClass = MyAnnot.class)
  //     public interface MyAnnotBuilder {
  //       ...
  //       MyAnnot build();
  //     }
  //
  //     public static MyAnnotBuilder myAnnotBuilder() {
  //       return new AutoBuilder_Annotations_MyAnnotBuilder();
  //     }
  //   }
  //
  // Then we will detect that the ofClass type is an annotation. Since annotations can have neither
  // constructors nor static methods, we know this isn't a regular @AutoBuilder. We want to
  // generate an implementation of the MyAnnot annotation, and we know we can do that if we have a
  // suitable @AutoAnnotation method. So we generate:
  //
  //   class AutoBuilderAnnotation_Annotations_MyAnnotBuilder {
  //     @AutoAnnotation
  //     static MyAnnot newAnnotation(...) {
  //       return new AutoAnnotation_AutoBuilderAnnotation_Annotations_MyAnnotBuilder_newAnnotation(
  //           ...);
  //     }
  //   }
  //
  // We also "defer" MyAnnotBuilder so that it will be considered again on the next round. At that
  // point the method AutoBuilderAnnotation_Annotations_MyAnnotBuilder.newAnnotation will exist, and
  // we just need to tweak the handling of MyAnnotBuilder so that it behaves as if it were:
  //
  //   @AutoBuilder(
  //       callMethod = newAnnotation,
  //       ofClass = AutoBuilderAnnotation_Annotations_MyAnnotBuilder.class)
  //   interface MyAnnotBuilder {...}
  //
  // Using AutoAnnotation and AutoBuilder together you'd write
  //
  // @AutoAnnotation static MyAnnot newAnnotation(...) { ... }
  //
  // @AutoBuilder(callMethod = "newAnnotation", ofClass = Some.class)
  // interface MyAnnotBuilder { ... }
  //
  // If you set ofClass to an annotation class, AutoBuilder generates the @AutoAnnotation method for
  // you and then acts as if your @AutoBuilder annotation pointed to it.

  @Override
  void processType(TypeElement autoBuilderType) {
    if (processingEnv.getOptions().containsKey(ALLOW_OPTION)) {
      errorReporter().reportWarning(autoBuilderType, "The -A%s option is obsolete", ALLOW_OPTION);
    }
    // The annotation is guaranteed to be present by the contract of Processor#process
    AnnotationMirror autoBuilderAnnotation =
        getAnnotationMirror(autoBuilderType, AUTO_BUILDER_NAME).get();
    TypeElement ofClass = getOfClass(autoBuilderType, autoBuilderAnnotation);
    checkModifiersIfNested(ofClass, autoBuilderType, "AutoBuilder ofClass");
    String callMethod = findCallMethodValue(autoBuilderAnnotation);
    if (ofClass.getKind() == ElementKind.ANNOTATION_TYPE) {
      buildAnnotation(autoBuilderType, ofClass, callMethod);
    } else {
      processType(autoBuilderType, ofClass, callMethod);
    }
  }

  private void processType(TypeElement autoBuilderType, TypeElement ofClass, String callMethod) {
    ImmutableSet<ExecutableElement> methods =
        abstractMethodsIn(
            getLocalAndInheritedMethods(autoBuilderType, typeUtils(), elementUtils()));
    Executable executable = findExecutable(ofClass, callMethod, autoBuilderType, methods);
    BuilderSpec builderSpec = new BuilderSpec(ofClass, processingEnv, errorReporter());
    BuilderSpec.Builder builder = builderSpec.new Builder(autoBuilderType);
    TypeMirror builtType = executable.builtType();
    ImmutableMap<String, String> propertyInitializers =
        propertyInitializers(autoBuilderType, executable);
    Nullables nullables = Nullables.fromMethods(processingEnv, methods);
    Optional<BuilderMethodClassifier<VariableElement>> maybeClassifier =
        BuilderMethodClassifierForAutoBuilder.classify(
            methods,
            errorReporter(),
            processingEnv,
            executable,
            builtType,
            autoBuilderType,
            propertyInitializers.keySet(),
            nullables);
    if (!maybeClassifier.isPresent() || errorReporter().errorCount() > 0) {
      // We've already output one or more error messages.
      return;
    }
    BuilderMethodClassifier<VariableElement> classifier = maybeClassifier.get();
    ImmutableMap<String, String> propertyToGetterName =
        propertyToGetterName(executable, autoBuilderType);
    AutoBuilderTemplateVars vars = new AutoBuilderTemplateVars();
    vars.props =
        propertySet(
            executable,
            propertyToGetterName,
            propertyInitializers,
            nullables);
    builder.defineVars(vars, classifier);
    vars.identifiers = !processingEnv.getOptions().containsKey(OMIT_IDENTIFIERS_OPTION);
    String generatedClassName = generatedClassName(autoBuilderType, "AutoBuilder_");
    vars.builderName = TypeSimplifier.simpleNameOf(generatedClassName);
    vars.builtType = TypeEncoder.encode(builtType);
    vars.builderAnnotations = copiedClassAnnotations(autoBuilderType);
    Optional<String> forwardingClassName = maybeForwardingClass(autoBuilderType, executable);
    vars.build =
        forwardingClassName
            .map(n -> TypeSimplifier.simpleNameOf(n) + ".of")
            .orElseGet(executable::invoke);
    vars.toBuilderConstructor = !propertyToGetterName.isEmpty();
    vars.toBuilderMethods = ImmutableList.of();
    defineSharedVarsForType(
        autoBuilderType, ImmutableSet.of(), nullables, vars);
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, autoBuilderType.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(generatedClassName, text, autoBuilderType);
    forwardingClassName.ifPresent(
        n -> generateForwardingClass(n, executable, builtType, autoBuilderType));
  }

  /**
   * Generates a class that will call the synthetic Kotlin constructor that is used to specify which
   * optional parameters are defaulted. Because it is synthetic, it can't be called from Java source
   * code. Instead, Java source code calls the {@code of} method in the class we generate here.
   */
  private void generateForwardingClass(
      String forwardingClassName,
      Executable executable,
      TypeMirror builtType,
      TypeElement autoBuilderType) {
    // The synthetic constructor has the same parameters as the user-written constructor, plus as
    // many `int` bitmasks as are needed to have one bit for each of those parameters, plus a dummy
    // parameter of type kotlin.jvm.internal.DefaultConstructorMarker to avoid confusion with a
    // constructor that might have its own `int` parameters where the bitmasks are.
    // This ABI is not publicly specified (as far as we know) but JetBrains has confirmed orally
    // that it unlikely to change, and if it does it will be in a backward-compatible way.
    ImmutableList.Builder<TypeMirror> constructorParameters = ImmutableList.builder();
    executable.parameters().stream()
        .map(Element::asType)
        .map(typeUtils()::erasure)
        .forEach(constructorParameters::add);
    int bitmaskCount = (executable.optionalParameterCount() + 31) / 32;
    constructorParameters.addAll(
        Collections.nCopies(bitmaskCount, typeUtils().getPrimitiveType(TypeKind.INT)));
    String marker = "kot".concat("lin.jvm.internal.DefaultConstructorMarker"); // defeat shading
    constructorParameters.add(elementUtils().getTypeElement(marker).asType());
    byte[] classBytes =
        ForwardingClassGenerator.makeConstructorForwarder(
            forwardingClassName, builtType, constructorParameters.build());
    try {
      JavaFileObject trampoline =
          processingEnv.getFiler().createClassFile(forwardingClassName, autoBuilderType);
      try (OutputStream out = trampoline.openOutputStream()) {
        out.write(classBytes);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Optional<String> maybeForwardingClass(
      TypeElement autoBuilderType, Executable executable) {
    return executable.optionalParameterCount() == 0
        ? Optional.empty()
        : Optional.of(generatedClassName(autoBuilderType, "AutoBuilderBridge_"));
  }

  private ImmutableSet<Property> propertySet(
      Executable executable,
      Map<String, String> propertyToGetterName,
      ImmutableMap<String, String> builderInitializers,
      Nullables nullables) {
    // Fix any parameter names that are reserved words in Java. Java source code can't have
    // such parameter names, but Kotlin code might, for example.
    Map<VariableElement, String> identifiers =
        executable.parameters().stream()
            .collect(toMap(v -> v, v -> v.getSimpleName().toString()));
    fixReservedIdentifiers(identifiers);
    return executable.parameters().stream()
        .map(
            v -> {
              String name = v.getSimpleName().toString();
              return newProperty(
                  v,
                  identifiers.get(v),
                  propertyToGetterName.get(name),
                  Optional.ofNullable(builderInitializers.get(name)),
                  executable.isOptional(name),
                  nullables);
            })
        .collect(toImmutableSet());
  }

  private Property newProperty(
      VariableElement var,
      String identifier,
      String getterName,
      Optional<String> builderInitializer,
      boolean hasDefault,
      Nullables nullables) {
    String name = var.getSimpleName().toString();
    TypeMirror type = var.asType();
    Optional<String> nullableAnnotation = nullableAnnotationFor(var, var.asType());
    return new Property(
        name,
        identifier,
        TypeEncoder.encode(type),
        type,
        nullableAnnotation,
        nullables,
        getterName,
        builderInitializer,
        hasDefault);
  }

  private ImmutableMap<String, String> propertyInitializers(
      TypeElement autoBuilderType, Executable executable) {
    boolean autoAnnotation =
        MoreElements.getAnnotationMirror(executable.executableElement(), AUTO_ANNOTATION_NAME)
            .isPresent();
    if (!autoAnnotation) {
      return ImmutableMap.of();
    }
    // We expect the return type of an @AutoAnnotation method to be an annotation type. If it isn't,
    // AutoAnnotation will presumably complain, so we don't need to complain further.
    TypeMirror returnType = executable.builtType();
    if (!returnType.getKind().equals(TypeKind.DECLARED)) {
      return ImmutableMap.of();
    }
    // This might not actually be an annotation (if the code is wrong), but if that's the case we
    // just won't see any contained ExecutableElement where getDefaultValue() returns something.
    TypeElement annotation = MoreTypes.asTypeElement(returnType);
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (ExecutableElement method : methodsIn(annotation.getEnclosedElements())) {
      AnnotationValue defaultValue = method.getDefaultValue();
      if (defaultValue != null) {
        String memberName = method.getSimpleName().toString();
        builder.put(
            memberName,
            AnnotationOutput.sourceFormForInitializer(
                defaultValue, processingEnv, memberName, autoBuilderType));
      }
    }
    return builder.build();
  }

  /**
   * Returns a map from property names to the corresponding getters in the built type. The built
   * type is the return type of the given {@code executable}, and the property names are the names
   * of its parameters. If the return type is a {@link DeclaredType} {@code Foo} and if every
   * property name {@code bar} matches a method {@code bar()} or {@code getBar()} in {@code Foo},
   * then the method returns a map where {@code bar} maps to {@code bar} or {@code getBar}. If these
   * conditions are not met then the method returns an empty map.
   *
   * <p>The method name match is case-insensitive, so we will also accept {@code baR()} or {@code
   * getbar()}. For a property of type {@code boolean}, we also accept {@code isBar()} (or {@code
   * isbar()} etc).
   *
   * <p>The return type of each getter method must match the type of the corresponding parameter
   * exactly. This will always be true for our principal use cases, Java records and Kotlin data
   * classes. For other use cases, we may in the future accept getters where we know how to convert,
   * for example if the getter has type {@code ImmutableList<Baz>} and the parameter has type
   * {@code Baz[]}. We already have similar logic for the parameter types of builder setters.
   */
  private ImmutableMap<String, String> propertyToGetterName(
      Executable executable, TypeElement autoBuilderType) {
    TypeMirror builtType = executable.builtType();
    if (builtType.getKind() != TypeKind.DECLARED) {
      return ImmutableMap.of();
    }
    TypeElement type = MoreTypes.asTypeElement(builtType);
    Map<String, ExecutableElement> nameToMethod =
        MoreElements.getLocalAndInheritedMethods(type, typeUtils(), elementUtils()).stream()
            .filter(m -> m.getParameters().isEmpty())
            .filter(m -> !m.getModifiers().contains(Modifier.STATIC))
            .filter(m -> visibleFrom(autoBuilderType, getPackage(autoBuilderType)))
            .collect(
                toMap(
                    m -> m.getSimpleName().toString(),
                    m -> m,
                    (a, b) -> a,
                    () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
    ImmutableMap<String, String> propertyToGetterName =
        executable.parameters().stream()
            .map(
                param -> {
                  String name = param.getSimpleName().toString();
                  // Parameter name is `bar`; we look for `bar()` and `getBar()` (or `getbar()` etc)
                  // in that order. If `bar` is boolean we also look for `isBar()`.
                  ExecutableElement getter = nameToMethod.get(name);
                  if (getter == null) {
                    getter = nameToMethod.get("get" + name);
                    if (getter == null && param.asType().getKind() == TypeKind.BOOLEAN) {
                      getter = nameToMethod.get("is" + name);
                    }
                  }
                  if (getter != null
                      && !typeUtils().isAssignable(getter.getReturnType(), param.asType())
                      && !MoreTypes.equivalence()
                          .equivalent(getter.getReturnType(), param.asType())) {
                    // TODO(b/268680785): we should not need to have two type checks here
                    getter = null;
                  }
                  return new SimpleEntry<>(name, getter);
                })
            .filter(entry -> entry.getValue() != null)
            .collect(
                toImmutableMap(
                    Map.Entry::getKey, entry -> entry.getValue().getSimpleName().toString()));
    return (propertyToGetterName.size() == executable.parameters().size())
        ? propertyToGetterName
        : ImmutableMap.of();
  }

  private Executable findExecutable(
      TypeElement ofClass,
      String callMethod,
      TypeElement autoBuilderType,
      ImmutableSet<ExecutableElement> methodsInAutoBuilderType) {
    ImmutableList<Executable> executables =
        findRelevantExecutables(ofClass, callMethod, autoBuilderType);
    String description =
        callMethod.isEmpty() ? "constructor" : "static method named \"" + callMethod + "\"";
    switch (executables.size()) {
      case 0:
        throw errorReporter()
            .abortWithError(
                autoBuilderType,
                "[AutoBuilderNoVisible] No visible %s for %s",
                description,
                ofClass);
      case 1:
        return executables.get(0);
      default:
        return matchingExecutable(
            autoBuilderType, executables, methodsInAutoBuilderType, description);
    }
  }

  private ImmutableList<Executable> findRelevantExecutables(
      TypeElement ofClass, String callMethod, TypeElement autoBuilderType) {
    Optional<AnnotationMirror> kotlinMetadata = kotlinMetadataAnnotation(ofClass);
    List<? extends Element> elements = ofClass.getEnclosedElements();
    Stream<Executable> relevantExecutables =
        callMethod.isEmpty()
            ? kotlinMetadata
                .map(a -> kotlinConstructorsIn(a, ofClass).stream())
                .orElseGet(() -> constructorsIn(elements).stream().map(Executable::of))
            : methodsIn(elements).stream()
                .filter(m -> m.getSimpleName().contentEquals(callMethod))
                .filter(m -> m.getModifiers().contains(Modifier.STATIC))
                .map(Executable::of);
    return relevantExecutables
        .filter(e -> visibleFrom(e.executableElement(), getPackage(autoBuilderType)))
        .collect(toImmutableList());
  }

  private Executable matchingExecutable(
      TypeElement autoBuilderType,
      List<Executable> executables,
      ImmutableSet<ExecutableElement> methodsInAutoBuilderType,
      String description) {
    // There's more than one visible executable (constructor or method). We try to find the one that
    // corresponds to the methods in the @AutoBuilder interface. This is a bit approximate. We're
    // basically just looking for an executable where all the parameter names correspond to setters
    // or property builders in the interface. We might find out after choosing one that it is wrong
    // for whatever reason (types don't match, spurious methods, etc). But it is likely that if the
    // names are all accounted for in the methods, and if there's no other matching executable with
    // more parameters, then this is indeed the one we want. If we later get errors when we try to
    // analyze the interface in detail, those are probably legitimate errors and not because we
    // picked the wrong executable.
    ImmutableList<Executable> matches =
        executables.stream()
            .filter(x -> executableMatches(x, methodsInAutoBuilderType))
            .collect(toImmutableList());
    switch (matches.size()) {
      case 0:
        throw errorReporter()
            .abortWithError(
                autoBuilderType,
                "[AutoBuilderNoMatch] Property names do not correspond to the parameter names of"
                    + " any %s:\n%s",
                description,
                executableListString(executables));
      case 1:
        return matches.get(0);
      default:
        // More than one match, let's see if we can find the best one.
    }
    int max = matches.stream().mapToInt(e -> e.parameters().size()).max().getAsInt();
    ImmutableList<Executable> maxMatches =
        matches.stream().filter(c -> c.parameters().size() == max).collect(toImmutableList());
    if (maxMatches.size() > 1) {
      throw errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderAmbiguous] Property names correspond to more than one %s:\n%s",
              description,
              executableListString(maxMatches));
    }
    return maxMatches.get(0);
  }

  private String executableListString(List<Executable> executables) {
    return executables.stream()
        .map(Object::toString)
        .collect(joining("\n  ", "  ", ""));
  }

  private boolean executableMatches(
      Executable executable, ImmutableSet<ExecutableElement> methodsInAutoBuilderType) {
    // Start with the complete set of parameter names and remove them one by one as we find
    // corresponding methods. We ignore case, under the assumption that it is unlikely that a case
    // difference is going to allow a candidate to match when another one is better.
    // A parameter named foo could be matched by methods like this:
    //    X foo(Y)
    //    X setFoo(Y)
    //    X fooBuilder()
    //    X fooBuilder(Y)
    // There are further constraints, including on the types X and Y, that will later be imposed by
    // BuilderMethodClassifier, but here we just require that there be at least one method with
    // one of these shapes for foo.
    NavigableSet<String> parameterNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    parameterNames.addAll(executable.parameterNames());
    for (ExecutableElement method : methodsInAutoBuilderType) {
      String name = method.getSimpleName().toString();
      if (name.endsWith("Builder")) {
        String property = name.substring(0, name.length() - "Builder".length());
        parameterNames.remove(property);
      }
      if (method.getParameters().size() == 1) {
        parameterNames.remove(name);
        if (name.startsWith("set")) {
          parameterNames.remove(name.substring(3));
        }
      }
      if (parameterNames.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean visibleFrom(Element element, PackageElement fromPackage) {
    Visibility visibility = Visibility.effectiveVisibilityOfElement(element);
    switch (visibility) {
      case PUBLIC:
        return true;
      case PROTECTED:
        // We care about whether the constructor is visible from the generated class. The generated
        // class is never going to be a subclass of the class containing the constructor, so
        // protected and default access are equivalent.
      case DEFAULT:
        return getPackage(element).equals(fromPackage);
      default:
        return false;
    }
  }

  private Optional<AnnotationMirror> kotlinMetadataAnnotation(Element element) {
    // It would be MUCH simpler if we could just use ofClass.getAnnotation(Metadata.class).
    // However that would be unsound. We want to shade the Kotlin runtime, including
    // kotlin.Metadata, so as not to interfere with other things on the annotation classpath that
    // might have a different version of the runtime. That means that if we referenced
    // kotlin.Metadata.class here we would actually be referencing
    // autovalue.shaded.kotlin.Metadata.class. Obviously the Kotlin class doesn't have that
    // annotation.
    return element.getAnnotationMirrors().stream()
        .filter(
            a ->
                asTypeElement(a.getAnnotationType())
                    .getQualifiedName()
                    .contentEquals(KOTLIN_METADATA_NAME))
        .<AnnotationMirror>map(a -> a) // get rid of that stupid wildcard
        .findFirst();
  }

  /**
   * Use Kotlin reflection to build {@link Executable} instances for the constructors in {@code
   * ofClass} that include information about which parameters have default values.
   */
  private ImmutableList<Executable> kotlinConstructorsIn(
      AnnotationMirror metadata, TypeElement ofClass) {
    ImmutableMap<String, AnnotationValue> annotationValues =
        AnnotationMirrors.getAnnotationValuesWithDefaults(metadata).entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey().getSimpleName().toString(), e -> e.getValue()));
    // We match the KmConstructor instances with the ExecutableElement instances based on the
    // parameter names. We could possibly just assume that the constructors are in the same order.
    Map<ImmutableSet<String>, ExecutableElement> map =
        constructorsIn(ofClass.getEnclosedElements()).stream()
            .collect(toMap(c -> parameterNames(c), c -> c, (a, b) -> a, LinkedHashMap::new));
    ImmutableMap<ImmutableSet<String>, ExecutableElement> paramNamesToConstructor =
        ImmutableMap.copyOf(map);
    KotlinClassHeader header =
        new KotlinClassHeader(
            (Integer) annotationValues.get("k").getValue(),
            intArrayValue(annotationValues.get("mv")),
            stringArrayValue(annotationValues.get("d1")),
            stringArrayValue(annotationValues.get("d2")),
            (String) annotationValues.get("xs").getValue(),
            (String) annotationValues.get("pn").getValue(),
            (Integer) annotationValues.get("xi").getValue());
    KotlinClassMetadata.Class classMetadata =
        (KotlinClassMetadata.Class) KotlinClassMetadata.read(header);
    KmClass kmClass = classMetadata.toKmClass();
    ImmutableList.Builder<Executable> kotlinConstructorsBuilder = ImmutableList.builder();
    for (KmConstructor constructor : kmClass.getConstructors()) {
      ImmutableSet.Builder<String> allBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<String> optionalBuilder = ImmutableSet.builder();
      for (KmValueParameter param : constructor.getValueParameters()) {
        String name = param.getName();
        allBuilder.add(name);
        if (Flag.ValueParameter.DECLARES_DEFAULT_VALUE.invoke(param.getFlags())) {
          optionalBuilder.add(name);
        }
      }
      ImmutableSet<String> optional = optionalBuilder.build();
      ImmutableSet<String> all = allBuilder.build();
      ExecutableElement javaConstructor = paramNamesToConstructor.get(all);
      if (javaConstructor != null) {
        kotlinConstructorsBuilder.add(Executable.of(javaConstructor, optional));
      }
    }
    return kotlinConstructorsBuilder.build();
  }

  private static int[] intArrayValue(AnnotationValue value) {
    @SuppressWarnings("unchecked")
    List<AnnotationValue> list = (List<AnnotationValue>) value.getValue();
    return list.stream().mapToInt(v -> (int) v.getValue()).toArray();
  }

  private static String[] stringArrayValue(AnnotationValue value) {
    @SuppressWarnings("unchecked")
    List<AnnotationValue> list = (List<AnnotationValue>) value.getValue();
    return list.stream().map(AnnotationValue::getValue).toArray(String[]::new);
  }

  private static ImmutableSet<String> parameterNames(ExecutableElement executableElement) {
    return executableElement.getParameters().stream()
        .map(v -> v.getSimpleName().toString())
        .collect(toImmutableSet());
  }

  private static final ElementKind ELEMENT_KIND_RECORD = elementKindRecord();

  private static ElementKind elementKindRecord() {
    try {
      Field record = ElementKind.class.getField("RECORD");
      return (ElementKind) record.get(null);
    } catch (ReflectiveOperationException e) {
      // OK: we must be on a JDK version that predates this.
      return null;
    }
  }

  private TypeElement getOfClass(
      TypeElement autoBuilderType, AnnotationMirror autoBuilderAnnotation) {
    TypeElement ofClassValue = findOfClassValue(autoBuilderAnnotation);
    boolean isDefault = typeUtils().isSameType(ofClassValue.asType(), javaLangVoid);
    if (!isDefault) {
      return ofClassValue;
    }
    Element enclosing = autoBuilderType.getEnclosingElement();
    ElementKind enclosingKind = enclosing.getKind();
    if (enclosing.getKind() != ElementKind.CLASS && enclosingKind != ELEMENT_KIND_RECORD) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderEnclosing] @AutoBuilder must specify ofClass=Something.class or it"
                  + " must be nested inside the class to be built; actually nested inside %s %s.",
              Ascii.toLowerCase(enclosingKind.name()),
              enclosing);
    }
    return MoreElements.asType(enclosing);
  }

  private TypeElement findOfClassValue(AnnotationMirror autoBuilderAnnotation) {
    AnnotationValue ofClassValue =
        AnnotationMirrors.getAnnotationValue(autoBuilderAnnotation, "ofClass");
    Object value = ofClassValue.getValue();
    if (value instanceof TypeMirror) {
      TypeMirror ofClassType = (TypeMirror) value;
      switch (ofClassType.getKind()) {
        case DECLARED:
          return MoreTypes.asTypeElement(ofClassType);
        case ERROR:
          throw new MissingTypeException(MoreTypes.asError(ofClassType));
        default:
          break;
      }
    }
    throw new MissingTypeException(null);
  }

  private String findCallMethodValue(AnnotationMirror autoBuilderAnnotation) {
    AnnotationValue callMethodValue =
        AnnotationMirrors.getAnnotationValue(autoBuilderAnnotation, "callMethod");
    return AnnotationValues.getString(callMethodValue);
  }

  @Override
  Optional<String> nullableAnnotationForMethod(ExecutableElement propertyMethod) {
    // TODO(b/183005059): implement
    return Optional.empty();
  }

  private void buildAnnotation(
      TypeElement autoBuilderType, TypeElement annotationType, String callMethod) {
    if (!callMethod.isEmpty()) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderAnnotationMethod] @AutoBuilder for an annotation must have an empty"
                  + " callMethod, not \"%s\"",
              callMethod);
    }
    String autoAnnotationClassName =
        generatedClassName(autoBuilderType, AUTO_ANNOTATION_CLASS_PREFIX);
    TypeElement autoAnnotationClass = elementUtils().getTypeElement(autoAnnotationClassName);
    if (autoAnnotationClass != null) {
      processType(autoBuilderType, autoAnnotationClass, "newAnnotation");
      return;
    }
    AutoBuilderAnnotationTemplateVars vars = new AutoBuilderAnnotationTemplateVars();
    vars.autoBuilderType = TypeEncoder.encode(autoBuilderType.asType());
    vars.props = annotationBuilderPropertySet(annotationType);
    vars.pkg = TypeSimplifier.packageNameOf(autoBuilderType);
    vars.generated =
        generatedAnnotation(elementUtils(), processingEnv.getSourceVersion())
            .map(annotation -> TypeEncoder.encode(annotation.asType()))
            .orElse("");
    vars.className = TypeSimplifier.simpleNameOf(autoAnnotationClassName);
    vars.annotationType = TypeEncoder.encode(annotationType.asType());
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, /* baseType= */ javaLangVoid);
    text = Reformatter.fixup(text);
    writeSourceFile(autoAnnotationClassName, text, autoBuilderType);
    addDeferredType(autoBuilderType, autoAnnotationClassName);
  }

  private ImmutableSet<Property> annotationBuilderPropertySet(TypeElement annotationType) {
    // Annotation methods can't have their own annotations so there's nowhere for us to discover
    // a user @Nullable. We can only use our default @Nullable type annotation.
    Nullables nullables = Nullables.fromMethods(processingEnv, ImmutableList.of());
    // Translate the annotation elements into fake Property instances. We're really only interested
    // in the name and type, so we can use them to declare a parameter of the generated
    // @AutoAnnotation method. We'll generate a parameter for every element, even elements that
    // don't have setters in the builder. The generated builder implementation will pass the default
    // value from the annotation to those parameters.
    return methodsIn(annotationType.getEnclosedElements()).stream()
        .filter(m -> m.getParameters().isEmpty() && !m.getModifiers().contains(Modifier.STATIC))
        .map(method -> annotationBuilderProperty(method, nullables))
        .collect(toImmutableSet());
  }

  private static Property annotationBuilderProperty(
      ExecutableElement annotationMethod,
      Nullables nullables) {
    String name = annotationMethod.getSimpleName().toString();
    TypeMirror type = annotationMethod.getReturnType();
    return new Property(
        name,
        name,
        TypeEncoder.encode(type),
        type,
        /* nullableAnnotation= */ Optional.empty(),
        nullables,
        /* getter= */ "",
        /* maybeBuilderInitializer= */ Optional.empty(),
        /* hasDefault= */ false);
  }
}
