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
    Optional<BuilderMethodClassifier<VariableElement>> maybeClassifier =
        BuilderMethodClassifierForAutoBuilder.classify(
            methods,
            errorReporter(),
            processingEnv,
            executable,
            builtType,
            autoBuilderType,
            propertyInitializers.keySet());
    if (!maybeClassifier.isPresent() || errorReporter().errorCount() > 0) {
      // We've already output one or more error messages.
      return;
    }
    BuilderMethodClassifier<VariableElement> classifier = maybeClassifier.get();
    Map<String, String> propertyToGetterName =
        Maps.transformValues(classifier.builderGetters(), PropertyGetter::getName);
    AutoBuilderTemplateVars vars = new AutoBuilderTemplateVars();
    vars.props = propertySet(executable, propertyToGetterName, propertyInitializers);
    builder.defineVars(vars, classifier);
    vars.identifiers = !processingEnv.getOptions().containsKey(OMIT_IDENTIFIERS_OPTION);
    String generatedClassName = generatedClassName(autoBuilderType, "AutoBuilder_");
    vars.builderName = TypeSimplifier.simpleNameOf(generatedClassName);
    vars.builtType = TypeEncoder.encode(builtType);
    vars.build = executable.invoke();
    vars.toBuilderConstructor = false;
    vars.toBuilderMethods = ImmutableList.of();
    defineSharedVarsForType(autoBuilderType, ImmutableSet.of(), vars);
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, autoBuilderType.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(generatedClassName, text, autoBuilderType);
  }

  private ImmutableSet<Property> propertySet(
      Executable executable,
      Map<String, String> propertyToGetterName,
      ImmutableMap<String, String> builderInitializers) {
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
    List<? extends Element> elements = ofClass.getEnclosedElements();
    Stream<ExecutableElement> relevantExecutables =
        callMethod.isEmpty()
            ? constructorsIn(elements).stream()
            : methodsIn(elements).stream()
                .filter(m -> m.getSimpleName().contentEquals(callMethod))
                .filter(m -> m.getModifiers().contains(Modifier.STATIC));
    return relevantExecutables
        .filter(c -> visibleFrom(c, getPackage(autoBuilderType)))
        .map(Executable::new)
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
    addDeferredType(autoBuilderType);
  }

  private ImmutableSet<Property> annotationBuilderPropertySet(TypeElement annotationType) {
    // Translate the annotation elements into fake Property instances. We're really only interested
    // in the name and type, so we can use them to declare a parameter of the generated
    // @AutoAnnotation method. We'll generate a parameter for every element, even elements that
    // don't have setters in the builder. The generated builder implementation will pass the default
    // value from the annotation to those parameters.
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
