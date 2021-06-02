/*
 * Copyright 2014 Google LLC
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
import static com.google.auto.value.processor.ClassNames.AUTO_ANNOTATION_NAME;
import static com.google.common.collect.Maps.immutableEntry;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Primitives;
import com.google.errorprone.annotations.FormatMethod;
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
import javax.annotation.processing.SupportedAnnotationTypes;
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
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * Javac annotation processor (compiler plugin) to generate annotation implementations. User code
 * never references this class.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoService(Processor.class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
@SupportedAnnotationTypes(AUTO_ANNOTATION_NAME)
public class AutoAnnotationProcessor extends AbstractProcessor {
  public AutoAnnotationProcessor() {}

  private Elements elementUtils;
  private Types typeUtils;
  private Nullables nullables;
  private TypeMirror javaLangObject;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public ImmutableSet<String> getSupportedOptions() {
    return ImmutableSet.of(Nullables.NULLABLE_OPTION);
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.elementUtils = processingEnv.getElementUtils();
    this.typeUtils = processingEnv.getTypeUtils();
    this.nullables = new Nullables(processingEnv);
    this.javaLangObject = elementUtils.getTypeElement("java.lang.Object").asType();
  }

  /**
   * Issue a compilation error. This method does not throw an exception, since we want to continue
   * processing and perhaps report other errors.
   */
  @FormatMethod
  private void reportError(Element e, String msg, Object... msgParams) {
    String formattedMessage = String.format(msg, msgParams);
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, formattedMessage, e);
  }

  /**
   * Issue a compilation error and return an exception that, when thrown, will cause the processing
   * of this class to be abandoned. This does not prevent the processing of other classes.
   */
  @FormatMethod
  private AbortProcessingException abortWithError(Element e, String msg, Object... msgParams) {
    reportError(e, msg, msgParams);
    return new AbortProcessingException();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    process(roundEnv);
    return false;
  }

  private void process(RoundEnvironment roundEnv) {
    TypeElement autoAnnotation = elementUtils.getTypeElement(AUTO_ANNOTATION_NAME);
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(autoAnnotation);
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
      throw abortWithError(method, "@AutoAnnotation method must be static");
    }

    TypeElement annotationElement = getAnnotationReturnType(method);

    Set<Class<?>> wrapperTypesUsedInCollections = wrapperTypesUsedInCollections(method);

    ImmutableMap<String, ExecutableElement> memberMethods = getMemberMethods(annotationElement);
    TypeElement methodClass = MoreElements.asType(method.getEnclosingElement());
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
    vars.equalsParameterType = equalsParameterType();
    vars.pkg = pkg;
    vars.wrapperTypesUsedInCollections = wrapperTypesUsedInCollections;
    vars.gwtCompatible = isGwtCompatible(annotationElement);
    vars.serialVersionUID = computeSerialVersionUid(members, parameters);
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
    return generatedAnnotation(elementUtils, processingEnv.getSourceVersion())
        .map(generatedAnnotation -> TypeEncoder.encode(generatedAnnotation.asType()))
        .orElse("");
  }

  private String equalsParameterType() {
    // Unlike AutoValue, we don't currently try to guess a @Nullable based on the methods in your
    // class. It's the default one or nothing.
    ImmutableList<AnnotationMirror> equalsParameterAnnotations =
        nullables
            .appropriateNullableGivenMethods(ImmutableSet.of())
            .map(ImmutableList::of)
            .orElse(ImmutableList.of());
    return TypeEncoder.encodeWithAnnotations(
        javaLangObject, equalsParameterAnnotations, ImmutableSet.of());
  }

  /**
   * Returns the hashCode of the given AnnotationValue, if that hashCode is guaranteed to be always
   * the same. The hashCode of a String or primitive type never changes. The hashCode of a Class or
   * an enum constant does potentially change in different runs of the same program. The hashCode of
   * an array doesn't change if the hashCodes of its elements don't. Although we could have a
   * similar rule for nested annotation values, we currently don't.
   */
  private static Optional<Integer> invariableHash(AnnotationValue annotationValue) {
    Object value = annotationValue.getValue();
    if (value instanceof String || Primitives.isWrapperType(value.getClass())) {
      return Optional.of(value.hashCode());
    } else if (value instanceof List<?>) {
      @SuppressWarnings("unchecked") // by specification
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
    Set<String> classNames = new HashSet<>();
    for (ExecutableElement method : methods) {
      String qualifiedClassName =
          fullyQualifiedName(
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
    TypeElement type = MoreElements.asType(method.getEnclosingElement());
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = MoreElements.asType(type.getEnclosingElement());
      name = type.getSimpleName() + "_" + name;
    }
    return "AutoAnnotation_" + name + "_" + method.getSimpleName();
  }

  private TypeElement getAnnotationReturnType(ExecutableElement method) {
    TypeMirror returnTypeMirror = method.getReturnType();
    if (returnTypeMirror.getKind() == TypeKind.DECLARED) {
      Element returnTypeElement = typeUtils.asElement(method.getReturnType());
      if (returnTypeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
        return MoreElements.asType(returnTypeElement);
      }
    }
    throw abortWithError(
        method,
        "Return type of @AutoAnnotation method must be an annotation type, not %s",
        returnTypeMirror);
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
      Element context, ImmutableMap<String, ExecutableElement> memberMethods) {
    ImmutableMap.Builder<String, Member> members = ImmutableMap.builder();
    for (Map.Entry<String, ExecutableElement> entry : memberMethods.entrySet()) {
      ExecutableElement memberMethod = entry.getValue();
      String name = memberMethod.getSimpleName().toString();
      members.put(name, new Member(processingEnv, context, memberMethod));
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
      TypeElement annotationElement, ExecutableElement method, Map<String, Member> members) {
    ImmutableMap.Builder<String, Parameter> parameters = ImmutableMap.builder();
    boolean error = false;
    for (VariableElement parameter : method.getParameters()) {
      String name = parameter.getSimpleName().toString();
      Member member = members.get(name);
      if (member == null) {
        reportError(
            parameter,
            "@AutoAnnotation method parameter '%s' must have the same name as a member of %s",
            name,
            annotationElement);
        error = true;
      } else {
        TypeMirror parameterType = parameter.asType();
        TypeMirror memberType = member.getTypeMirror();
        if (compatibleTypes(parameterType, memberType)) {
          parameters.put(name, new Parameter(parameterType));
        } else {
          reportError(
              parameter,
              "@AutoAnnotation method parameter '%s' has type %s but %s.%s has type %s",
              name,
              parameterType,
              annotationElement,
              name,
              memberType);
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
        reportError(
            method,
            "@AutoAnnotation method needs a parameter with name '%s' and type %s"
                + " corresponding to %s.%s, which has no default value",
            memberName,
            members.get(memberName).getType(),
            annotationElement,
            memberName);
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
    TypeMirror arrayElementType = MoreTypes.asArray(memberType).getComponentType();
    TypeMirror wrappedArrayElementType =
        arrayElementType.getKind().isPrimitive()
            ? typeUtils.boxedClass((PrimitiveType) arrayElementType).asType()
            : arrayElementType;
    TypeElement javaUtilCollection =
        elementUtils.getTypeElement(Collection.class.getCanonicalName());
    DeclaredType collectionOfElement =
        typeUtils.getDeclaredType(javaUtilCollection, wrappedArrayElementType);
    return typeUtils.isAssignable(parameterType, collectionOfElement);
  }

  /**
   * Returns the wrapper types ({@code Integer.class} etc) that are used in collection parameters
   * like {@code List<Integer>}. This is needed because we will emit a helper method for each such
   * type, for example to convert {@code Collection<Integer>} into {@code int[]}.
   */
  private ImmutableSet<Class<?>> wrapperTypesUsedInCollections(ExecutableElement method) {
    TypeElement javaUtilCollection = elementUtils.getTypeElement(Collection.class.getName());
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
    return elementUtils.getTypeElement(c.getName()).asType();
  }

  private static boolean isGwtCompatible(TypeElement annotationElement) {
    return annotationElement.getAnnotationMirrors().stream()
        .map(mirror -> mirror.getAnnotationType().asElement())
        .anyMatch(element -> element.getSimpleName().contentEquals("GwtCompatible"));
  }

  private static String fullyQualifiedName(String pkg, String cls) {
    return pkg.isEmpty() ? cls : pkg + "." + cls;
  }

  /**
   * We compute a {@code serialVersionUID} for the generated class based on the names and types of
   * the annotation members that the {@code @AutoAnnotation} method defines. These are exactly the
   * names and types of the instance fields in the generated class. So in the common case where the
   * annotation acquires a new member with a default value, if the {@code @AutoAnnotation} method is
   * not changed then the generated class will acquire an implementation of the new member method
   * which just returns the default value. The {@code serialVersionUID} will not change, which makes
   * sense because the instance fields haven't changed, and instances that were serialized before
   * the new member was added should deserialize fine. On the other hand, if you then add a
   * parameter to the {@code @AutoAnnotation} method for the new member, the implementation class
   * will acquire a new instance field, and we will compute a different {@code serialVersionUID}.
   * That's because an instance serialized before that change would not have a value for the new
   * instance field, which would end up zero or null. Users don't expect annotation methods to
   * return null so that would be bad.
   *
   * <p>We could instead add a {@code readObject(ObjectInputStream)} method that would check that
   * all of the instance fields are really present in the deserialized instance, and perhaps
   * replace them with their default values from the annotation if not. That seems a lot more
   * complicated than is justified, though, especially since the instance fields are final and
   * would have to be set in the deserialized object through reflection.
   */
  private static long computeSerialVersionUid(
      ImmutableMap<String, Member> members, ImmutableMap<String, Parameter> parameters) {
    // TypeMirror.toString() isn't fully specified so it could potentially differ between
    // implementations. Our member.getType() string comes from TypeEncoder and is predictable, but
    // it includes `...` markers around fully-qualified type names, which are used to handle
    // imports. So we remove those markers below.
    String namesAndTypesString =
        members.entrySet().stream()
            .filter(e -> parameters.containsKey(e.getKey()))
            .map(e -> immutableEntry(e.getKey(), e.getValue().getType().replace("`", "")))
            .sorted(comparing(Map.Entry::getKey))
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(joining(";"));
    return Hashing.murmur3_128().hashUnencodedChars(namesAndTypesString).asLong();
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
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.WARNING, "Could not write generated class " + className + ": " + e);
    }
  }

  public static class Member {
    private final ProcessingEnvironment processingEnv;
    private final Element context;
    private final ExecutableElement method;

    Member(ProcessingEnvironment processingEnv, Element context, ExecutableElement method) {
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
      ArrayType arrayType = MoreTypes.asArray(getTypeMirror());
      return TypeEncoder.encode(arrayType.getComponentType());
    }

    public TypeMirror getTypeMirror() {
      return method.getReturnType();
    }

    public TypeKind getKind() {
      return getTypeMirror().getKind();
    }

    // Used as part of the hashCode() computation.
    // See https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Annotation.html#hashCode--
    public int getNameHash() {
      return 127 * toString().hashCode();
    }

    public boolean isArrayOfClassWithBounds() {
      if (getTypeMirror().getKind() != TypeKind.ARRAY) {
        return false;
      }
      TypeMirror componentType = MoreTypes.asArray(getTypeMirror()).getComponentType();
      if (componentType.getKind() != TypeKind.DECLARED) {
        return false;
      }
      DeclaredType declared = MoreTypes.asDeclared(componentType);
      if (!MoreElements.asType(processingEnv.getTypeUtils().asElement(componentType))
          .getQualifiedName()
          .contentEquals("java.lang.Class")) {
        return false;
      }
      if (declared.getTypeArguments().size() != 1) {
        return false;
      }
      TypeMirror parameter = declared.getTypeArguments().get(0);
      if (parameter.getKind() != TypeKind.WILDCARD) {
        return true; // for Class<Foo>
      }
      WildcardType wildcard = MoreTypes.asWildcard(parameter);
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
