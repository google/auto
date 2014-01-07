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

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
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
@SupportedOptions(EclipseHack.ENABLING_OPTION)
public class AutoValueProcessor extends AbstractProcessor {
  private static final boolean SILENT = true;

  public AutoValueProcessor() {}

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(AutoValue.class.getName());
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

  @SuppressWarnings("serial")
  private static class CompileException extends Exception {}

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
  private void abortWithError(String msg, Element e) throws CompileException {
    reportError(msg, e);
    throw new CompileException();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean claimed = (annotations.size() == 1
        && annotations.iterator().next().getQualifiedName().toString().equals(
            AutoValue.class.getName()));
    if (claimed) {
      process(roundEnv);
      return true;
    } else {
      return false;
    }
  }

  private void process(RoundEnvironment roundEnv) {
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(AutoValue.class);
    Collection<? extends TypeElement> types = ElementFilter.typesIn(annotatedElements);
    for (TypeElement type : types) {
      try {
        processType(type);
      } catch (CompileException e) {
        // We abandoned this type, but continue with the next.
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

  private static String simpleNameOf(String s) {
    if (s.contains(".")) {
      return s.substring(s.lastIndexOf('.') + 1);
    } else {
      return s;
    }
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

  // This is just because I hate typing    "...\n" +   all the time.
  private static String concatLines(String... lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append("\n");
    }
    return sb.toString();
  }

  // The code below uses a small templating language. This is not hugely readable, but is much more
  // so than sb.append(this).append(that) with ifs and fors scattered around everywhere.
  // See the Template class for an explanation of the various constructs.
  private static final String TEMPLATE_STRING = concatLines(
      // CHECKSTYLE:OFF:OperatorWrap
      // Package declaration
      "$[pkg?package $[pkg];\n]",

      // Imports
      "$[imports:i||import $[i];\n]",

      // @Generated annotation
      "@javax.annotation.Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",

      // Class declaration
      "final class $[subclass]$[formaltypes] extends $[origclass]$[actualtypes] {",

      // Fields
      "$[props:p||  private final $[p.type] $[p];\n]",

      // Constructor
      "  $[subclass](\n      $[props:p|,\n      |$[p.type] $[p]]) {",
      "$[props:p|\n|$[p.primitive!$[p.nullable!    if ($[p] == null) {",
      "      throw new NullPointerException(\"Null $[p]\");",
      "    }",
      "]]" +
      "    this.$[p] = $[p];]",
      "  }",

      // Property getters
      "$[props:p|\n|\n  @Override",
      "  $[p.access]$[p.type] $[p]() {",
      "    return $[p.array?[$[p.nullable?$[p] == null ? null : ]$[p].clone()][$[p]]];",
      "  }]",

      // toString()
      "$[toString?\n  @Override",
      "  public String toString() {",
      "    return \"$[simpleclassname]{\"$[props?\n        + \"]" +
      "$[props:p|\n        + \", |" +
                "$[p]=\" + $[p.array?[$[Arrays].toString($[p])][$[p]]]]",
      "        + \"}\";",
      "  }]",

      // equals(Object)
      "$[equals?\n  @Override",
      "  public boolean equals(Object o) {",
      "    if (o == this) {",
      "      return true;",
      "    }",
      "    if (o instanceof $[origclass]) {",
      "      $[origclass]$[wildcardtypes] that = ($[origclass]$[wildcardtypes]) o;",
      "      return $[props!true]$[props:p|\n          && |" +
                  "($[p.primitive?" +
                    "[this.$[p] == that.$[p]()]" +
                    "[$[p.array?" +
                     "[$[Arrays].equals(" +
                           "this.$[p],",
         "                  (that instanceof $[subclass]) ",
         "                      ? (($[subclass]) that).$[p] : that.$[p]())]" +
                     "[$[p.nullable?" +
                       "(this.$[p] == null) ? (that.$[p]() == null) : ]" +
                       "this.$[p].equals(that.$[p]())]]]])];",
                                                // Eat your heart out, Lisp
      "    }",
      "    return false;",
      "  }]",

      // hashCode()
      "$[hashCode?",
      "$[cacheHashCode?  private transient int hashCode;\n\n]" +

      "  @Override",
      "  public int hashCode() {",
      "$[cacheHashCode?    if (hashCode != 0) {",
      "      return hashCode;",
      "    }\n]" +
      "    int h = 1;",
      "$[props:p||" +
      "    h *= 1000003;",
      "$[p.array?" +
       "[    h ^= $[Arrays].hashCode($[p]);\n]" +
       "[$[p.hashCodeStatements:statement||" +
        "    $[statement]",
      "]]]]" +
      "$[cacheHashCode?    hashCode = h;\n]" +
      "    return h;",
      "  }]" +

      // serialVersionUID
      "$[serialVersionUID?\n\n  private static final long serialVersionUID = $[serialVersionUID];]",

      "}"
      // CHECKSTYLE:ON
  );
  private static final Template template = Template.compile(TEMPLATE_STRING);

  static class Property {
    private final ExecutableElement method;
    private final String type;

    Property(ExecutableElement method, String type) {
      this.method = method;
      this.type = type;
    }

    @Override
    public String toString() {
      return method.getSimpleName().toString();
    }

    TypeElement owner() {
      return (TypeElement) method.getEnclosingElement();
    }

    public String type() {
      return type;
    }

    public boolean primitive() {
      return method.getReturnType().getKind().isPrimitive();
    }

    public boolean array() {
      return method.getReturnType().getKind() == TypeKind.ARRAY;
    }

    public boolean nullable() {
      return method.getAnnotation(Nullable.class) != null;
    }

    /**
     * One or more statements that, taken together, xor the hashCode of this
     * property into the variable h.
     */
    public List<String> hashCodeStatements() {
      switch (method.getReturnType().getKind()) {
        case BYTE:
        case SHORT:
        case CHAR:
        case INT:
          return Arrays.asList("h ^= " + this + ";");
        case LONG:
          return Arrays.asList("h ^= (" + this + ">>> 32) ^ " + this + ";");
        case FLOAT:
          return Arrays.asList("h ^= Float.floatToIntBits(" + this + ");");
        case DOUBLE:
          return Arrays.asList(
              "{",
              "  long bits = Double.doubleToLongBits(" + this + ");",
              "  h ^= (bits >>> 32) ^ bits;",
              "}");
        case BOOLEAN:
          return Arrays.asList("h ^= " + this + " ? 1231 : 1237;");
        default:
          if (nullable()) {
            return Arrays.asList(
                "if (" + this + " != null) {",
                "  h ^= " + this + ".hashCode();",
                "}");
          } else {
            return Arrays.asList("h ^= " + this + ".hashCode();");
          }
      }
    }

    public String access() {
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

  private static boolean isToStringOrEqualsOrHashCode(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    return ((name.equals("toString") || name.equals("hashCode"))
              && method.getParameters().isEmpty())
        || (name.equals("equals") && method.getParameters().size() == 1
              && method.getParameters().get(0).asType().toString().equals("java.lang.Object"));
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
    eclipseHack().sortMethodsIfSimulatingEclipse(theseMethods);
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

  private void processType(TypeElement type) throws CompileException {
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
    Map<String, Object> vars = new TreeMap<String, Object>();
    vars.put("pkg", TypeSimplifier.packageNameOf(type));
    vars.put("origclass", classNameOf(type));
    vars.put("simpleclassname", simpleNameOf(classNameOf(type)));
    vars.put("formaltypes", formalTypeString(type));
    vars.put("actualtypes", actualTypeString(type));
    vars.put("wildcardtypes", wildcardTypeString(type));
    vars.put("subclass", simpleNameOf(generatedSubclassName(type)));
    vars.put("cacheHashCode", autoValue.cacheHashCode());
    defineVarsForType(type, vars);
    String text = template.rewrite(vars);
    writeSourceFile(generatedSubclassName(type), text, type);
  }

  private void defineVarsForType(TypeElement type, Map<String, Object> vars)
      throws CompileException {
    List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    findLocalAndInheritedMethods(type, methods);
    vars.putAll(objectMethodsToGenerate(methods));
    List<ExecutableElement> toImplement = methodsToImplement(methods);
    Set<TypeMirror> types = new HashSet<TypeMirror>();
    types.addAll(returnTypesOf(toImplement));
    TypeMirror javaUtilArrays =
        processingEnv.getElementUtils().getTypeElement(Arrays.class.getName()).asType();
    if (containsArrayType(types)) {
      // If there are array properties then we will be referencing java.util.Arrays.
      // Arrange to import it unless that would introduce ambiguity.
      types.add(javaUtilArrays);
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    TypeSimplifier typeSimplifier = new TypeSimplifier(processingEnv.getTypeUtils(), pkg, types);
    vars.put("imports", typeSimplifier.typesToImport());
    List<Property> props = new ArrayList<Property>();
    for (ExecutableElement method : toImplement) {
      props.add(new Property(method, typeSimplifier.simplify(method.getReturnType())));
    }
    vars.put("Arrays", typeSimplifier.simplify(javaUtilArrays));
    // If we are running from Eclipse, undo the work of its compiler which sorts methods.
    eclipseHack().reorderProperties(props);
    vars.put("props", props);
    vars.put("serialVersionUID", getSerialVersionUID(type));
  }

  private Set<TypeMirror> returnTypesOf(List<ExecutableElement> methods) {
    HashSet<TypeMirror> returnTypes = new HashSet<TypeMirror>();
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
   * Given a list of all methods defined in or inherited by a class, returns a map with keys
   * "toString", "equals", "hashCode" and corresponding value true if that method should be
   * generated.
   */
  private static Map<String, Boolean> objectMethodsToGenerate(List<ExecutableElement> methods) {
    Map<String, Boolean> vars = new TreeMap<String, Boolean>();
    // The defaults here only come into play when an ancestor class doesn't exist.
    // Compilation will fail in that case, but we don't want it to crash the compiler with
    // an exception before it does. If all ancestors do exist then we will definitely find
    // definitions of these three methods (perhaps the ones in Object) so we will overwrite these:
    vars.put("equals", false);
    vars.put("hashCode", false);
    vars.put("toString", false);
    for (ExecutableElement method : methods) {
      if (isToStringOrEqualsOrHashCode(method)) {
        boolean canGenerate = method.getModifiers().contains(Modifier.ABSTRACT)
            || isJavaLangObject((TypeElement) method.getEnclosingElement());
        vars.put(method.getSimpleName().toString(), canGenerate);
      }
    }
    assert vars.size() == 3;
    return vars;
  }

  private List<ExecutableElement> methodsToImplement(List<ExecutableElement> methods)
      throws CompileException {
    List<ExecutableElement> toImplement = new ArrayList<ExecutableElement>();
    boolean errors = false;
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)
          && !isToStringOrEqualsOrHashCode(method)) {
        if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID) {
          if (isReferenceArrayType(method.getReturnType())) {
            reportError("An @AutoValue class cannot define an array-valued property unless it is "
                + "a primitive array", method);
            errors = true;
          }
          toImplement.add(method);
        } else {
          reportError("@AutoValue classes cannot have abstract methods other than property getters",
              method);
          errors = true;
        }
      }
    }
    if (errors) {
      throw new CompileException();
    }
    return toImplement;
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

  // Return a string like "1234L" if type instanceof Serializable and defines
  // serialVersionUID = 1234L, otherwise "".
  private String getSerialVersionUID(TypeElement type) {
    Types typeUtils = processingEnv.getTypeUtils();
    Elements elementUtils = processingEnv.getElementUtils();
    TypeMirror serializable = elementUtils.getTypeElement(Serializable.class.getName()).asType();
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

  // Why does TypeParameterElement.toString() not return this? Grrr.
  private static String typeParameterString(TypeParameterElement type) {
    String s = type.getSimpleName().toString();
    List<? extends TypeMirror> bounds = type.getBounds();
    if (bounds.isEmpty()) {
      return s;
    } else {
      s += " extends ";
      String sep = "";
      for (TypeMirror bound : bounds) {
        s += sep + bound;
        sep = " & ";
      }
      return s;
    }
  }

  private static String formalTypeString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "<";
      String sep = "";
      for (TypeParameterElement typeParameter : typeParameters) {
        s += sep + typeParameterString(typeParameter);
        sep = ", ";
      }
      return s + ">";
    }
  }

  private static String actualTypeString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "<";
      String sep = "";
      for (TypeParameterElement typeParameter : typeParameters) {
        s += sep + typeParameter.getSimpleName();
        sep = ", ";
      }
      return s + ">";
    }
  }

  // The @AutoValue type, with a ? for every type.
  private static String wildcardTypeString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "<";
      String sep = "";
      for (TypeParameterElement unused : typeParameters) {
        s += sep + "?";
        sep = ", ";
      }
      return s + ">";
    }
  }

  private EclipseHack eclipseHack() {
    return new EclipseHack(processingEnv);
  }
}
