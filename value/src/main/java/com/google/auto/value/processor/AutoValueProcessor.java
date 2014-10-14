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

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
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
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see AutoValue
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
public class AutoValueProcessor extends AbstractProcessor {
  private static final boolean SILENT = true;

  public AutoValueProcessor() {}

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoValue.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private void note(String msg) {
    if (!SILENT) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
    }
  }

  private void reportWarning(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, e);
  }

  /**
   * Issue a compilation error. This method does not throw an exception, since we want to
   * continue processing and perhaps report other errors. It is a good idea to introduce a
   * test case in CompilationErrorsTest for any new call to reportError(...) to ensure that we
   * continue correctly after an error.
   */
  private void reportError(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }

  /**
   * Issue a compilation error and abandon the processing of this class. This does not prevent
   * the processing of other classes.
   */
  private void abortWithError(String msg, Element e) {
    reportError(msg, e);
    throw new AbortProcessingException();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.size() == 1
        && Iterables.getOnlyElement(annotations).getQualifiedName().toString().equals(
            AutoValue.class.getName())) {
      process(roundEnv);
    }
    return false;  // never claim annotation, because who knows what other processors want?
  }

  private void process(RoundEnvironment roundEnv) {
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(AutoValue.class);
    Collection<? extends TypeElement> types = ElementFilter.typesIn(annotatedElements);
    for (TypeElement type : types) {
      try {
        processType(type);
      } catch (AbortProcessingException e) {
        // We abandoned this type, but continue with the next.
      } catch (RuntimeException e) {
        // Don't propagate this exception, which will confusingly crash the compiler.
        // Instead, report a compiler error with the stack trace.
        String trace = Throwables.getStackTraceAsString(e);
        reportError("@AutoValue processor threw an exception: " + trace, type);
      }
    }
  }

  private String generatedClassName(TypeElement type, String prefix) {
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = (TypeElement) type.getEnclosingElement();
      name = type.getSimpleName() + "_" + name;
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    String dot = pkg.isEmpty() ? "" : ".";
    return pkg + dot + prefix + name;
  }

  private String generatedSubclassName(TypeElement type) {
    return generatedClassName(type, "AutoValue_");
  }

  // Return the name of the class, including any enclosing classes but not the package.
  private static String classNameOf(TypeElement type) {
    String name = type.getQualifiedName().toString();
    String pkgName = TypeSimplifier.packageNameOf(type);
    if (!pkgName.isEmpty()) {
      return name.substring(pkgName.length() + 1);
    } else {
      return name;
    }
  }

  /**
   * A property of an {@code @AutoValue} class, defined by one of its abstract methods.
   * An instance of this class is made available to the Velocity template engine for
   * each property. The public methods of this class define JavaBeans-style properties
   * that are accessible from templates. For example {@link #getType()} means we can
   * write {@code $p.type} for a Velocity variable {@code $p} that is a {@code Property}.
   */
  public static class Property {
    private final ExecutableElement method;
    private final String type;
    private final ImmutableList<String> annotationClasses;
    private final TypeSimplifier typeSimplifier;

    Property(ExecutableElement method, String type, TypeSimplifier typeSimplifier) {
      this.method = method;
      this.type = type;
      this.typeSimplifier = typeSimplifier;
      this.annotationClasses = buildAnnotationClasses();
    }

    private ImmutableList<String> buildAnnotationClasses() {
      ImmutableList.Builder<String> builder = ImmutableList.builder();

      for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
        String annotationName = typeSimplifier.simplify(annotationMirror.getAnnotationType());
        String annotation = "@" + annotationName;

        List<String> values = FluentIterable
            .from(annotationMirror.getElementValues().entrySet())
            .filter(Predicates.notNull())
            .transform(valuesToString)
            .toList();

        if (!values.isEmpty()) {
          annotation += "(" + Joiner.on(", ").join(values) + ")";
        }

        builder.add(annotation);
      }

      return builder.build();
    }

    private static Function<Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>, String> valuesToString =
        new Function<Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>, String>() {
          @Override
          public String apply(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry) {
            return entry.getKey().getSimpleName().toString() + "=" + entry.getValue().toString();
          }
    };

    @Override
    public String toString() {
      return method.getSimpleName().toString();
    }

    TypeElement getOwner() {
      return (TypeElement) method.getEnclosingElement();
    }

    TypeMirror getTypeMirror() {
      return method.getReturnType();
    }

    public String getType() {
      return type;
    }

    public TypeKind getKind() {
      return method.getReturnType().getKind();
    }

    public List<String> getAnnotations() {
      return annotationClasses;
    }

    public boolean isNullable() {
      for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
        String name = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
        if (name.equals("Nullable")) {
          return true;
        }
      }
      return false;
    }

    public String getAccess() {
      Set<Modifier> mods = method.getModifiers();
      if (mods.contains(Modifier.PUBLIC)) {
        return "public ";
      } else if (mods.contains(Modifier.PROTECTED)) {
        return "protected ";
      } else {
        return "";
      }
    }
  }

  private static boolean isJavaLangObject(TypeElement type) {
    return type.getSuperclass().getKind() == TypeKind.NONE && type.getKind() == ElementKind.CLASS;
  }

  private enum ObjectMethodToOverride {
    NONE, TO_STRING, EQUALS, HASH_CODE
  }

  private static ObjectMethodToOverride objectMethodToOverride(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    switch (method.getParameters().size()) {
      case 0:
        if (name.equals("toString")) {
          return ObjectMethodToOverride.TO_STRING;
        } else if (name.equals("hashCode")) {
          return ObjectMethodToOverride.HASH_CODE;
        }
        break;
      case 1:
        if (name.equals("equals")
            && method.getParameters().get(0).asType().toString().equals("java.lang.Object")) {
          return ObjectMethodToOverride.EQUALS;
        }
        break;
    }
    return ObjectMethodToOverride.NONE;
  }

  private void findLocalAndInheritedMethods(TypeElement type, List<ExecutableElement> methods) {
    note("Looking at methods in " + type);
    Types typeUtils = processingEnv.getTypeUtils();
    Elements elementUtils = processingEnv.getElementUtils();
    for (TypeMirror superInterface : type.getInterfaces()) {
      findLocalAndInheritedMethods((TypeElement) typeUtils.asElement(superInterface), methods);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      findLocalAndInheritedMethods(
          (TypeElement) typeUtils.asElement(type.getSuperclass()), methods);
    }
    // Add each method of this class, and in so doing remove any inherited method it overrides.
    // This algorithm is quadratic in the number of methods but it's hard to see how to improve
    // that while still using Elements.overrides.
    List<ExecutableElement> theseMethods = ElementFilter.methodsIn(type.getEnclosedElements());
    for (ExecutableElement method : theseMethods) {
      if (!method.getModifiers().contains(Modifier.PRIVATE)) {
        boolean alreadySeen = false;
        for (Iterator<ExecutableElement> methodIter = methods.iterator(); methodIter.hasNext();) {
          ExecutableElement otherMethod = methodIter.next();
          if (elementUtils.overrides(method, otherMethod, type)) {
            methodIter.remove();
          } else if (method.getSimpleName().equals(otherMethod.getSimpleName())
              && method.getParameters().equals(otherMethod.getParameters())) {
            // If we inherit this method on more than one path, we don't want to add it twice.
            alreadySeen = true;
          }
        }
        if (!alreadySeen) {
          methods.add(method);
        }
      }
    }
  }

  private void processType(TypeElement type) {
    AutoValue autoValue = type.getAnnotation(AutoValue.class);
    if (autoValue == null) {
      // This shouldn't happen unless the compilation environment is buggy,
      // but it has happened in the past and can crash the compiler.
      abortWithError("annotation processor for @AutoValue was invoked with a type that "
          + "does not have that annotation; this is probably a compiler bug", type);
    }
    if (type.getKind() != ElementKind.CLASS) {
      abortWithError("@" + AutoValue.class.getName() + " only applies to classes", type);
    }
    if (ancestorIsAutoValue(type)) {
      abortWithError("One @AutoValue class may not extend another", type);
    }
    if (implementsAnnotation(type)) {
      abortWithError("@AutoValue may not be used to implement an annotation interface; "
          + "try using @AutoAnnotation instead", type);
    }
    AutoValueTemplateVars vars = new AutoValueTemplateVars();
    vars.pkg = TypeSimplifier.packageNameOf(type);
    vars.origClass = classNameOf(type);
    vars.simpleClassName = TypeSimplifier.simpleNameOf(vars.origClass);
    vars.subclass = TypeSimplifier.simpleNameOf(generatedSubclassName(type));
    defineVarsForType(type, vars);
    String text = vars.toText();
    text = Reformatter.fixup(text);
    writeSourceFile(generatedSubclassName(type), text, type);
    GwtSerialization gwtSerialization = new GwtSerialization(processingEnv, type);
    gwtSerialization.maybeWriteGwtSerializer(vars);
  }

  private void defineVarsForType(TypeElement type, AutoValueTemplateVars vars) {
    Types typeUtils = processingEnv.getTypeUtils();
    List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    findLocalAndInheritedMethods(type, methods);
    determineObjectMethodsToGenerate(methods, vars);
    ImmutableList<ExecutableElement> toImplement = methodsToImplement(methods);
    Set<TypeMirror> types = new TypeMirrorSet();
    types.addAll(returnTypesOf(toImplement));
    TypeMirror javaxAnnotationGenerated = getTypeMirror(Generated.class);
    types.add(javaxAnnotationGenerated);
    TypeMirror javaUtilArrays = getTypeMirror(Arrays.class);
    if (containsArrayType(types)) {
      // If there are array properties then we will be referencing java.util.Arrays.
      // Arrange to import it unless that would introduce ambiguity.
      types.add(javaUtilArrays);
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtils, pkg, types, type.asType());
    vars.imports = typeSimplifier.typesToImport();
    vars.generated = typeSimplifier.simplify(javaxAnnotationGenerated);
    vars.arrays = typeSimplifier.simplify(javaUtilArrays);
    List<Property> props = new ArrayList<Property>();
    for (ExecutableElement method : toImplement) {
      String propType = typeSimplifier.simplify(method.getReturnType());
      Property prop = new Property(method, propType, typeSimplifier);
      props.add(prop);
    }
    // If we are running from Eclipse, undo the work of its compiler which sorts methods.
    eclipseHack().reorderProperties(props);
    vars.props = props;
    vars.serialVersionUID = getSerialVersionUID(type);
    vars.formalTypes = typeSimplifier.formalTypeParametersString(type);
    vars.actualTypes = actualTypeParametersString(type);
    vars.wildcardTypes = wildcardTypeParametersString(type);
  }

  private Set<TypeMirror> returnTypesOf(List<ExecutableElement> methods) {
    Set<TypeMirror> returnTypes = new TypeMirrorSet();
    for (ExecutableElement method : methods) {
      returnTypes.add(method.getReturnType());
    }
    return returnTypes;
  }

  private static boolean containsArrayType(Set<TypeMirror> types) {
    for (TypeMirror type : types) {
      if (type.getKind() == TypeKind.ARRAY) {
        return true;
      }
    }
    return false;
  }

  /**
   * Given a list of all methods defined in or inherited by a class, sets the equals, hashCode, and
   * toString fields of vars according as the corresponding methods should be generated.
   */
  private static void determineObjectMethodsToGenerate(
      List<ExecutableElement> methods, AutoValueTemplateVars vars) {
    // The defaults here only come into play when an ancestor class doesn't exist.
    // Compilation will fail in that case, but we don't want it to crash the compiler with
    // an exception before it does. If all ancestors do exist then we will definitely find
    // definitions of these three methods (perhaps the ones in Object) so we will overwrite these:
    vars.equals = false;
    vars.hashCode = false;
    vars.toString = false;
    for (ExecutableElement method : methods) {
      ObjectMethodToOverride override = objectMethodToOverride(method);
      boolean canGenerate = method.getModifiers().contains(Modifier.ABSTRACT)
          || isJavaLangObject((TypeElement) method.getEnclosingElement());
      switch (override) {
        case EQUALS:
          vars.equals = canGenerate;
          break;
        case HASH_CODE:
          vars.hashCode = canGenerate;
          break;
        case TO_STRING:
          vars.toString = canGenerate;
          break;
      }
    }
  }

  private ImmutableList<ExecutableElement> methodsToImplement(List<ExecutableElement> methods) {
    ImmutableList.Builder<ExecutableElement> toImplement = ImmutableList.builder();
    boolean errors = false;
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)
          && objectMethodToOverride(method) == ObjectMethodToOverride.NONE) {
        if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID) {
          if (isReferenceArrayType(method.getReturnType())) {
            reportError("An @AutoValue class cannot define an array-valued property unless it is "
                + "a primitive array", method);
            errors = true;
          }
          toImplement.add(method);
        } else {
          // This could reasonably be an error, were it not for an Eclipse bug in
          // ElementUtils.override that sometimes fails to recognize that one method overrides
          // another, and therefore leaves us with both an abstract method and the subclass method
          // that overrides it. This shows up in AutoValueTest.LukesBase for example.
          reportWarning("@AutoValue classes cannot have abstract methods other than "
              + "property getters", method);
        }
      }
    }
    if (errors) {
      throw new AbortProcessingException();
    }
    return toImplement.build();
  }

  private static boolean isReferenceArrayType(TypeMirror type) {
    return type.getKind() == TypeKind.ARRAY
        && !((ArrayType) type).getComponentType().getKind().isPrimitive();
  }

  private void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      note(text);
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      Writer writer = sourceFile.openWriter();
      try {
        writer.write(text);
      } finally {
        writer.close();
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Could not write generated class " + className + ": " + e);
    }
  }

  private boolean ancestorIsAutoValue(TypeElement type) {
    while (true) {
      TypeMirror parentMirror = type.getSuperclass();
      if (parentMirror.getKind() == TypeKind.NONE) {
        return false;
      }
      Types typeUtils = processingEnv.getTypeUtils();
      TypeElement parentElement = (TypeElement) typeUtils.asElement(parentMirror);
      if (parentElement.getAnnotation(AutoValue.class) != null) {
        return true;
      }
      type = parentElement;
    }
  }

  private boolean implementsAnnotation(TypeElement type) {
    Types typeUtils = processingEnv.getTypeUtils();
    return typeUtils.isAssignable(type.asType(), getTypeMirror(Annotation.class));
  }

  // Return a string like "1234L" if type instanceof Serializable and defines
  // serialVersionUID = 1234L, otherwise "".
  private String getSerialVersionUID(TypeElement type) {
    Types typeUtils = processingEnv.getTypeUtils();
    TypeMirror serializable = getTypeMirror(Serializable.class);
    if (typeUtils.isAssignable(type.asType(), serializable)) {
      List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
      for (VariableElement field : fields) {
        if (field.getSimpleName().toString().equals("serialVersionUID")) {
          Object value = field.getConstantValue();
          if (field.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.FINAL))
              && field.asType().getKind() == TypeKind.LONG
              && value != null) {
            return value + "L";
          } else {
            reportError(
                "serialVersionUID must be a static final long compile-time constant", field);
            break;
          }
        }
      }
    }
    return "";
  }

  private TypeMirror getTypeMirror(Class<?> c) {
    return processingEnv.getElementUtils().getTypeElement(c.getName()).asType();
  }

  private enum ElementNameFunction implements Function<Element, String> {
    INSTANCE;
    @Override public String apply(Element element) {
      return element.getSimpleName().toString();
    }
  }

  // The actual type parameters of the generated type.
  // If we have @AutoValue abstract class Foo<T extends Something> then the subclass will be
  // final class AutoValue_Foo<T extends Something> extends Foo<T>.
  // <T extends Something> is the formal type parameter list and
  // <T> is the actual type parameter list, which is what this method returns.
  private static String actualTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      return "<"
          + Joiner.on(", ").join(
              FluentIterable.from(typeParameters).transform(ElementNameFunction.INSTANCE))
          + ">";
    }
  }

  // The @AutoValue type, with a ? for every type.
  // If we have @AutoValue abstract class Foo<T extends Something> then this method will return
  // just <?>.
  private static String wildcardTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      return "<"
          + Joiner.on(", ").join(
              FluentIterable.from(typeParameters).transform(Functions.constant("?")))
          + ">";
    }
  }

  private EclipseHack eclipseHack() {
    return new EclipseHack(processingEnv);
  }
}
