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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSortedSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

/**
 * Takes a set of types and a package and determines which of those types can be imported, and how
 * to spell any of the types in the set given those imports.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class TypeSimplifier {
  /**
   * The spelling that should be used to refer to a given class, and an indication of whether it
   * should be imported.
   */
  private static class Spelling {
    final String spelling;
    final boolean importIt;

    Spelling(String spelling, boolean importIt) {
      this.spelling = spelling;
      this.importIt = importIt;
    }
  }

  private final Types typeUtils;
  private final Map<String, Spelling> imports;

  /**
   * Makes a new simplifier for the given package and set of types.
   *
   * @param typeUtils the result of {@code ProcessingEnvironment.getTypeUtils()} for the current
   *     annotation processing environment.
   * @param packageName the name of the package from which classes will be referenced. Classes that
   *     are in the same package do not need to be imported.
   * @param types the types that will be referenced.
   * @param base a base class that the class containing the references will extend. This is needed
   *     because nested classes in that class or one of its ancestors are in scope in the generated
   *     subclass, so a reference to another class with the same name as one of them is ambiguous.
   *
   * @throws MissingTypeException if one of the input types contains an error (typically,
   *     is undefined). This may be something like {@code UndefinedClass}, or something more subtle
   *     like {@code Set<UndefinedClass<?>>}.
   */
  TypeSimplifier(Types typeUtils, String packageName, Set<TypeMirror> types, TypeMirror base) {
    this.typeUtils = typeUtils;
    Set<TypeMirror> typesPlusBase = new TypeMirrorSet(types);
    if (base != null) {
      typesPlusBase.add(base);
    }
    Set<TypeMirror> referenced = referencedClassTypes(typeUtils, typesPlusBase);
    Set<TypeMirror> defined = nonPrivateDeclaredTypes(typeUtils, base);
    this.imports = findImports(typeUtils, packageName, referenced, defined);
  }

  /**
   * Returns the set of types to import. We import every type that is neither in java.lang nor in
   * the package containing the AutoValue class, provided that the result refers to the type
   * unambiguously. For example, if there is a property of type java.util.Map.Entry then we will
   * import java.util.Map.Entry and refer to the property as Entry. We could also import just
   * java.util.Map in this case and refer to Map.Entry, but currently we never do that.
   */
  ImmutableSortedSet<String> typesToImport() {
    ImmutableSortedSet.Builder<String> typesToImport = ImmutableSortedSet.naturalOrder();
    for (Map.Entry<String, Spelling> entry : imports.entrySet()) {
      if (entry.getValue().importIt) {
        typesToImport.add(entry.getKey());
      }
    }
    return typesToImport.build();
  }

  /**
   * Returns a string that can be used to refer to the given type given the imports defined by
   * {@link #typesToImport}.
   */
  String simplify(TypeMirror type) {
    return type.accept(toStringTypeVisitor, new StringBuilder()).toString();
  }

  /**
   * Returns a string that can be used to refer to the given raw type given the imports defined by
   * {@link #typesToImport}. The difference between this and {@link #simplify} is that the string
   * returned here will not include type parameters.
   */
  String simplifyRaw(TypeMirror type) {
    return type.accept(toStringRawTypeVisitor, new StringBuilder()).toString();
  }

  /**
   * Returns a string that can be used to refer to the given type given the imports defined by
   * {@link #typesToImport}. The difference between this and {@link #simplify} is that the string
   * returned here includes any type annotations, with appropriate spelling given current imports.
   */
  String simplifyWithAnnotations(TypeMirror type) {
    return type.accept(toStringAnnotatedTypeVisitor, new StringBuilder()).toString();
  }

  // The formal type parameters of the given type.
  // If we have @AutoValue abstract class Foo<T extends SomeClass> then this method will
  // return <T extends Something> for Foo. Likewise it will return the angle-bracket part of:
  // Foo<SomeClass>
  // Foo<T extends Number>
  // Foo<E extends Enum<E>>
  // Foo<K, V extends Comparable<? extends K>>
  // Type variables need special treatment because we only want to include their bounds when they
  // are declared, not when they are referenced. We don't want to include the bounds of the second E
  // in <E extends Enum<E>> or of the second K in <K, V extends Comparable<? extends K>>. That's
  // why we put the "extends" handling here and not in ToStringTypeVisitor.
  String formalTypeParametersString(TypeElement type) {
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

  // The actual type parameters of the given type.
  // If we have @AutoValue abstract class Foo<T extends Something> then the subclass will be
  // final class AutoValue_Foo<T extends Something> extends Foo<T>.
  // <T extends Something> is the formal type parameter list and
  // <T> is the actual type parameter list, which is what this method returns.
  static String actualTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      return typeParameters.stream()
          .map(e -> e.getSimpleName().toString())
          .collect(joining(", ", "<", ">"));
    }
  }

  private void appendTypeParameterWithBounds(StringBuilder sb, TypeParameterElement typeParameter) {
    sb.append(typeParameter.getSimpleName());
    String sep = " extends ";
    for (TypeMirror bound : typeParameter.getBounds()) {
      if (!bound.toString().equals("java.lang.Object")) {
        sb.append(sep);
        sep = " & ";
        bound.accept(toStringTypeVisitor, sb);
      }
    }
  }

  private final ToStringTypeVisitor toStringTypeVisitor = new ToStringTypeVisitor();
  private final ToStringTypeVisitor toStringRawTypeVisitor = new ToStringRawTypeVisitor();
  private final ToStringTypeVisitor toStringAnnotatedTypeVisitor =
      new ToStringAnnotatedTypeVisitor();

  /**
   * Visitor that produces a string representation of a type for use in generated code.
   * The visitor takes into account the imports defined by {@link #typesToImport} and will use
   * the short names of those types.
   *
   * <p>A simpler alternative would be just to use TypeMirror.toString() and regular expressions to
   * pick apart the type references and replace fully-qualified types where possible. That depends
   * on unspecified behaviour of TypeMirror.toString(), though, and is vulnerable to formatting
   * quirks such as the way it omits the space after the comma in
   * {@code java.util.Map<java.lang.String, java.lang.String>}.
   */
  private class ToStringTypeVisitor extends SimpleTypeVisitor8<StringBuilder, StringBuilder> {
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
      TypeElement typeElement = (TypeElement) typeUtils.asElement(type);
      TypeElement top = topLevelType(typeElement);
      // We always want to write a class name starting from the outermost class. For example,
      // if the type is java.util.Map.Entry then we will import java.util.Map and write Map.Entry.
      String topString = top.getQualifiedName().toString();
      if (imports.containsKey(topString)) {
        String suffix = typeElement.getQualifiedName().toString().substring(topString.length());
        return imports.get(topString).spelling + suffix;
      } else {
        return typeElement.getQualifiedName().toString();
      }
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
  }

  private class ToStringRawTypeVisitor extends ToStringTypeVisitor {
    @Override void appendTypeArguments(DeclaredType type, StringBuilder sb) {}
  }

  private class ToStringAnnotatedTypeVisitor extends ToStringTypeVisitor {
    private final AnnotationOutput annotationOutput = new AnnotationOutput(TypeSimplifier.this);

    @Override public StringBuilder visitPrimitive(PrimitiveType type, StringBuilder sb) {
      appendAnnotations(type.getAnnotationMirrors(), sb);
      // We can't just append type.toString(), because that will also have the annotation, but
      // without using our imports.
      return sb.append(typeUtils.getPrimitiveType(type.getKind()));
    }

    @Override public StringBuilder visitTypeVariable(TypeVariable type, StringBuilder sb) {
      appendAnnotations(type.getAnnotationMirrors(), sb);
      return sb.append(type.asElement().getSimpleName());
    }

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
      String name = declaredTypeName(type);
      List<? extends AnnotationMirror> annotationMirrors = type.getAnnotationMirrors();
      if (annotationMirrors.isEmpty()) {
        sb.append(name);
      } else {
        // Find the index of the last part of the name, "Map" in "Map" or "Entry" in "Map.Entry".
        // lastIndexOf might return -1 in which case this is still correct.
        // The goal here is to produce "@Nullable Foo" for simple type names and
        // "com.example.@Nullable Foo" or "Bar.@Nullable Foo" for qualified ones.
        int lastPart = name.lastIndexOf('.') + 1;
        sb.append(name.substring(0, lastPart));
        appendAnnotations(annotationMirrors, sb);
        sb.append(name.substring(lastPart));
      }
      appendTypeArguments(type, sb);
      return sb;
    }

    private void appendAnnotations(
        List<? extends AnnotationMirror> annotationMirrors, StringBuilder sb) {
      for (AnnotationMirror annotationMirror : annotationMirrors) {
        sb.append(annotationOutput.sourceFormForAnnotation(annotationMirror)).append(" ");
      }
    }
  }

  /**
   * Returns the name of the given type, including any enclosing types but not the package.
   */
  static String classNameOf(TypeElement type) {
    String name = type.getQualifiedName().toString();
    String pkgName = packageNameOf(type);
    return pkgName.isEmpty() ? name : name.substring(pkgName.length() + 1);
  }

  private static TypeElement topLevelType(TypeElement type) {
    while (type.getNestingKind() != NestingKind.TOP_LEVEL) {
      type = MoreElements.asType(type.getEnclosingElement());
    }
    return type;
  }

  /**
   * Returns the name of the package that the given type is in. If the type is in the default
   * (unnamed) package then the name is the empty string.
   */
  static String packageNameOf(TypeElement type) {
    return MoreElements.getPackage(type).getQualifiedName().toString();
  }

  static String simpleNameOf(String s) {
    if (s.contains(".")) {
      return s.substring(s.lastIndexOf('.') + 1);
    } else {
      return s;
    }
  }

  /**
   * Given a set of referenced types, works out which of them should be imported and what the
   * resulting spelling of each one is.
   *
   * <p>This method operates on a {@code Set<TypeMirror>} rather than just a {@code Set<String>}
   * because it is not strictly possible to determine what part of a fully-qualified type name is
   * the package and what part is the top-level class. For example, {@code java.util.Map.Entry} is
   * a class called {@code Map.Entry} in a package called {@code java.util} assuming Java
   * conventions are being followed, but it could theoretically also be a class called {@code Entry}
   * in a package called {@code java.util.Map}. Since we are operating as part of the compiler, our
   * goal should be complete correctness, and the only way to achieve that is to operate on the real
   * representations of types.
   *
   * @param packageName The name of the package where the class containing these references is
   *     defined. Other classes within the same package do not need to be imported.
   * @param referenced The complete set of declared types (classes and interfaces) that will be
   *     referenced in the generated code.
   * @param defined The complete set of declared types (classes and interfaces) that are defined
   *     within the scope of the generated class (i.e. nested somewhere in its superclass chain,
   *     or in its interface set)
   * @return a map where the keys are fully-qualified types and the corresponding values indicate
   *     whether the type should be imported, and how the type should be spelled in the source code.
   */
  private static Map<String, Spelling> findImports(
      Types typeUtils, String packageName, Set<TypeMirror> referenced, Set<TypeMirror> defined) {
    Map<String, Spelling> imports = new HashMap<String, Spelling>();
    Set<TypeMirror> typesInScope = new TypeMirrorSet();
    typesInScope.addAll(referenced);
    typesInScope.addAll(defined);
    Set<String> ambiguous = ambiguousNames(typeUtils, typesInScope);
    for (TypeMirror type : referenced) {
      TypeElement typeElement = (TypeElement) typeUtils.asElement(type);
      String fullName = typeElement.getQualifiedName().toString();
      String simpleName = typeElement.getSimpleName().toString();
      String pkg = packageNameOf(typeElement);
      boolean importIt;
      String spelling;
      if (ambiguous.contains(simpleName)) {
        importIt = false;
        spelling = fullName;
      } else if (pkg.equals(packageName) || pkg.equals("java.lang")) {
        importIt = false;
        spelling = fullName.substring(pkg.isEmpty() ? 0 : pkg.length() + 1);
      } else {
        importIt = true;
        spelling = simpleName;
      }
      imports.put(fullName, new Spelling(spelling, importIt));
    }
    return imports;
  }

  /**
   * Finds all declared types (classes and interfaces) that are referenced in the given
   * {@code Set<TypeMirror>}. This includes classes and interfaces that appear directly in the set,
   * but also ones that appear in type parameters and the like. For example, if the set contains
   * {@code java.util.List<? extends java.lang.Number>} then both {@code java.util.List} and
   * {@code java.lang.Number} will be in the resulting set.
   *
   * <p>The returned set contains only top-level types. If we reference {@code java.util.Map.Entry}
   * then the returned set will contain {@code java.util.Map}. This is because we want to write
   * {@code Map.Entry} everywhere rather than {@code Entry}.
   */
  private static Set<TypeMirror> referencedClassTypes(Types typeUtil, Set<TypeMirror> types) {
    Set<TypeMirror> allReferenced = new TypeMirrorSet();
    ReferencedClassTypeVisitor referencedClassVisitor =
        new ReferencedClassTypeVisitor(typeUtil, allReferenced);
    for (TypeMirror type : types) {
      referencedClassVisitor.visit(type);
    }
    return allReferenced.stream()
        .map(typeMirror -> MoreElements.asType(typeUtil.asElement(typeMirror)))
        .map(typeElement -> topLevelType(typeElement).asType())
        .collect(toCollection(TypeMirrorSet::new));
  }

  private static class ReferencedClassTypeVisitor extends SimpleTypeVisitor8<Void, Void> {
    private final Types typeUtils;
    private final Set<TypeMirror> referencedTypes;
    private final Set<TypeMirror> seenTypes;

    ReferencedClassTypeVisitor(Types typeUtils, Set<TypeMirror> referenced) {
      this.typeUtils = typeUtils;
      this.referencedTypes = referenced;
      this.seenTypes = new TypeMirrorSet();
    }

    @Override public Void visitArray(ArrayType t, Void p) {
      return visit(t.getComponentType(), p);
    }

    @Override public Void visitDeclared(DeclaredType t, Void p) {
      if (seenTypes.add(t)) {
        referencedTypes.add(typeUtils.erasure(t));
        for (TypeMirror param : t.getTypeArguments()) {
          visit(param, p);
        }
      }
      return null;
    }

    @Override public Void visitTypeVariable(TypeVariable t, Void p) {
      // Instead of visiting t.getUpperBound(), we explicitly visit the supertypes of t.
      // The reason is that for a variable like <T extends Foo & Bar>, t.getUpperBound() will be
      // the intersection type Foo & Bar, with no really simple way to extract Foo and Bar. But
      // directSupertypes(t) will be exactly [Foo, Bar]. For plain <T>, directSupertypes(t) will
      // be java.lang.Object, and it is harmless for us to record a reference to that since we won't
      // try to import it or use it in the output string for <T>.
      for (TypeMirror upper : typeUtils.directSupertypes(t)) {
        visit(upper, p);
      }
      return visit(t.getLowerBound(), p);
    }

    @Override public Void visitWildcard(WildcardType t, Void p) {
      for (TypeMirror bound : new TypeMirror[] {t.getSuperBound(), t.getExtendsBound()}) {
        if (bound != null) {
          visit(bound, p);
        }
      }
      return null;
    }

    @Override public Void visitError(ErrorType t, Void p) {
      throw new MissingTypeException();
    }
  }

  /**
   * Finds all types that are declared with non private visibility by the given {@code TypeMirror},
   * any class in its superclass chain, or any interface it implements.
   */
  private static Set<TypeMirror> nonPrivateDeclaredTypes(Types typeUtils, TypeMirror type) {
    if (type == null) {
      return new TypeMirrorSet();
    } else {
      Set<TypeMirror> declared = new TypeMirrorSet();
      declared.add(type);
      List<TypeElement> nestedTypes =
          ElementFilter.typesIn(typeUtils.asElement(type).getEnclosedElements());
      for (TypeElement nestedType : nestedTypes) {
        if (!nestedType.getModifiers().contains(PRIVATE)) {
          declared.add(nestedType.asType());
        }
      }
      for (TypeMirror supertype : typeUtils.directSupertypes(type)) {
        declared.addAll(nonPrivateDeclaredTypes(typeUtils, supertype));
      }
      return declared;
    }
  }

  private static Set<String> ambiguousNames(Types typeUtils, Set<TypeMirror> types) {
    Set<String> ambiguous = new HashSet<String>();
    Map<String, Name> simpleNamesToQualifiedNames = new HashMap<String, Name>();
    for (TypeMirror type : types) {
      if (type.getKind() == TypeKind.ERROR) {
        throw new MissingTypeException();
      }
      String simpleName = typeUtils.asElement(type).getSimpleName().toString();
      /*
       * Compare by qualified names, because in Eclipse JDT, if Java 8 type annotations are used,
       * the same (unannotated) type may appear multiple times in the Set<TypeMirror>.
       * TODO(emcmanus): investigate further, because this might cause problems elsewhere.
       */
      Name qualifiedName = ((TypeElement)typeUtils.asElement(type)).getQualifiedName();
      Name previous = simpleNamesToQualifiedNames.put(simpleName, qualifiedName);
      if (previous != null && !previous.equals(qualifiedName)) {
        ambiguous.add(simpleName);
      }
    }
    return ambiguous;
  }
  
  /**
   * Returns true if casting to the given type will elicit an unchecked warning from the
   * compiler. Only generic types such as {@code List<String>} produce such warnings. There will be
   * no warning if the type's only generic parameters are simple wildcards, as in {@code Map<?, ?>}.
   */
  static boolean isCastingUnchecked(TypeMirror type) {
    return new CastingUncheckedVisitor().visit(type, false);
  }

  /**
   * Visitor that tells whether a type is erased, in the sense of {@link #isCastingUnchecked}. Each
   * visitX method returns true if its input parameter is true or if the type being visited is
   * erased.
   */
  private static class CastingUncheckedVisitor extends SimpleTypeVisitor8<Boolean, Boolean> {
    @Override protected Boolean defaultAction(TypeMirror e, Boolean p) {
      return p;
    }

    @Override public Boolean visitUnknown(TypeMirror t, Boolean p) {
      // We don't know whether casting is unchecked for this mysterious type but assume it is,
      // so we will insert a possible-unnecessary @SuppressWarnings("unchecked").
      return true;
    }

    @Override public Boolean visitArray(ArrayType t, Boolean p) {
      return visit(t.getComponentType(), p);
    }

    @Override public Boolean visitDeclared(DeclaredType t, Boolean p) {
      return p || t.getTypeArguments().stream().anyMatch(this::uncheckedTypeArgument);
    }

    @Override public Boolean visitTypeVariable(TypeVariable t, Boolean p) {
      return true;
    }

    // If a type has a type argument, then casting to the type is unchecked, except if the argument
    // is <?> or <? extends Object>. The same applies to all type arguments, so casting to Map<?, ?>
    // does not produce an unchecked warning for example.
    private boolean uncheckedTypeArgument(TypeMirror arg) {
      if (arg.getKind() == TypeKind.WILDCARD) {
        WildcardType wildcard = (WildcardType) arg;
        if (wildcard.getExtendsBound() == null || isJavaLangObject(wildcard.getExtendsBound())) {
          // This is <?>, unless there's a super bound, in which case it is <? super Foo> and
          // is erased.
          return (wildcard.getSuperBound() != null);
        }
      }
      return true;
    };

    private static boolean isJavaLangObject(TypeMirror type) {
      if (type.getKind() != TypeKind.DECLARED) {
        return false;
      }
      DeclaredType declaredType = (DeclaredType) type;
      TypeElement typeElement = (TypeElement) declaredType.asElement();
      return typeElement.getQualifiedName().contentEquals("java.lang.Object");
    }
  };
}
