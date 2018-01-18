/*
 * Copyright (C) 2018 Google, Inc.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.auto.common.Visibility;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.beans.Introspector;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;

/**
 * Shared code between AutoValueProcessor and AutoOneOfProcessor.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class AutoValueOrOneOfProcessor extends AbstractProcessor {
  private final Class<? extends Annotation> annotationClass;

  /**
   * Qualified names of {@code @AutoValue} or {@code AutoOneOf} classes that we attempted to process
   * but had to abandon because we needed other types that they referenced and those other types
   * were missing.
   */
  private final List<String> deferredTypeNames = new ArrayList<>();

  AutoValueOrOneOfProcessor(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
  }

  private ErrorReporter errorReporter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    errorReporter = new ErrorReporter(processingEnv);
  }

  final ErrorReporter errorReporter() {
    return errorReporter;
  }

  final Types typeUtils() {
    return processingEnv.getTypeUtils();
  }

  final Elements elementUtils() {
    return processingEnv.getElementUtils();
  }

  @Override
  public final Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(annotationClass.getName());
  }

  @Override
  public final SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    List<TypeElement> deferredTypes = deferredTypeNames.stream()
        .map(name -> elementUtils().getTypeElement(name))
        .collect(toList());
    if (roundEnv.processingOver()) {
      // This means that the previous round didn't generate any new sources, so we can't have found
      // any new instances of @AutoValue; and we can't have any new types that are the reason a type
      // was in deferredTypes.
      for (TypeElement type : deferredTypes) {
        errorReporter.reportError(
            "Did not generate @" + annotationClass.getSimpleName() + " class for " +
                type.getQualifiedName() + " because it references undefined types",
            type);
      }
      return false;
    }
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(annotationClass);
    List<TypeElement> types = new ImmutableList.Builder<TypeElement>()
        .addAll(deferredTypes)
        .addAll(ElementFilter.typesIn(annotatedElements))
        .build();
    deferredTypeNames.clear();
    for (TypeElement type : types) {
      try {
        processType(type);
      } catch (AbortProcessingException e) {
        // We abandoned this type; continue with the next.
      } catch (MissingTypeException e) {
        // We abandoned this type, but only because we needed another type that it references and
        // that other type was missing. It is possible that the missing type will be generated by
        // further annotation processing, so we will try again on the next round (perhaps failing
        // again and adding it back to the list). We save the name of the @AutoValue type rather
        // than its TypeElement because it is not guaranteed that it will be represented by
        // the same TypeElement on the next round.
        deferredTypeNames.add(type.getQualifiedName().toString());
      } catch (RuntimeException e) {
        String trace = Throwables.getStackTraceAsString(e);
        errorReporter.reportError(
            "@" + annotationClass.getSimpleName() + " processor threw an exception: " + trace,
            type);
        throw e;
      }
    }
    return false;  // never claim annotation, because who knows what other processors want?
  }

  /**
   * Analyzes a single {@code @AutoValue} or {@code @AutoOneOf} class, and outputs the corresponding
   * implementation class or classes.
   *
   * @param type the class with the {@code @AutoValue} or {@code @AutoOneOf} annotation.
   */
  abstract void processType(TypeElement type);

  static String generatedClassName(TypeElement type, String prefix) {
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = (TypeElement) type.getEnclosingElement();
      name = type.getSimpleName() + "_" + name;
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    String dot = pkg.isEmpty() ? "" : ".";
    return pkg + dot + prefix + name;
  }

  private static boolean isJavaLangObject(TypeElement type) {
    return type.getSuperclass().getKind() == TypeKind.NONE && type.getKind() == ElementKind.CLASS;
  }

  enum ObjectMethod {
    NONE, TO_STRING, EQUALS, HASH_CODE
  }

  /**
   * Determines which of the three public non-final methods from {@code java.lang.Object}, if any,
   * is overridden by the given method.
   */
  static ObjectMethod objectMethodToOverride(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    switch (method.getParameters().size()) {
      case 0:
        if (name.equals("toString")) {
          return ObjectMethod.TO_STRING;
        } else if (name.equals("hashCode")) {
          return ObjectMethod.HASH_CODE;
        }
        break;
      case 1:
        if (name.equals("equals")
            && method.getParameters().get(0).asType().toString().equals("java.lang.Object")) {
          return ObjectMethod.EQUALS;
        }
        break;
      default:
        // No relevant Object methods have more than one parameter.
    }
    return ObjectMethod.NONE;
  }

  /**
   * Returns a bi-map between property names and the corresponding abstract property methods.
   */
  final ImmutableBiMap<String, ExecutableElement> propertyNameToMethodMap(
      Set<ExecutableElement> propertyMethods) {
    Map<String, ExecutableElement> map = Maps.newLinkedHashMap();
    Set<String> reportedDups = new HashSet<>();
    boolean allPrefixed = gettersAllPrefixed(propertyMethods);
    for (ExecutableElement method : propertyMethods) {
      String methodName = method.getSimpleName().toString();
      String name = allPrefixed ? nameWithoutPrefix(methodName) : methodName;
      ExecutableElement old = map.put(name, method);
      if (old != null) {
        errorReporter.reportError(
            "More than one @" + annotationClass.getSimpleName() + " property called " + name,
            method);
        if (reportedDups.add(name)) {
          errorReporter.reportError(
              "More than one @" + annotationClass.getSimpleName() + " property called " + name,
              old);
        }
      }
    }
    return ImmutableBiMap.copyOf(map);
  }

  private static boolean gettersAllPrefixed(Set<ExecutableElement> methods) {
    return prefixedGettersIn(methods).size() == methods.size();
  }

  /**
   * Returns the subset of the given zero-arg methods whose names begin with {@code get}. Also
   * includes {@code isFoo} methods if they return {@code boolean}. This corresponds to JavaBeans
   * conventions.
   */
  static ImmutableSet<ExecutableElement> prefixedGettersIn(Iterable<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> getters = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      String name = method.getSimpleName().toString();
      // Note that getfoo() (without a capital) is still a getter.
      boolean get = name.startsWith("get") && !name.equals("get");
      boolean is = name.startsWith("is") && !name.equals("is")
          && method.getReturnType().getKind() == TypeKind.BOOLEAN;
      if (get || is) {
        getters.add(method);
      }
    }
    return getters.build();
  }

  /**
   * Returns the name of the property defined by the given getter. A getter called {@code getFoo()}
   * or {@code isFoo()} defines a property called {@code foo}. For consistency with JavaBeans, a
   * getter called {@code getHTMLPage()} defines a property called {@code HTMLPage}. The
   * <a href="https://docs.oracle.com/javase/8/docs/api/java/beans/Introspector.html#decapitalize-java.lang.String-">
   * rule</a> is: the name of the property is the part after {@code get} or {@code is}, with the
   * first letter lowercased <i>unless</i> the first two letters are uppercase. This works well
   * for the {@code HTMLPage} example, but in these more enlightened times we use {@code HtmlPage}
   * anyway, so the special behaviour is not useful, and of course it behaves poorly with examples
   * like {@code OAuth}.
   */
  private static String nameWithoutPrefix(String name) {
    if (name.startsWith("get")) {
      name = name.substring(3);
    } else {
      assert name.startsWith("is");
      name = name.substring(2);
    }
    return Introspector.decapitalize(name);
  }

  /**
   * Checks that, if the given {@code @AutoValue} or {@code @AutoOneOf} class is nested, it is
   * static and not private. This check is not necessary for correctness, since the generated code
   * would not compile if the check fails, but it produces better error messages for the user.
   */
  final void checkModifiersIfNested(TypeElement type) {
    ElementKind enclosingKind = type.getEnclosingElement().getKind();
    if (enclosingKind.isClass() || enclosingKind.isInterface()) {
      if (type.getModifiers().contains(Modifier.PRIVATE)) {
        errorReporter.abortWithError(
            "@" + annotationClass.getSimpleName() + " class must not be private", type);
      } else if (Visibility.effectiveVisibilityOfElement(type).equals(Visibility.PRIVATE)) {
        // The previous case, where the class itself is private, is much commoner so it deserves
        // its own error message, even though it would be caught by the test here too.
        errorReporter.abortWithError(
            "@" + annotationClass.getSimpleName() + " class must not be nested in a private class",
            type);
      }
      if (!type.getModifiers().contains(Modifier.STATIC)) {
        errorReporter.abortWithError(
            "Nested @" + annotationClass.getSimpleName() + " class must be static", type);
      }
    }
    // In principle type.getEnclosingElement() could be an ExecutableElement (for a class
    // declared inside a method), but since RoundEnvironment.getElementsAnnotatedWith doesn't
    // return such classes we won't see them here.
  }

  /**
   * Modifies the values of the given map to avoid reserved words. If we have a getter called
   * {@code getPackage()} then we can't use the identifier {@code package} to represent
   * its value since that's a reserved word.
   */
  static void fixReservedIdentifiers(Map<?, String> methodToIdentifier) {
    for (Map.Entry<?, String> entry : methodToIdentifier.entrySet()) {
      if (SourceVersion.isKeyword(entry.getValue())) {
        entry.setValue(disambiguate(entry.getValue(), methodToIdentifier.values()));
      }
    }
  }

  private static String disambiguate(String name, Collection<String> existingNames) {
    for (int i = 0; ; i++) {
      String candidate = name + i;
      if (!existingNames.contains(candidate)) {
        return candidate;
      }
    }
  }

  /**
   * Given a list of all methods defined in or inherited by a class, returns a set indicating
   * which of equals, hashCode, and toString should be generated.
   */
  static Set<ObjectMethod> determineObjectMethodsToGenerate(Set<ExecutableElement> methods) {
    Set<ObjectMethod> methodsToGenerate = EnumSet.noneOf(ObjectMethod.class);
    for (ExecutableElement method : methods) {
      ObjectMethod override = objectMethodToOverride(method);
      boolean canGenerate = method.getModifiers().contains(Modifier.ABSTRACT)
          || isJavaLangObject((TypeElement) method.getEnclosingElement());
      if (!override.equals(ObjectMethod.NONE) && canGenerate) {
        methodsToGenerate.add(override);
      }
    }
    return methodsToGenerate;
  }

  /**
   * Returns the subset of all abstract methods in the given set of methods. A given method
   * signature is only mentioned once, even if it is inherited on more than one path.
   */
  static ImmutableSet<ExecutableElement> abstractMethodsIn(
      ImmutableSet<ExecutableElement> methods) {
    Set<Name> noArgMethods = Sets.newHashSet();
    ImmutableSet.Builder<ExecutableElement> abstracts = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        boolean hasArgs = !method.getParameters().isEmpty();
        if (hasArgs || noArgMethods.add(method.getSimpleName())) {
          // If an abstract method with the same signature is inherited on more than one path,
          // we only add it once. At the moment we only do this check for no-arg methods. All
          // methods that AutoValue will implement are either no-arg methods or equals(Object).
          // The former is covered by this check and the latter will lead to vars.equals being
          // set to true, regardless of how many times it appears. So the only case that is
          // covered imperfectly here is that of a method that is inherited on more than one path
          // and that will be consumed by an extension. We could check parameters as well, but that
          // can be a bit tricky if any of the parameters are generic.
          abstracts.add(method);
        }
      }
    }
    return abstracts.build();
  }

  /**
   * Returns the subset of property methods in the given set of abstract methods. A property method
   * has no arguments, is not void, and is not {@code hashCode()} or {@code toString()}.
   */
  static ImmutableSet<ExecutableElement> propertyMethodsIn(Set<ExecutableElement> abstractMethods) {
    ImmutableSet.Builder<ExecutableElement> properties = ImmutableSet.builder();
    for (ExecutableElement method : abstractMethods) {
      if (method.getParameters().isEmpty()
          && method.getReturnType().getKind() != TypeKind.VOID
          && objectMethodToOverride(method) == ObjectMethod.NONE) {
        properties.add(method);
      }
    }
    return properties.build();
  }

  /**
   * Checks that the return type of the given property method is allowed. Currently, this means
   * that it cannot be an array, unless it is a primitive array.
   */
  final void checkReturnType(TypeElement autoValueClass, ExecutableElement getter) {
    TypeMirror type = getter.getReturnType();
    if (type.getKind() == TypeKind.ARRAY) {
      TypeMirror componentType = ((ArrayType) type).getComponentType();
      if (componentType.getKind().isPrimitive()) {
        warnAboutPrimitiveArrays(autoValueClass, getter);
      } else {
        errorReporter.reportError(
            "An @" + annotationClass.getSimpleName() + " class cannot define an array-valued"
                + " property unless it is a primitive array", getter);
      }
    }
  }

  private void warnAboutPrimitiveArrays(TypeElement autoValueClass, ExecutableElement getter) {
    boolean suppressed = false;
    com.google.common.base.Optional<AnnotationMirror> maybeAnnotation =
        getAnnotationMirror(getter, SuppressWarnings.class);
    if (maybeAnnotation.isPresent()) {
      AnnotationValue listValue = getAnnotationValue(maybeAnnotation.get(), "value");
      suppressed = listValue.accept(new ContainsMutableVisitor(), null);
    }
    if (!suppressed) {
      // If the primitive-array property method is defined directly inside the @AutoValue class,
      // then our error message should point directly to it. But if it is inherited, we don't
      // want to try to make the error message point to the inherited definition, since that would
      // be confusing (there is nothing wrong with the definition itself), and won't work if the
      // inherited class is not being recompiled. Instead, in this case we point to the @AutoValue
      // class itself, and we include extra text in the error message that shows the full name of
      // the inherited method.
      String warning =
          "An @" + annotationClass.getSimpleName() + " property that is a primitive array returns "
              + "the original array, which can therefore be modified by the caller. If this OK, "
              + "you can suppress this warning with @SuppressWarnings(\"mutable\"). Otherwise, you "
              + "should replace the property with an immutable type, perhaps a simple wrapper "
              + "around the original array.";
      boolean sameClass = getter.getEnclosingElement().equals(autoValueClass);
      if (sameClass) {
        errorReporter.reportWarning(warning, getter);
      } else {
        errorReporter.reportWarning(
            warning + " Method: " + getter.getEnclosingElement() + "." + getter, autoValueClass);
      }
    }
  }

  // Detects whether the visited AnnotationValue is an array that contains the string "mutable".
  // The simpler approach using Element.getAnnotation(SuppressWarnings.class) doesn't work if
  // the annotation has an undefined reference, like @SuppressWarnings(UNDEFINED).
  // TODO(emcmanus): replace with a method from auto-common when that is available.
  private static class ContainsMutableVisitor extends SimpleAnnotationValueVisitor8<Boolean, Void> {
    @Override
    public Boolean visitArray(List<? extends AnnotationValue> list, Void p) {
      return list.stream().map(av -> av.getValue()).anyMatch("mutable"::equals);
    }
  }

  /**
   * Returns a string like {@code "1234L"} if {@code type instanceof Serializable} and defines
   * {@code serialVersionUID = 1234L}; otherwise {@code ""}.
   */
  final String getSerialVersionUID(TypeElement type) {
    TypeMirror serializable =
        elementUtils().getTypeElement(Serializable.class.getName()).asType();
    if (typeUtils().isAssignable(type.asType(), serializable)) {
      List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
      for (VariableElement field : fields) {
        if (field.getSimpleName().contentEquals("serialVersionUID")) {
          Object value = field.getConstantValue();
          if (field.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.FINAL))
              && field.asType().getKind() == TypeKind.LONG
              && value != null) {
            return value + "L";
          } else {
            errorReporter.reportError(
                "serialVersionUID must be a static final long compile-time constant", field);
            break;
          }
        }
      }
    }
    return "";
  }

  /**
   * Returns the {@code @AutoValue} or {@code @AutoOneOf} type parameters, with a ? for every type.
   * If we have {@code @AutoValue abstract class Foo<T extends Something>} then this method will
   * return just {@code <?>}.
   */
  static String wildcardTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      return typeParameters.stream().map(e -> "?").collect(joining(", ", "<", ">"));
    }
  }
}
