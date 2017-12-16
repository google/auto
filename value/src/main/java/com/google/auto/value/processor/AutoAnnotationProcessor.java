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

import static com.google.auto.common.GeneratedAnnotations.generatedAnnotation;

import com.google.auto.common.MoreElements;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
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
import javax.lang.model.type.WildcardType;
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
    boolean claimed =
        (annotations.size() == 1
            && annotations
                .iterator()
                .next()
                .getQualifiedName()
                .contentEquals(AutoAnnotation.class.getName()));
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
        String trace = Throwables.getStackTraceAsString(e);
        reportError(method, "@AutoAnnotation processor threw an exception: %s", trace);
        throw e;
      }
    }
  }

  private void processMethod(ExecutableElement method) {
    if (!method.getModifiers().contains(Modifier.STATIC)) {
      throw abortWithError("@AutoAnnotation method must be static", method);
    }

    TypeElement annotationElement = getAnnotationReturnType(method);

    Set<Class<?>> wrapperTypesUsedInCollections = wrapperTypesUsedInCollections(method);

    ImmutableMap<String, ExecutableElement> memberMethods = getMemberMethods(annotationElement);
    TypeElement methodClass = (TypeElement) method.getEnclosingElement();
    String pkg = TypeSimplifier.packageNameOf(methodClass);

    ImmutableMap<String, AnnotationValue> defaultValues = getDefaultValues(annotationElement);
    ImmutableMap<String, Member> members = getMembers(method, memberMethods);
    ImmutableMap<String, Parameter> parameters = getParameters(annotationElement, method, members);
    validateParameters(annotationElement, method, members, parameters, defaultValues);

    String generatedClassName = generatedClassName(method);

    AutoAnnotationTemplateVars vars = new AutoAnnotationTemplateVars();
    vars.annotationFullName = annotationElement.toString();
    vars.annotationName = TypeEncoder.encode(annotationElement.asType());
    vars.className = generatedClassName;
    vars.generated = getGeneratedTypeName();
    vars.members = members;
    vars.params = parameters;
    vars.pkg = pkg;
    vars.wrapperTypesUsedInCollections = wrapperTypesUsedInCollections;
    vars.gwtCompatible = isGwtCompatible(annotationElement);
    ImmutableMap<String, Integer> invariableHashes = invariableHashes(members, parameters.keySet());
    vars.invariableHashSum = 0;
    for (int h : invariableHashes.values()) {
      vars.invariableHashSum += h;
    }
    vars.invariableHashes = invariableHashes.keySet();
    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, pkg, annotationElement.asType());
    text = Reformatter.fixup(text);
    String fullName = fullyQualifiedName(pkg, generatedClassName);
    writeSourceFile(fullName, text, methodClass);
  }

  private String getGeneratedTypeName() {
    return generatedAnnotation(processingEnv.getElementUtils())
        .map(generatedAnnotation -> TypeEncoder.encode(generatedAnnotation.asType()))
        .orElse(null);
  }

  /**
   * Returns the hashCode of the given AnnotationValue, if that hashCode is guaranteed to be always
   * the same. The hashCode of a String or primitive type never changes. The hashCode of a Class
   * or an enum constant does potentially change in different runs of the same program. The hashCode
   * of an array doesn't change if the hashCodes of its elements don't. Although we could have a
   * similar rule for nested annotation values, we currently don't.
   */
  private static Optional<Integer> invariableHash(AnnotationValue annotationValue) {
    Object value = annotationValue.getValue();
    if (value instanceof String || Primitives.isWrapperType(value.getClass())) {
      return Optional.of(value.hashCode());
    } else if (value instanceof List<?>) {
      @SuppressWarnings("unchecked")  // by specification
      List<? extends AnnotationValue> list = (List<? extends AnnotationValue>) value;
      return invariableHash(list);
    } else {
      return Optional.empty();
    }
  }

  private static Optional<Integer> invariableHash(
      List<? extends AnnotationValue> annotationValues) {
    int h = 1;
    for (AnnotationValue annotationValue : annotationValues) {
      Optional<Integer> maybeHash = invariableHash(annotationValue);
      if (!maybeHash.isPresent()) {
        return Optional.empty();
      }
      h = h * 31 + maybeHash.get();
    }
    return Optional.of(h);
  }

  /**
   * Returns a map from the names of members with invariable hashCodes to the values of those
   * hashCodes.
   */
  private static ImmutableMap<String, Integer> invariableHashes(
      ImmutableMap<String, Member> members, ImmutableSet<String> parameters) {
    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    for (String element : members.keySet()) {
      if (!parameters.contains(element)) {
        Member member = members.get(element);
        AnnotationValue annotationValue = member.method.getDefaultValue();
        Optional<Integer> invariableHash = invariableHash(annotationValue);
        if (invariableHash.isPresent()) {
          builder.put(element, (element.hashCode() * 127) ^ invariableHash.get());
        }
      }
    }
    return builder.build();
  }

  private boolean methodsAreOverloaded(List<ExecutableElement> methods) {
    boolean overloaded = false;
    Set<String> classNames = new HashSet<String>();
    for (ExecutableElement method : methods) {
      String qualifiedClassName = fullyQualifiedName(
          MoreElements.getPackage(method).getQualifiedName().toString(),
          generatedClassName(method));
      if (!classNames.add(qualifiedClassName)) {
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
      ImmutableMap<String, ExecutableElement> memberMethods) {
    ImmutableMap.Builder<String, Member> members = ImmutableMap.builder();
    for (Map.Entry<String, ExecutableElement> entry : memberMethods.entrySet()) {
      ExecutableElement memberMethod = entry.getValue();
      String name = memberMethod.getSimpleName().toString();
      members.put(
          name,
          new Member(processingEnv, context, memberMethod));
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

  private ImmutableMap<String, Parameter> getParameters(
      TypeElement annotationElement,
      ExecutableElement method,
      Map<String, Member> members) {
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
          parameters.put(name, new Parameter(parameterType));
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

  private TypeMirror getTypeMirror(Class<?> c) {
    return processingEnv.getElementUtils().getTypeElement(c.getName()).asType();
  }

  private static boolean isGwtCompatible(TypeElement annotationElement) {
    return annotationElement.getAnnotationMirrors().stream()
        .map(mirror -> mirror.getAnnotationType().asElement())
        .anyMatch(element -> element.getSimpleName().contentEquals("GwtCompatible"));
  }

  private static String fullyQualifiedName(String pkg, String cls) {
    return pkg.isEmpty() ? cls : pkg + "." + cls;
  }

  private void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      try (Writer writer = sourceFile.openWriter()) {
        writer.write(text);
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

    Member(
        ProcessingEnvironment processingEnv,
        Element context,
        ExecutableElement method) {
      this.processingEnv = processingEnv;
      this.context = context;
      this.method = method;
    }

    @Override
    public String toString() {
      return method.getSimpleName().toString();
    }

    public String getType() {
      return TypeEncoder.encode(getTypeMirror());
    }

    public String getComponentType() {
      Preconditions.checkState(getTypeMirror().getKind() == TypeKind.ARRAY);
      ArrayType arrayType = (ArrayType) getTypeMirror();
      return TypeEncoder.encode(arrayType.getComponentType());
    }

    public TypeMirror getTypeMirror() {
      return method.getReturnType();
    }

    public TypeKind getKind() {
      return getTypeMirror().getKind();
    }

    public boolean isArrayOfClassWithBounds() {
      if (getTypeMirror().getKind() != TypeKind.ARRAY) {
        return false;
      }
      TypeMirror componentType = ((ArrayType) getTypeMirror()).getComponentType();
      if (componentType.getKind() != TypeKind.DECLARED) {
        return false;
      }
      DeclaredType declared = (DeclaredType) componentType;
      if (!((TypeElement) processingEnv.getTypeUtils().asElement(componentType))
          .getQualifiedName()
          .contentEquals("java.lang.Class")) {
        return false;
      }
      if (declared.getTypeArguments().size() != 1) {
        return false;
      }
      TypeMirror parameter = declared.getTypeArguments().get(0);
      if (parameter.getKind() != TypeKind.WILDCARD) {
        return true;  // for Class<Foo>
      }
      WildcardType wildcard = (WildcardType) parameter;
      // In theory, we should check if getExtendsBound() != Object, since '?' is equivalent to
      // '? extends Object', but, experimentally, neither javac or ecj will sets getExtendsBound()
      // to 'Object', so there isn't a point in checking.
      return wildcard.getSuperBound() != null || wildcard.getExtendsBound() != null;
    }

    public String getDefaultValue() {
      AnnotationValue defaultValue = method.getDefaultValue();
      if (defaultValue == null) {
        return null;
      } else {
        return AnnotationOutput.sourceFormForInitializer(
            defaultValue, processingEnv, method.getSimpleName().toString(), context);
      }
    }
  }

  public static class Parameter {
    private final String typeName;
    private final TypeKind kind;

    Parameter(TypeMirror type) {
      this.typeName = TypeEncoder.encode(type);
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
