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
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.value.processor.AutoValueProcessor.OMIT_IDENTIFIERS_OPTION;
import static com.google.auto.value.processor.ClassNames.AUTO_ANNOTATION_NAME;
import static com.google.auto.value.processor.ClassNames.AUTO_BUILDER_NAME;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.AnnotationValues;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.service.AutoService;
import com.google.auto.value.processor.BuilderSpec.PropertyGetter;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.base.Ascii;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
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
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * Javac annotation processor (compiler plugin) for builders; user code never references this class.
 *
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(AUTO_BUILDER_NAME)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
public class AutoBuilderProcessor extends AutoValueishProcessor {
  private static final String ALLOW_OPTION = "com.google.auto.value.AutoBuilderIsUnstable";
  private static final String GENERATED_CLASS_PREFIX = "AutoBuilderAnnotation_";

  public AutoBuilderProcessor() {
    super(AUTO_BUILDER_NAME, /* appliesToInterfaces= */ true);
  }

  @Override
  public Set<String> getSupportedOptions() {
    return ImmutableSet.of(OMIT_IDENTIFIERS_OPTION, ALLOW_OPTION);
  }

  private TypeMirror javaLangAnnotationAnnotation;
  private TypeMirror javaLangVoid;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    javaLangAnnotationAnnotation =
        elementUtils().getTypeElement("java.lang.annotation.Annotation").asType();
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
  //   @AutoBuilder(callMethod = "newAnnotation", ofClass = Annotations.MyAnnotBuilder.class)
  //   class AutoBuilderAnnotation_Annotations_MyAnnotBuilder {
  //     @AutoAnnotation
  //     static MyAnnot newAnnotation(...) {
  //       return new AutoBuilderAnnotation_Annotations_MyAnnotBuilder_newAnnotation(...);
  //     }
  //   }
  //
  // Generating that code will cause both AutoAnnotation and AutoBuilder to be triggered in the next
  // annotation processing round. The AutoAnnotation handling is regular but the AutoBuilder
  // handling detects the AutoBuilderAnnotation_ name and realizes that it needs to generate
  // AutoBuilder_Annotations_MyAnnotBuilder. We also abuse ofClass to tell the second AutoBuilder
  // run what the original @AutoBuilder interface was.
  //
  // Without this dodge, either we would have to synthesize an ExecutableElement for a
  // not-yet-generated @AutoAnnotation method, or we would have to complicate the AutoBuilder logic
  // so it can get the names and types of the properties from either an ExecutableElement (as at
  // present) or a TypeElement (when the type is an annotation).

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
    if (isAnnotation(ofClass)) {
      generateAutoAnnotationClass(autoBuilderType, ofClass, callMethod);
      return;
    }
    if (autoBuilderType.getSimpleName().toString().startsWith(GENERATED_CLASS_PREFIX)) {
      // This is the second round of annotation implementation, as described above.
      // The actual builder type we're implementing is Annotations.MyAnnotBuilder, with its
      // @AutoBuilder(ofClass = MyAnnot.class) annotation. So we're going to want to set
      // autoBuilderType to that. But the method the builder will call is newAnnotation (which we
      // already have in callMethod) and the class that's in is
      // AutoBuilderAnnotation_Annotations_MyAnnotBuilder. That class is also annotated with
      // @AutoBuilder, and it's how we got here, so autoBuilderType is currently set to that. So in
      // fact we need to exchange autoBuilderType and ofClass.
      TypeElement t = ofClass;
      ofClass = autoBuilderType;
      autoBuilderType = t;
    }
    ImmutableSet<ExecutableElement> methods =
        abstractMethodsIn(
            getLocalAndInheritedMethods(autoBuilderType, typeUtils(), elementUtils()));
    ExecutableElement executable = findExecutable(ofClass, callMethod, autoBuilderType, methods);
    BuilderSpec builderSpec = new BuilderSpec(ofClass, processingEnv, errorReporter());
    BuilderSpec.Builder builder = builderSpec.new Builder(autoBuilderType);
    TypeMirror builtType = builtType(executable);
    Optional<BuilderMethodClassifier<VariableElement>> maybeClassifier =
        BuilderMethodClassifierForAutoBuilder.classify(
            methods, errorReporter(), processingEnv, executable, builtType, autoBuilderType);
    if (!maybeClassifier.isPresent() || errorReporter().errorCount() > 0) {
      // We've already output one or more error messages.
      return;
    }
    BuilderMethodClassifier<VariableElement> classifier = maybeClassifier.get();
    Map<String, String> propertyToGetterName =
        Maps.transformValues(classifier.builderGetters(), PropertyGetter::getName);
    AutoBuilderTemplateVars vars = new AutoBuilderTemplateVars();
    vars.props = propertySet(autoBuilderType, executable, propertyToGetterName);
    builder.defineVars(vars, classifier);
    vars.identifiers = !processingEnv.getOptions().containsKey(OMIT_IDENTIFIERS_OPTION);
    String generatedClassName = generatedClassName(autoBuilderType, "AutoBuilder_");
    vars.builderName = TypeSimplifier.simpleNameOf(generatedClassName);
    vars.builtType = TypeEncoder.encode(builtType);
    vars.build = build(executable);
    vars.toBuilderConstructor = false;
    vars.toBuilderMethods = ImmutableList.of();
    defineSharedVarsForType(autoBuilderType, ImmutableSet.of(), vars);
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, autoBuilderType.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(generatedClassName, text, autoBuilderType);
  }

  private ImmutableSet<Property> propertySet(
      TypeElement autoBuilderType,
      ExecutableElement executable,
      Map<String, String> propertyToGetterName) {
    boolean autoAnnotation =
        MoreElements.getAnnotationMirror(executable, AUTO_ANNOTATION_NAME).isPresent();
    ImmutableMap<String, String> builderInitializers =
        autoAnnotation
            ? autoAnnotationInitializers(autoBuilderType, executable)
            : ImmutableMap.of();
    // Fix any parameter names that are reserved words in Java. Java source code can't have
    // such parameter names, but Kotlin code might, for example.
    Map<VariableElement, String> identifiers =
        executable.getParameters().stream()
            .collect(toMap(v -> v, v -> v.getSimpleName().toString()));
    fixReservedIdentifiers(identifiers);
    return executable.getParameters().stream()
        .map(
            v -> {
              String name = v.getSimpleName().toString();
              return newProperty(
                  v,
                  identifiers.get(v),
                  propertyToGetterName.get(name),
                  Optional.ofNullable(builderInitializers.get(name)));
            })
        .collect(toImmutableSet());
  }

  private Property newProperty(
      VariableElement var,
      String identifier,
      String getterName,
      Optional<String> builderInitializer) {
    String name = var.getSimpleName().toString();
    TypeMirror type = var.asType();
    Optional<String> nullableAnnotation = nullableAnnotationFor(var, var.asType());
    return new Property(
        name,
        identifier,
        TypeEncoder.encode(type),
        type,
        nullableAnnotation,
        getterName,
        builderInitializer);
  }

  private ImmutableMap<String, String> autoAnnotationInitializers(
      TypeElement autoBuilderType, ExecutableElement autoAnnotationMethod) {
    // We expect the return type of an @AutoAnnotation method to be an annotation type. If it isn't,
    // AutoAnnotation will presumably complain, so we don't need to complain further.
    TypeMirror returnType = autoAnnotationMethod.getReturnType();
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

  private ExecutableElement findExecutable(
      TypeElement ofClass,
      String callMethod,
      TypeElement autoBuilderType,
      ImmutableSet<ExecutableElement> methods) {
    List<ExecutableElement> executables =
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
        return matchingExecutable(autoBuilderType, executables, methods, description);
    }
  }

  private ImmutableList<ExecutableElement> findRelevantExecutables(
      TypeElement ofClass, String callMethod, TypeElement autoBuilderType) {
    List<? extends Element> elements = ofClass.getEnclosedElements();
    Stream<ExecutableElement> relevantExecutables =
        callMethod.isEmpty()
            ? constructorsIn(elements).stream()
            : methodsIn(elements).stream()
                .filter(m -> m.getSimpleName().contentEquals(callMethod))
                .filter(m -> m.getModifiers().contains(Modifier.STATIC));
    return relevantExecutables
        .filter(c -> visibleFrom(c, getPackage(autoBuilderType)))
        .collect(toImmutableList());
  }

  private ExecutableElement matchingExecutable(
      TypeElement autoBuilderType,
      List<ExecutableElement> executables,
      ImmutableSet<ExecutableElement> methods,
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
    ImmutableList<ExecutableElement> matches =
        executables.stream().filter(x -> executableMatches(x, methods)).collect(toImmutableList());
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
    int max = matches.stream().mapToInt(c -> c.getParameters().size()).max().getAsInt();
    ImmutableList<ExecutableElement> maxMatches =
        matches.stream().filter(c -> c.getParameters().size() == max).collect(toImmutableList());
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

  private String executableListString(List<ExecutableElement> executables) {
    return executables.stream()
        .map(AutoBuilderProcessor::executableString)
        .collect(joining("\n  ", "  ", ""));
  }

  static String executableString(ExecutableElement executable) {
    Element nameSource =
        executable.getKind() == ElementKind.CONSTRUCTOR
            ? executable.getEnclosingElement()
            : executable;
    return nameSource.getSimpleName()
        + executable.getParameters().stream()
            .map(v -> v.asType() + " " + v.getSimpleName())
            .collect(joining(", ", "(", ")"));
  }

  private boolean executableMatches(
      ExecutableElement executable, ImmutableSet<ExecutableElement> methods) {
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
    NavigableSet<String> parameterNames =
        executable.getParameters().stream()
            .map(v -> v.getSimpleName().toString())
            .collect(toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    for (ExecutableElement method : methods) {
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

  private TypeMirror builtType(ExecutableElement executable) {
    switch (executable.getKind()) {
      case CONSTRUCTOR:
        return executable.getEnclosingElement().asType();
      case METHOD:
        return executable.getReturnType();
      default:
        throw new VerifyException("Unexpected executable kind " + executable.getKind());
    }
  }

  private String build(ExecutableElement executable) {
    TypeElement enclosing = MoreElements.asType(executable.getEnclosingElement());
    String type = TypeEncoder.encodeRaw(enclosing.asType());
    switch (executable.getKind()) {
      case CONSTRUCTOR:
        boolean generic = !enclosing.getTypeParameters().isEmpty();
        String typeParams = generic ? "<>" : "";
        return "new " + type + typeParams;
      case METHOD:
        return type + "." + executable.getSimpleName();
      default:
        throw new VerifyException("Unexpected executable kind " + executable.getKind());
    }
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

  private boolean isAnnotation(TypeElement typeElement) {
    TypeMirror typeMirror = typeElement.asType();
    return processingEnv.getTypeUtils().isAssignable(typeMirror, javaLangAnnotationAnnotation);
  }

  private void generateAutoAnnotationClass(
      TypeElement autoBuilderType, TypeElement annotationType, String callMethod) {
    if (!callMethod.isEmpty()) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderAnnotationMethod] @AutoBuilder for an annotation must have an empty"
                  + " callMethod, not \"%s\"",
              callMethod);
    }
    AutoBuilderAnnotationTemplateVars vars = new AutoBuilderAnnotationTemplateVars();
    vars.autoBuilderType = TypeEncoder.encode(autoBuilderType.asType());
    vars.props = annotationBuilderPropertySet(annotationType);
    vars.pkg = TypeSimplifier.packageNameOf(autoBuilderType);
    vars.generated =
        generatedAnnotation(elementUtils(), processingEnv.getSourceVersion())
            .map(annotation -> TypeEncoder.encode(annotation.asType()))
            .orElse("");
    String generatedClassName = generatedClassName(autoBuilderType, GENERATED_CLASS_PREFIX);
    vars.className = TypeSimplifier.simpleNameOf(generatedClassName);
    vars.annotationType = TypeEncoder.encode(annotationType.asType());
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, /* baseType= */ javaLangVoid);
    text = Reformatter.fixup(text);
    writeSourceFile(generatedClassName, text, autoBuilderType);
  }

  private ImmutableSet<Property> annotationBuilderPropertySet(TypeElement annotationType) {
    return methodsIn(annotationType.getEnclosedElements()).stream()
        .filter(m -> m.getParameters().isEmpty() && !m.getModifiers().contains(Modifier.STATIC))
        .map(AutoBuilderProcessor::annotationBuilderProperty)
        .collect(toImmutableSet());
  }

  private static Property annotationBuilderProperty(ExecutableElement annotationMethod) {
    String name = annotationMethod.getSimpleName().toString();
    TypeMirror type = annotationMethod.getReturnType();
    return new Property(
        name,
        name,
        TypeEncoder.encode(type),
        type,
        /* nullableAnnotation= */ Optional.empty(),
        /* getter= */ "",
        /* maybeBuilderInitializer= */ Optional.empty());
  }
}
