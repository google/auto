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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see AutoValue
 * @author Éamonn McManus
 */
// I have avoided using any classes from Guava here for fear of future circularity problems.
@SupportedOptions(EclipseHack.ENABLING_OPTION)
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

  private void error(String msg, Element e) {
    // Issue a compilation error. This method does not throw an exception, since we want to
    // continue processing and perhaps report other errors. It is a good idea to introduce a
    // test case in CompilationErrorsTest for any new call to error(...) to ensure that we continue
    // correctly after an error.
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
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
      processType(type);
    }
  }

  private String generatedClassName(TypeElement type, String prefix) {
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = (TypeElement) type.getEnclosingElement();
      name = type.getSimpleName() + "_" + name;
    }
    String pkg = packageNameOf(type);
    String dot = pkg.isEmpty() ? "" : ".";
    return pkg + dot + prefix + name;
  }

  private String generatedSubclassName(TypeElement type) {
    return generatedClassName(type, "AutoValue_");
  }

  private String generatedFactoryName(TypeElement type) {
    return generatedClassName(type, "AutoValueFactory_");
  }

  private static String simpleNameOf(String s) {
    if (s.contains(".")) {
      return s.substring(s.lastIndexOf('.') + 1);
    } else {
      return s;
    }
  }

  static String packageNameOf(TypeElement type) {
    while (true) {
      Element enclosing = type.getEnclosingElement();
      if (enclosing instanceof PackageElement) {
        return ((PackageElement) enclosing).getQualifiedName().toString();
      }
      type = (TypeElement) enclosing;
    }
  }

  // Return the name of the class, including any enclosing classes but not the package.
  private static String classNameOf(TypeElement type) {
    String name = type.getQualifiedName().toString();
    String pkgName = packageNameOf(type);
    assert name.startsWith(pkgName) && name.charAt(pkgName.length()) == '.';
    return name.substring(pkgName.length() + 1);
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
    // Package declaration
    "$[pkg?package $[pkg];\n]",

    // Class declaration
    "final class $[subclass]$[formaltypes] extends $[origclass]$[actualtypes] {",

    // Fields
    "$[props:p||  private final $[p.type] $[p];\n]",

    // Constructor
    // CHECKSTYLE:OFF:OperatorWrap
    "  $[subclass](\n      $[props:p|,\n      |$[p.type] $[p]]) {",
    "$[props:p|\n|$[p.primitive!$[p.nullable!    if ($[p] == null) {",
    "      throw new NullPointerException(\"Null $[p]\");",
    "    }",
    "]]" +
    "    this.$[p] = $[p];]",
    "  }",
    // CHECKSTYLE:ON

    // Property getters
    "$[props:p|\n|\n  @Override",
    "  $[p.access]$[p.type] $[p]() {",
    "    return this.$[p];",
    "  }]",

    // toString()
    // CHECKSTYLE:OFF:OperatorWrap
    "$[genToString?\n  @Override",
    "  public String toString() {",
    "    return \"$[simpleclassname]{\"$[props?\n      + \"]" +
    "$[props:p|\n      + \", |$[p]=\" + $[p]]",
    "      + \"}\";",
    "  }]",
    // CHECKSTYLE:ON

    // equals(Object)
    // TODO(emcmanus): this probably generates a rawtypes warning if there are type parameters.
    // CHECKSTYLE:OFF:OperatorWrap
    "$[genEquals?\n  @Override",
    "  public boolean equals(Object o) {",
    "    if (o == this) {",
    "      return true;",
    "    }",
    "    if (o instanceof $[origclass]) {",
    "      $[origclass]$[wildcardtypes] that = ($[origclass]$[wildcardtypes]) o;",
    "      return $[props!true]$[props:p|\n          && |" +
                "($[p.primitive?" +
                  "[this.$[p] == that.$[p]()]" +
                  "[$[p.nullable?" +
                    "(this.$[p] == null) ? (that.$[p]() == null) : ]" +
                    "this.$[p].equals(that.$[p]())]])];",
                                              // Eat your heart out, Lisp
    "    }",
    "    return false;",
    "  }]",
    // CHECKSTYLE:ON

    // hashCode()
    // CHECKSTYLE:OFF:OperatorWrap
    "$[genHashCode?",
    "  private transient int hashCode;",
    "",
    "  @Override",
    "  public int hashCode() {",
    "    int h = hashCode;",
    "    if (h == 0) {",
    "      h = 1;",
    "$[props:p||" +
    "      h *= 1000003;",
    "$[p.hashCodeStatements:statement||" +
    "      $[statement]",
    "]]" +
    "      hashCode = h;",
    "    }",
    "    return h;",
    "  }]",
    "}"
    // CHECKSTYLE:ON
  );
  private static final Template template = Template.compile(TEMPLATE_STRING);

  private static final String FACTORY_TEMPLATE_STRING = concatLines(
      // CHECKSTYLE:OFF:OperatorWrap
      "$[pkg?package $[pkg];\n]",
      "public final class $[factoryimpl] implements $[factory] {",
      "  @Override",
      "  public $[formaltypes] $[origclass]$[actualtypes] $[create]($[props:p|, " +
            "|$[p.type] $[p]]) {",
      "    return new $[subclass]$[actualtypes]($[props:p|, |$[p]]);",
      "  }",
      "}"
      // CHECKSTYLE:ON
  );
  private static final Template factoryTemplate = Template.compile(FACTORY_TEMPLATE_STRING);

  static class Property {
    private final ExecutableElement method;

    Property(ExecutableElement method) {
      this.method = method;
    }

    @Override
    public String toString() {
      return method.getSimpleName().toString();
    }

    TypeElement owner() {
      return (TypeElement) method.getEnclosingElement();
    }

    public TypeMirror type() {
      return method.getReturnType();
    }

    public boolean primitive() {
      return method.getReturnType().getKind().isPrimitive();
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

  private boolean isJavaLangObject(TypeElement type) {
    return type.getSuperclass().getKind() == TypeKind.NONE && type.getKind() == ElementKind.CLASS;
  }

  private void localAndInheritedMethods(TypeElement type, List<ExecutableElement> methods) {
    note("Looking at methods in " + type);
    Types typeUtils = processingEnv.getTypeUtils();
    Elements elementUtils = processingEnv.getElementUtils();
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      localAndInheritedMethods((TypeElement) typeUtils.asElement(type.getSuperclass()), methods);
    }
    for (TypeMirror superInterface : type.getInterfaces()) {
      localAndInheritedMethods((TypeElement) typeUtils.asElement(superInterface), methods);
    }
    // Add each method of this class, and in so doing remove any inherited method it overrides.
    // This algorithm is quadratic in the number of methods but it's hard to see how to improve
    // that while still using Elements.overrides.
    List<ExecutableElement> theseMethods = ElementFilter.methodsIn(type.getEnclosedElements());
    eclipseHack().sortMethodsIfSimulatingEclipse(theseMethods);
    for (ExecutableElement method : theseMethods) {
      if (!method.getModifiers().contains(Modifier.PRIVATE)) {
        boolean overridden = false;
        for (Iterator<ExecutableElement> methodIter = methods.iterator(); methodIter.hasNext();) {
          ExecutableElement otherMethod = methodIter.next();
          if (elementUtils.overrides(method, otherMethod, type)) {
            methodIter.remove();
          } else if (elementUtils.overrides(otherMethod, method, type)) {
            // If we inherit this method on more than one path, we may have already seen the
            // overriding method.
            overridden = true;
          }
        }
        if (!overridden) {
          methods.add(method);
        }
      }
    }
  }

  private void processType(TypeElement type) {
    if (type.getKind() != ElementKind.CLASS) {
      error("@" + AutoValue.class.getName() + " only applies to classes", type);
    }
    Map<String, Object> vars = new TreeMap<String, Object>();
    vars.put("pkg", packageNameOf(type));
    vars.put("origclass", classNameOf(type));
    vars.put("simpleclassname", simpleNameOf(classNameOf(type)));
    vars.put("formaltypes", formalTypeString(type));
    vars.put("actualtypes", actualTypeString(type));
    vars.put("wildcardtypes", wildcardTypeString(type));
    vars.put("subclass", simpleNameOf(generatedSubclassName(type)));
    defineVarsForType(type, vars);
    String text = template.rewrite(vars);
    writeSourceFile(generatedSubclassName(type), text, type);

    TypeElement factoryType = factoryInterfaceFor(type);
    if (factoryType != null && checkFactory(factoryType, vars)) {
      defineVarsForFactory(factoryType, vars);
      String factoryName = generatedFactoryName(type);
      vars.put("factoryimpl", simpleNameOf(factoryName));
      String factoryText = factoryTemplate.rewrite(vars);
      writeSourceFile(factoryName, factoryText, type);
    }
  }

  private void defineVarsForType(TypeElement type, Map<String, Object> vars) {
    List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    localAndInheritedMethods(type, methods);
    List<Property> props = new ArrayList<Property>();
    boolean genToString = true;
    boolean genEquals = true;
    boolean genHashCode = true;
    for (ExecutableElement method : methods) {
      String name = method.getSimpleName().toString();
      boolean isAbstract = method.getModifiers().contains(Modifier.ABSTRACT);
      boolean canGenerate =
          isAbstract || isJavaLangObject((TypeElement) method.getEnclosingElement());
      if (name.equals("toString") && method.getParameters().isEmpty()) {
        genToString = canGenerate;
      } else if (name.equals("hashCode") && method.getParameters().isEmpty()) {
        genHashCode = canGenerate;
      } else if (name.equals("equals") && method.getParameters().size() == 1
          && method.getParameters().get(0).asType().toString().equals("java.lang.Object")) {
        genEquals = canGenerate;
      } else if (isAbstract) {
        if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID) {
          if (method.getReturnType().getKind() == TypeKind.ARRAY) {
            error("Array-valued properties are not supported in @AutoValue classes: " + name,
                method);
          }
          // The preceding ifs mean we won't think that an abstract toString() or hashCode() is a
          // property getter.
          props.add(new Property(method));
        } else {
          error("@AutoValue classes cannot have abstract methods other than property getters",
              method);
        }
      }
    }
    // If we are running from Eclipse, undo the work of its compiler which sorts methods.
    eclipseHack().reorderProperties(props);
    vars.put("props", props);
    vars.put("genToString", genToString);
    vars.put("genEquals", genEquals);
    vars.put("genHashCode", genHashCode);
  }

  private TypeElement factoryInterfaceFor(TypeElement type) {
    List<TypeElement> classes = ElementFilter.typesIn(type.getEnclosedElements());
    for (TypeElement nested : classes) {
      if (nested.getSimpleName().toString().equals("Factory")) {
        return nested;
      }
    }
    return null;
  }

  private boolean checkFactory(TypeElement factoryType, Map<String, Object> vars) {
    List<ExecutableElement> factoryMethods =
        ElementFilter.methodsIn(factoryType.getEnclosedElements());
    if (factoryMethods.size() != 1) {
      error("Factory interface must have exactly one method", factoryType);
      return false;
    }
    ExecutableElement factoryMethod = factoryMethods.get(0);
    List<?> props = (List<?>) vars.get("props");
    List<? extends VariableElement> factoryParams = factoryMethod.getParameters();
    if (factoryMethod.getParameters().size() != props.size()) {
      error("Factory method " + factoryMethod.getSimpleName() + " parameters "
          + factoryMethod.getParameters() + " do not match props " + props, factoryMethod);
      return false;
    }
    boolean match = true;
    for (int i = 0; i < props.size(); i++) {
      if (!props.get(i).toString().equals(factoryParams.get(i).getSimpleName().toString())) {
        // We don't check that the types match because that turns out not to be trivial if
        // generic type variables are involved, and anyway the compiler will complain later.
        error("Parameter " + (i + 1) + " of factory method " + factoryMethod.getSimpleName()
            + " does not match the corresponding property getter " + props.get(i), factoryMethod);
        match = false;
      }
    }
    return match;
  }

  private void defineVarsForFactory(TypeElement factoryType, Map<String, Object> vars) {
    List<ExecutableElement> factoryMethods =
        ElementFilter.methodsIn(factoryType.getEnclosedElements());
    ExecutableElement factoryMethod = factoryMethods.get(0);
    vars.put("factory", factoryType.getQualifiedName());
    vars.put("create", factoryMethod.getSimpleName());
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
      for (TypeParameterElement typeParameter : typeParameters) {
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