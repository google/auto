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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.value.processor.AutoValueProcessor.OMIT_IDENTIFIERS_OPTION;
import static com.google.auto.value.processor.ClassNames.AUTO_BUILDER_NAME;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.service.AutoService;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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

  public AutoBuilderProcessor() {
    super(AUTO_BUILDER_NAME);
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

  @Override
  void processType(TypeElement autoBuilderType) {
    if (!processingEnv.getOptions().containsKey(ALLOW_OPTION)) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "Compile with -A%s to enable this UNSUPPORTED AND UNSTABLE prototype",
              ALLOW_OPTION);
    }
    if (autoBuilderType.getKind() != ElementKind.CLASS
        && autoBuilderType.getKind() != ElementKind.INTERFACE) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderWrongType] @AutoBuilder only applies to classes and interfaces");
    }
    checkModifiersIfNested(autoBuilderType);
    TypeElement constructedType = findConstructedType(autoBuilderType);
    checkModifiersIfNested(constructedType); // TODO: error message is wrong
    ExecutableElement constructor = findConstructor(constructedType, autoBuilderType);
    BuilderSpec builderSpec = new BuilderSpec(constructedType, processingEnv, errorReporter());
    BuilderSpec.Builder builder = builderSpec.new Builder(autoBuilderType);
    ImmutableSet<ExecutableElement> methods =
        abstractMethodsIn(
            getLocalAndInheritedMethods(autoBuilderType, typeUtils(), elementUtils()));
    Optional<BuilderMethodClassifier<VariableElement>> classifier =
        BuilderMethodClassifierForAutoBuilder.classify(
            methods, errorReporter(), processingEnv, constructor, constructedType, autoBuilderType);
    if (!classifier.isPresent()) {
      // We've already output one or more error messages.
      return;
    }
    AutoBuilderTemplateVars vars = new AutoBuilderTemplateVars();
    vars.props = propertySet(constructor);
    builder.defineVars(vars, classifier.get());
    vars.identifiers = !processingEnv.getOptions().containsKey(OMIT_IDENTIFIERS_OPTION);
    String generatedClassName = generatedClassName(autoBuilderType, "AutoBuilder_");
    vars.builderName = TypeSimplifier.simpleNameOf(generatedClassName);
    vars.builtClass = TypeEncoder.encodeRaw(constructedType.asType());
    vars.types = typeUtils();
    vars.toBuilderConstructor = false;
    defineSharedVarsForType(constructedType, ImmutableSet.of(), vars);
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, autoBuilderType.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(generatedClassName, text, autoBuilderType);
  }

  private ImmutableSet<Property> propertySet(ExecutableElement constructor) {
    return constructor.getParameters().stream().map(this::newProperty).collect(toImmutableSet());
  }

  private Property newProperty(VariableElement var) {
    String name = var.getSimpleName().toString();
    TypeMirror type = var.asType();
    return new Property(
        name,
        name,
        TypeEncoder.encode(type),
        type,
        Optional.empty());
  }

  private ExecutableElement findConstructor(
      TypeElement constructedType, TypeElement autoBuilderType) {
    // TODO(b/183005059): choose a constructor based on parameter names and types.
    // Currently we always choose the constructor with the most parameters, and there must be
    // exactly one of those.
    List<ExecutableElement> constructors = visibleConstructors(constructedType, autoBuilderType);
    List<ExecutableElement> maxConstructors = maxConstructors(constructors);
    if (maxConstructors.size() > 1) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderNoMaxConstructor] @AutoBuilder constructed type %s must have one"
                  + " visible constructor with more parameters than any other, but there are %d"
                  + " constructors with %d parameters",
              constructedType,
              maxConstructors.size(),
              maxConstructors.get(0).getParameters().size());
    }
    return maxConstructors.get(0);
  }

  private ImmutableList<ExecutableElement> visibleConstructors(
      TypeElement constructedType, TypeElement autoBuilderType) {
    ImmutableList<ExecutableElement> constructors =
        constructorsIn(constructedType.getEnclosedElements()).stream()
            .filter(c -> visibleFrom(c, getPackage(autoBuilderType)))
            .collect(toImmutableList());
    if (constructors.isEmpty()) {
      errorReporter()
          .abortWithError(
              autoBuilderType,
              "[AutoBuilderNoConstructor] No visible constructors for %s",
              constructedType);
    }
    return constructors;
  }

  private ImmutableList<ExecutableElement> maxConstructors(List<ExecutableElement> constructors) {
    int maxParams = constructors.stream().mapToInt(c -> c.getParameters().size()).max().getAsInt();
    return constructors.stream()
        .filter(c -> c.getParameters().size() == maxParams)
        .collect(toImmutableList());
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

  private TypeElement findConstructedType(TypeElement autoBuilderType) {
    TypeElement ofClassValue = findOfClassValue(autoBuilderType);
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

  private TypeElement findOfClassValue(TypeElement autoBuilderType) {
    // The annotation is guaranteed to be present by the contract of Processor#process
    AnnotationMirror autoBuilderAnnotation =
        getAnnotationMirror(autoBuilderType, AUTO_BUILDER_NAME).get();
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

  @Override
  Optional<String> nullableAnnotationForMethod(ExecutableElement propertyMethod) {
    // TODO(b/183005059): implement
    return Optional.empty();
  }
}
