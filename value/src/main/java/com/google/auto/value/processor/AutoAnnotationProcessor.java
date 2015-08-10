/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Javac annotation processor (compiler plugin) to generate annotation implementations. User code
 * never references this class.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoService(Processor.class)
public class AutoAnnotationProcessor extends AbstractProcessor {
  public AutoAnnotationProcessor() {}

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoAnnotation.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Issue a compilation error. This method does not throw an exception, since we want to
   * continue processing and perhaps report other errors.
   */
  private void reportError(Element e, String msg, Object... msgParams) {
    String formattedMessage = String.format(msg, msgParams);
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, formattedMessage, e);
  }

  /**
   * Issue a compilation error and return an exception that, when thrown, will cause the processing
   * of this class to be abandoned. This does not prevent the processing of other classes.
   */
  private AbortProcessingException abortWithError(String msg, Element e) {
    reportError(e, msg);
    return new AbortProcessingException();
  }

  private Types typeUtils;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    typeUtils = processingEnv.getTypeUtils();
    boolean claimed = (annotations.size() == 1
        && annotations.iterator().next().getQualifiedName().toString().equals(
            AutoAnnotation.class.getName()));
    if (claimed) {
      process(roundEnv);
      return true;
    } else {
      return false;
    }
  }

  private void process(RoundEnvironment roundEnv) {
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(AutoAnnotation.class);
    List<ExecutableElement> methods = ElementFilter.methodsIn(annotatedElements);
    if (!SuperficialValidation.validateElements(methods) || methodsAreOverloaded(methods)) {
      return;
    }
    for (ExecutableElement method : methods) {
      try {
        processMethod(method);
      } catch (AbortProcessingException e) {
        // We abandoned this type, but continue with the next.
      } catch (RuntimeException e) {
        // Don't propagate this exception, which will confusingly crash the compiler.
        // Instead, report a compiler error with the stack trace.
        String trace = Throwables.getStackTraceAsString(e);
        reportError(method, "@AutoAnnotation processor threw an exception: %s", trace);
      }
    }
  }

  private void processMethod(ExecutableElement method) {
    if (!method.getModifiers().contains(Modifier.STATIC)) {
      throw abortWithError("@AutoAnnotation method must be static", method);
    }

    TypeElement annotationElement = getAnnotationReturnType(method);
    TypeMirror annotationTypeMirror = annotationElement.asType();

    Set<Class<?>> wrapperTypesUsedInCollections = wrapperTypesUsedInCollections(method);

    ImmutableMap<String, ExecutableElement> memberMethods = getMemberMethods(annotationElement);
    Set<TypeMirror> memberTypes = getMemberTypes(memberMethods.values());
    Set<TypeMirror> referencedTypes = getReferencedTypes(
        annotationTypeMirror, method, memberTypes, wrapperTypesUsedInCollections);
    TypeElement methodClass = (TypeElement) method.getEnclosingElement();
    String pkg = TypeSimplifier.packageNameOf(methodClass);
    TypeSimplifier typeSimplifier = new TypeSimplifier(
        typeUtils, pkg, referencedTypes, annotationTypeMirror);

    AnnotationOutput annotationOutput = new AnnotationOutput(typeSimplifier);
    ImmutableMap<String, AnnotationValue> defaultValues = getDefaultValues(annotationElement);
    ImmutableMap<String, Member> members =
        getMembers(method, memberMethods, typeSimplifier, annotationOutput);
    ImmutableMap<String, Parameter> parameters =
        getParameters(annotationElement, method, members, typeSimplifier);
    validateParameters(annotationElement, method, members, parameters, defaultValues);

    String generatedClassName = generatedClassName(method);

    AutoAnnotationTemplateVars vars = new AutoAnnotationTemplateVars();
    vars.annotationFullName = annotationElement.toString();
    vars.annotationName = typeSimplifier.simplify(annotationElement.asType());
    vars.className = generatedClassName;
    vars.imports = typeSimplifier.typesToImport();
    vars.generated = typeSimplifier.simplify(getTypeMirror(Generated.class));
    vars.arrays = typeSimplifier.simplify(getTypeMirror(Arrays.class));
    vars.members = members;
    vars.params = parameters;
    vars.pkg = pkg;
    vars.wrapperTypesUsedInCollections = wrapperTypesUsedInCollections;
    vars.gwtCompatible = isGwtCompatible(annotationElement);
    String text = vars.toText();
    text = Reformatter.fixup(text);
    writeSourceFile(pkg + "." + generatedClassName, text, methodClass);
  }

  private boolean methodsAreOverloaded(List<ExecutableElement> methods) {
    boolean overloaded = false;
    Set<String> classNames = new HashSet<String>();
    for (ExecutableElement method : methods) {
      if (!classNames.add(generatedClassName(method))) {
        overloaded = true;
        reportError(method, "@AutoAnnotation methods cannot be overloaded");
      }
    }
    return overloaded;
  }

  private String generatedClassName(ExecutableElement method) {
    TypeElement type = (TypeElement) method.getEnclosingElement();
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = (TypeElement) type.getEnclosingElement();
      name = type.getSimpleName() + "_" + name;
    }
    return "AutoAnnotation_" + name + "_" + method.getSimpleName();
  }

  private TypeElement getAnnotationReturnType(ExecutableElement method) {
    TypeMirror returnTypeMirror = method.getReturnType();
    if (returnTypeMirror.getKind() == TypeKind.DECLARED) {
      Element returnTypeElement = typeUtils.asElement(method.getReturnType());
      if (returnTypeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
        return (TypeElement) returnTypeElement;
      }
    }
    throw abortWithError("Return type of @AutoAnnotation method must be an annotation type, not "
        + returnTypeMirror, method);
  }

  private ImmutableMap<String, ExecutableElement> getMemberMethods(TypeElement annotationElement) {
    ImmutableMap.Builder<String, ExecutableElement> members = ImmutableMap.builder();
    for (ExecutableElement member :
        ElementFilter.methodsIn(annotationElement.getEnclosedElements())) {
      String name = member.getSimpleName().toString();
      members.put(name, member);
    }
    return members.build();
  }

  private ImmutableMap<String, Member> getMembers(
      Element context,
      ImmutableMap<String, ExecutableElement> memberMethods,
      TypeSimplifier typeSimplifier,
      AnnotationOutput annotationOutput) {
    ImmutableMap.Builder<String, Member> members = ImmutableMap.builder();
    for (Map.Entry<String, ExecutableElement> entry : memberMethods.entrySet()) {
      ExecutableElement memberMethod = entry.getValue();
      String name = memberMethod.getSimpleName().toString();
      members.put(
          name,
          new Member(processingEnv, context, memberMethod, typeSimplifier, annotationOutput));
    }
    return members.build();
  }

  private ImmutableMap<String, AnnotationValue> getDefaultValues(TypeElement annotationElement) {
    ImmutableMap.Builder<String, AnnotationValue> defaultValues = ImmutableMap.builder();
    for (ExecutableElement member :
        ElementFilter.methodsIn(annotationElement.getEnclosedElements())) {
      String name = member.getSimpleName().toString();
      AnnotationValue defaultValue = member.getDefaultValue();
      if (defaultValue != null) {
        defaultValues.put(name, defaultValue);
      }
    }
    return defaultValues.build();
  }

  private Set<TypeMirror> getMemberTypes(Collection<ExecutableElement> memberMethods) {
    Set<TypeMirror> types = new TypeMirrorSet();
    for (ExecutableElement memberMethod : memberMethods) {
      types.add(memberMethod.getReturnType());
    }
    return types;
  }

  private ImmutableMap<String, Parameter> getParameters(
      TypeElement annotationElement,
      ExecutableElement method,
      Map<String, Member> members,
      TypeSimplifier typeSimplifier) {
    ImmutableMap.Builder<String, Parameter> parameters = ImmutableMap.builder();
    boolean error = false;
    for (VariableElement parameter : method.getParameters()) {
      String name = parameter.getSimpleName().toString();
      Member member = members.get(name);
      if (member == null) {
        reportError(parameter,
            "@AutoAnnotation method parameter '%s' must have the same name as a member of %s",
            name, annotationElement);
        error = true;
      } else {
        TypeMirror parameterType = parameter.asType();
        TypeMirror memberType = member.getTypeMirror();
        if (compatibleTypes(parameterType, memberType)) {
          parameters.put(name, new Parameter(parameterType, typeSimplifier));
        } else {
          reportError(parameter,
              "@AutoAnnotation method parameter '%s' has type %s but %s.%s has type %s",
              name, parameterType, annotationElement, name, memberType);
          error = true;
        }
      }
    }
    if (error) {
      throw new AbortProcessingException();
    }
    return parameters.build();
  }

  private void validateParameters(
      TypeElement annotationElement,
      ExecutableElement method,
      ImmutableMap<String, Member> members,
      ImmutableMap<String, Parameter> parameters,
      ImmutableMap<String, AnnotationValue> defaultValues) {
    boolean error = false;
    for (String memberName : members.keySet()) {
      if (!parameters.containsKey(memberName) && !defaultValues.containsKey(memberName)) {
        reportError(method,
            "@AutoAnnotation method needs a parameter with name '%s' and type %s"
                + " corresponding to %s.%s, which has no default value",
            memberName, members.get(memberName).getType(), annotationElement, memberName);
        error = true;
      }
    }
    if (error) {
      throw new AbortProcessingException();
    }
  }

  /**
   * Returns true if {@code parameterType} can be used to provide the value of an annotation member
   * of type {@code memberType}. They must either be the same type, or the member type must be an
   * array and the parameter type must be a collection of a compatible type.
   */
  private boolean compatibleTypes(TypeMirror parameterType, TypeMirror memberType) {
    if (typeUtils.isAssignable(parameterType, memberType)) {
      // parameterType assignable to memberType, which in the restricted world of annotations
      // means they are the same type, or maybe memberType is an annotation type and parameterType
      // is a subtype of that annotation interface (why would you do that?).
      return true;
    }
    // They're not the same, but we could still consider them compatible if for example
    // parameterType is List<Integer> and memberType is int[]. We accept any type that is assignable
    // to Collection<Integer> (in this example).
    if (memberType.getKind() != TypeKind.ARRAY) {
      return false;
    }
    TypeMirror arrayElementType = ((ArrayType) memberType).getComponentType();
    TypeMirror wrappedArrayElementType = arrayElementType.getKind().isPrimitive()
        ? typeUtils.boxedClass((PrimitiveType) arrayElementType).asType()
        : arrayElementType;
    TypeElement javaUtilCollection =
        processingEnv.getElementUtils().getTypeElement(Collection.class.getCanonicalName());
    DeclaredType collectionOfElement =
        typeUtils.getDeclaredType(javaUtilCollection, wrappedArrayElementType);
    return typeUtils.isAssignable(parameterType, collectionOfElement);
  }

  /**
   * Returns the wrapper types ({@code Integer.class} etc) that are used in collection parameters
   * like {@code List<Integer>}. This is needed because we will emit a helper method for each such
   * type, for example to convert {@code Collection<Integer>} into {@code int[]}.
   */
  private Set<Class<?>> wrapperTypesUsedInCollections(ExecutableElement method) {
    TypeElement javaUtilCollection =
        processingEnv.getElementUtils().getTypeElement(Collection.class.getName());
    ImmutableSet.Builder<Class<?>> usedInCollections = ImmutableSet.builder();
    for (Class<?> wrapper : Primitives.allWrapperTypes()) {
      DeclaredType collectionOfWrapper =
          typeUtils.getDeclaredType(javaUtilCollection, getTypeMirror(wrapper));
      for (VariableElement parameter : method.getParameters()) {
        if (typeUtils.isAssignable(parameter.asType(), collectionOfWrapper)) {
          usedInCollections.add(wrapper);
          break;
        }
      }
    }
    return usedInCollections.build();
  }

  private Set<TypeMirror> getReferencedTypes(
      TypeMirror annotation,
      ExecutableElement method,
      Set<TypeMirror> memberTypes,
      Set<Class<?>> wrapperTypesUsedInCollections) {
    Set<TypeMirror> types = new TypeMirrorSet();
    types.add(annotation);
    types.add(getTypeMirror(Generated.class));
    for (VariableElement parameter : method.getParameters()) {
      // Method parameter types are usually the same as annotation member types, but in the case of
      // List<Integer> for int[] we are referencing List.
      types.add(parameter.asType());
    }
    types.addAll(memberTypes);
    if (containsArrayType(types)) {
      // If there are array properties then we will be referencing java.util.Arrays.
      types.add(getTypeMirror(Arrays.class));
    }
    if (!wrapperTypesUsedInCollections.isEmpty()) {
      // If there is at least one parameter whose type is a collection of a primitive wrapper type
      // (for example List<Integer>) then we will be referencing java util.Collection.
      types.add(getTypeMirror(Collection.class));
    }
    return types;
  }

  private TypeMirror getTypeMirror(Class<?> c) {
    return processingEnv.getElementUtils().getTypeElement(c.getName()).asType();
  }

  private static boolean containsArrayType(Set<TypeMirror> types) {
    for (TypeMirror type : types) {
      if (type.getKind() == TypeKind.ARRAY) {
        return true;
      }
    }
    return false;
  }

  private static boolean isGwtCompatible(TypeElement annotationElement) {
    for (AnnotationMirror annotationMirror : annotationElement.getAnnotationMirrors()) {
      String name = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
      if (name.equals("GwtCompatible")) {
        return true;
      }
    }
    return false;
  }

  private void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      Writer writer = sourceFile.openWriter();
      try {
        writer.write(text);
      } finally {
        writer.close();
      }
    } catch (IOException e) {
      // This should really be an error, but we make it a warning in the hope of resisting Eclipse
      // bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599. If that bug manifests, we may get
      // invoked more than once for the same file, so ignoring the ability to overwrite it is the
      // right thing to do. If we are unable to write for some other reason, we should get a compile
      // error later because user code will have a reference to the code we were supposed to
      // generate (new AutoValue_Foo() or whatever) and that reference will be undefined.
      processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
          "Could not write generated class " + className + ": " + e);
    }
  }

  public static class Member {
    private final ProcessingEnvironment processingEnv;
    private final Element context;
    private final ExecutableElement method;
    private final TypeSimplifier typeSimplifier;
    private final AnnotationOutput annotationOutput;

    Member(
        ProcessingEnvironment processingEnv,
        Element context,
        ExecutableElement method,
        TypeSimplifier typeSimplifier,
        AnnotationOutput annotationDefaults) {
      this.processingEnv = processingEnv;
      this.context = context;
      this.method = method;
      this.typeSimplifier = typeSimplifier;
      this.annotationOutput = annotationDefaults;
    }

    @Override
    public String toString() {
      return method.getSimpleName().toString();
    }

    public String getType() {
      return typeSimplifier.simplify(getTypeMirror());
    }

    public String getComponentType() {
      Preconditions.checkState(getTypeMirror().getKind() == TypeKind.ARRAY);
      ArrayType arrayType = (ArrayType) getTypeMirror();
      return typeSimplifier.simplify(arrayType.getComponentType());
    }

    public TypeMirror getTypeMirror() {
      return method.getReturnType();
    }

    public TypeKind getKind() {
      return getTypeMirror().getKind();
    }

    public String getDefaultValue() {
      AnnotationValue defaultValue = method.getDefaultValue();
      if (defaultValue == null) {
        return null;
      } else {
        return annotationOutput.sourceFormForInitializer(
            defaultValue, processingEnv, method.getSimpleName().toString(), context);
      }
    }
  }

  public static class Parameter {
    private final String typeName;
    private final TypeKind kind;

    Parameter(TypeMirror type, TypeSimplifier typeSimplifier) {
      this.typeName = typeSimplifier.simplify(type);
      this.kind = type.getKind();
    }

    public String getType() {
      return typeName;
    }

    public TypeKind getKind() {
      return kind;
    }
  }
}
