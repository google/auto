/*
 * Copyright (C) 2017 Google, Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

/**
 * Encodes types so they can later be decoded to incorporate imports.
 *
 * <p>The idea is that types that appear in generated source code use {@link #encode}, which will
 * spell out a type like {@code java.util.List<? extends java.lang.Number>}, except that wherever a
 * class name appears it is replaced by a special token. So the spelling might actually be
 * {@code `java.util.List`<? extends `java.lang.Number`>}. Then once the entire class has been
 * generated, {@code #decode} scans for these tokens to determine what classes need to be imported,
 * and replaces the tokens with the correct spelling given the imports. So here,
 * {@code java.util.List} would be imported, and the final spelling would be
 * {@code List<? extends Number>} (knowing that {@code Number} is implicitly imported). The special
 * token {@code `import`} marks where the imports should be, and {@link #decode} replaces it with
 * the correct list of imports.
 *
 * <p>The funky syntax for type annotations on qualified type names requires an adjustment to this
 * scheme. {@code `«java.util.Map`} stands for {@code java.util.}
 * and {@code `»java.util.Map`} stands for {@code Map}. If {@code java.util.Map} is imported, then
 * {@code `«java.util.Map`} will eventually be empty, but if {@code java.util.Map} is not imported
 * (perhaps because there is another {@code Map} in scope) then {@code `«java.util.Map`} will be
 * {@code java.util.}. The end result is that the code can contain
 * {@code `«java.util.Map`@`javax.annotation.Nullable` `»java.util.Map`}. That might decode to
 * {@code @Nullable Map} or to {@code java.util.@Nullable Map} or even to
 * {@code java.util.@javax.annotation.Nullable Map}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class TypeEncoder {
  private TypeEncoder() {}  // There are no instances of this class.

  private static final EncodingTypeVisitor ENCODING_TYPE_VISITOR =
      new EncodingTypeVisitor();
  private static final RawEncodingTypeVisitor RAW_ENCODING_TYPE_VISITOR =
      new RawEncodingTypeVisitor();
  private static final AnnotatedEncodingTypeVisitor ANNOTATED_ENCODING_TYPE_VISITOR =
      new AnnotatedEncodingTypeVisitor();

  /**
   * Returns the encoding for the given type, where class names are marked by special tokens.
   * The encoding for {@code int} will be {@code int}, but the encoding for
   * {@code java.util.List<java.lang.Integer>} will be
   * {@code `java.util.List`<`java.lang.Integer`>}.
   */
  static String encode(TypeMirror type) {
    StringBuilder sb = new StringBuilder();
    return type.accept(ENCODING_TYPE_VISITOR, sb).toString();
  }

  /**
   * Like {@link #encode}, except that only the raw type is encoded. So if the given type is
   * {@code java.util.List<java.lang.Integer>} the result will be {@code `java.util.List`}.
   */
  static String encodeRaw(TypeMirror type) {
    StringBuilder sb = new StringBuilder();
    return type.accept(RAW_ENCODING_TYPE_VISITOR, sb).toString();
  }

  /**
   * Encodes the given type and its type annotations. The class comment for {@link TypeEncoder}
   * covers the details of annotation encoding.
   */
  static String encodeWithAnnotations(TypeMirror type) {
    StringBuilder sb = new StringBuilder();
    return type.accept(ANNOTATED_ENCODING_TYPE_VISITOR, sb).toString();
  }

  /**
   * Decodes the given string, respelling class names appropriately. The text is scanned for
   * tokens like {@code `java.util.Locale`} or {@code `«java.util.Locale`} to determine which
   * classes are referenced. An appropriate set of imports is computed based on the set of those
   * types. If the special token {@code `import`} appears in {@code text} then it will be replaced
   * by this set of import statements. Then all of the tokens are replaced by the class names they
   * represent, spelled appropriately given the import statements.
   *
   * @param text the text to be decoded.
   * @param packageName the package of the generated class. Other classes in the same package
   *     do not need to be imported.
   * @param baseType a class or interface that the generated class inherits from. Nested classes
   *     in that type do not need to be imported, and if another class has the same name as one
   *     of those nested classes then it will need to be qualified.
   */
  static String decode(
      String text, ProcessingEnvironment processingEnv, String packageName, TypeMirror baseType) {
    return decode(text, processingEnv.getElementUtils(), processingEnv.getTypeUtils(),
        packageName, baseType);
  }

  static String decode(
      String text, Elements elementUtils, Types typeUtils, String pkg, TypeMirror baseType) {
    TypeRewriter typeRewriter = new TypeRewriter(text, elementUtils, typeUtils, pkg, baseType);
    return typeRewriter.rewrite();
  }

  private static String className(DeclaredType declaredType) {
    return MoreElements.asType(declaredType.asElement()).getQualifiedName().toString();
  }

  /**
   * Returns the formal type parameters of the given type.
   * If we have {@code @AutoValue abstract class Foo<T extends SomeClass>} then this method will
   * return an encoding of {@code <T extends SomeClass>} for {@code Foo}. Likewise it will return
   * an encoding of the angle-bracket part of:<br>
   * {@code Foo<SomeClass>}<br>
   * {@code Foo<T extends Number>}<br>
   * {@code Foo<E extends Enum<E>>}<br>
   * {@code Foo<K, V extends Comparable<? extends K>>}.
   *
   * <p>The encoding is simply that classes in the "extends" part are marked, so the examples
   * will actually look something like this:<br>
   * {@code <`bar.baz.SomeClass`>}<br>
   * {@code <T extends `java.lang.Number`>}<br>
   * {@code <E extends `java.lang.Enum`<E>>}<br>
   * {@code <K, V extends `java.lang.Comparable`<? extends K>>}.
   */
  static String formalTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      StringBuilder sb = new StringBuilder("<");
      String sep = "";
      for (TypeParameterElement typeParameter : typeParameters) {
        sb.append(sep);
        sep = ", ";
        appendTypeParameterWithBounds(sb, typeParameter);
      }
      return sb.append(">").toString();
    }
  }

  private static void appendTypeParameterWithBounds(
      StringBuilder sb, TypeParameterElement typeParameter) {
    sb.append(typeParameter.getSimpleName());
    String sep = " extends ";
    for (TypeMirror bound : typeParameter.getBounds()) {
      if (!bound.toString().equals("java.lang.Object")) {
        sb.append(sep);
        sep = " & ";
        sb.append(encode(bound));
      }
    }
  }

  /**
   * Converts a type into a string, using standard Java syntax, except that every class name
   * is wrapped in backquotes, like {@code `java.util.List`}.
   */
  private static class EncodingTypeVisitor
      extends SimpleTypeVisitor8<StringBuilder, StringBuilder> {
    @Override protected StringBuilder defaultAction(TypeMirror type, StringBuilder sb) {
      return sb.append(type);
    }

    @Override public StringBuilder visitArray(ArrayType type, StringBuilder sb) {
      return visit(type.getComponentType(), sb).append("[]");
    }

    @Override public StringBuilder visitDeclared(DeclaredType type, StringBuilder sb) {
      sb.append(declaredTypeName(type));
      appendTypeArguments(type, sb);
      return sb;
    }

    String declaredTypeName(DeclaredType type) {
      return "`" + className(type) + "`";
    }

    void appendTypeArguments(DeclaredType type, StringBuilder sb) {
      List<? extends TypeMirror> arguments = type.getTypeArguments();
      if (!arguments.isEmpty()) {
        sb.append("<");
        String sep = "";
        for (TypeMirror argument : arguments) {
          sb.append(sep);
          sep = ", ";
          visit(argument, sb);
        }
        sb.append(">");
      }
    }

    @Override public StringBuilder visitWildcard(WildcardType type, StringBuilder sb) {
      sb.append("?");
      TypeMirror extendsBound = type.getExtendsBound();
      TypeMirror superBound = type.getSuperBound();
      if (superBound != null) {
        sb.append(" super ");
        visit(superBound, sb);
      } else if (extendsBound != null) {
        sb.append(" extends ");
        visit(extendsBound, sb);
      }
      return sb;
    }

    @Override public StringBuilder visitError(ErrorType t, StringBuilder p) {
      throw new MissingTypeException();
    }
  }

  /**
   * Like {@link EncodingTypeVisitor} except that type parameters are omitted from the result.
   */
  private static class RawEncodingTypeVisitor extends EncodingTypeVisitor {
    @Override void appendTypeArguments(DeclaredType type, StringBuilder sb) {}
  }

  /**
   * Like {@link EncodingTypeVisitor} except that annotations on the visited type are also
   * included in the resultant string. Class names in those annotations are also encoded using
   * the {@code `java.util.List`} form.
   */
  private static class AnnotatedEncodingTypeVisitor extends EncodingTypeVisitor {
    @Override public StringBuilder visitPrimitive(PrimitiveType type, StringBuilder sb) {
      appendAnnotations(type.getAnnotationMirrors(), sb);
      // We can't just append type.toString(), because that will also have the annotation, but
      // without encoding.
      return sb.append(type.getKind().toString().toLowerCase());
    }

    @Override public StringBuilder visitTypeVariable(TypeVariable type, StringBuilder sb) {
      appendAnnotations(type.getAnnotationMirrors(), sb);
      return sb.append(type.asElement().getSimpleName());
    }

    /**
     * {@inheritDoc}
     * The result respects the Java syntax, whereby {@code Foo @Bar []} is an annotation on the
     * array type itself, while {@code @Bar Foo[]} would be an annotation on the component type.
     */
    @Override public StringBuilder visitArray(ArrayType type, StringBuilder sb) {
      visit(type.getComponentType(), sb);
      List<? extends AnnotationMirror> annotationMirrors = type.getAnnotationMirrors();
      if (!annotationMirrors.isEmpty()) {
        sb.append(" ");
        appendAnnotations(annotationMirrors, sb);
      }
      return sb.append("[]");
    }

    @Override public StringBuilder visitDeclared(DeclaredType type, StringBuilder sb) {
      List<? extends AnnotationMirror> annotationMirrors = type.getAnnotationMirrors();
      if (annotationMirrors.isEmpty()) {
        sb.append(declaredTypeName(type));
      } else {
        String className = className(type);
        // See the class doc comment for an explanation of « and » here.
        sb.append("`«").append(className).append("`");
        appendAnnotations(annotationMirrors, sb);
        sb.append("`»").append(className).append("`");
      }
      appendTypeArguments(type, sb);
      return sb;
    }

    private void appendAnnotations(
        List<? extends AnnotationMirror> annotationMirrors, StringBuilder sb) {
      for (AnnotationMirror annotationMirror : annotationMirrors) {
        sb.append(AnnotationOutput.sourceFormForAnnotation(annotationMirror)).append(" ");
      }
    }
  }

  private static class TypeRewriter {
    private final String text;
    private final int textLength;
    private final JavaScanner scanner;
    private final Elements elementUtils;
    private final Types typeUtils;
    private final String packageName;
    private final TypeMirror baseType;

    TypeRewriter(
        String text, Elements elementUtils, Types typeUtils, String pkg, TypeMirror baseType) {
      this.text = text;
      this.textLength = text.length();
      this.scanner = new JavaScanner(text);
      this.elementUtils = elementUtils;
      this.typeUtils = typeUtils;
      this.packageName = pkg;
      this.baseType = baseType;
    }

    String rewrite() {
      // Scan the text to determine what classes are referenced.
      Set<TypeMirror> referencedClasses = findReferencedClasses();
      // Make a type simplifier based on these referenced types.
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtils, packageName, referencedClasses, baseType);

      StringBuilder output = new StringBuilder();
      int copyStart;

      // Replace the `import` token with the import statements, if it is present.
      OptionalInt importMarker = findImportMarker();
      if (importMarker.isPresent()) {
        output.append(text, 0, importMarker.getAsInt());
        for (String toImport : typeSimplifier.typesToImport()) {
          output.append("import ").append(toImport).append(";\n");
        }
        copyStart = scanner.tokenEnd(importMarker.getAsInt());
      } else {
        copyStart = 0;
      }

      // Replace each of the classname tokens with the appropriate spelling of the classname.
      int token;
      for (token = copyStart; token < textLength; token = scanner.tokenEnd(token)) {
        if (text.charAt(token) == '`') {
          output.append(text, copyStart, token);
          decode(output, typeSimplifier, token);
          copyStart = scanner.tokenEnd(token);
        }
      }
      output.append(text, copyStart, textLength);
      return output.toString();
    }

    private Set<TypeMirror> findReferencedClasses() {
      Set<TypeMirror> classes = new TypeMirrorSet();
      for (int token = 0; token < textLength; token = scanner.tokenEnd(token)) {
        if (text.charAt(token) == '`' && !text.startsWith("`import`", token)) {
          String className = classNameAt(token);
          classes.add(classForName(className));
        }
      }
      return classes;
    }

    private DeclaredType classForName(String className) {
      TypeElement typeElement = elementUtils.getTypeElement(className);
      checkState(typeElement != null, "Could not find referenced class %s", className);
      return MoreTypes.asDeclared(typeElement.asType());
    }

    private void decode(StringBuilder output, TypeSimplifier typeSimplifier, int token) {
      String className = classNameAt(token);
      DeclaredType type = classForName(className);
      String simplified = typeSimplifier.simplifiedClassName(type);
      int dot;
      switch (text.charAt(token + 1)) {
        case '«':
          // If this is `«java.util.Map` then we want "java.util." here.
          // That's because this is the first part of something like "java.util.@Nullable Map"
          // or "java.util.Map.@Nullable Entry".
          // If there's no dot, then we want nothing here, for "@Nullable Map".
          dot = simplified.lastIndexOf('.');
          output.append(simplified.substring(0, dot + 1)); // correct even if dot == -1
          break;
        case '»':
          dot = simplified.lastIndexOf('.');
          output.append(simplified.substring(dot + 1)); // correct even if dot == -1
          break;
        default:
          output.append(simplified);
          break;
      }
    }

    private OptionalInt findImportMarker() {
      for (int token = 0; token < textLength; token = scanner.tokenEnd(token)) {
        if (text.startsWith("`import`", token)) {
          return OptionalInt.of(token);
        }
      }
      return OptionalInt.empty();
    }

    private String classNameAt(int token) {
      checkArgument(text.charAt(token) == '`');
      int end = scanner.tokenEnd(token) - 1; // points to the closing `
      int t = token + 1;
      char c = text.charAt(t);
      if (c == '«' || c == '»') {
        t++;
      }
      return text.substring(t, end);
    }
  }
}
