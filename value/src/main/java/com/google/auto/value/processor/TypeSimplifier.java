/*
 * Copyright 2012 Google LLC
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
import com.google.auto.common.MoreTypes;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.collect.ImmutableSortedSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
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

  private final Map<String, Spelling> imports;

  /**
   * Makes a new simplifier for the given package and set of types.
   *
   * @param elementUtils the result of {@code ProcessingEnvironment.getElementUtils()} for the
   *     current annotation processing environment.
   * @param typeUtils the result of {@code ProcessingEnvironment.getTypeUtils()} for the current
   *     annotation processing environment.
   * @param packageName the name of the package from which classes will be referenced. Classes that
   *     are in the same package do not need to be imported.
   * @param types the types that will be referenced.
   * @param base a base class that the class containing the references will extend. This is needed
   *     because nested classes in that class or one of its ancestors are in scope in the generated
   *     subclass, so a reference to another class with the same name as one of them is ambiguous.
   * @throws MissingTypeException if one of the input types contains an error (typically, is
   *     undefined).
   */
  TypeSimplifier(
      Elements elementUtils,
      Types typeUtils,
      String packageName,
      Set<TypeMirror> types,
      TypeMirror base) {
    Set<TypeMirror> typesPlusBase = new TypeMirrorSet(types);
    if (base != null) {
      typesPlusBase.add(base);
    }
    Set<TypeMirror> topLevelTypes = topLevelTypes(typeUtils, typesPlusBase);
    Set<TypeMirror> defined = nonPrivateDeclaredTypes(typeUtils, base);
    this.imports = findImports(elementUtils, typeUtils, packageName, topLevelTypes, defined);
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

  String simplifiedClassName(DeclaredType type) {
    TypeElement typeElement = MoreElements.asType(type.asElement());
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

  /** Returns the name of the given type, including any enclosing types but not the package. */
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
   * the package and what part is the top-level class. For example, {@code java.util.Map.Entry} is a
   * class called {@code Map.Entry} in a package called {@code java.util} assuming Java conventions
   * are being followed, but it could theoretically also be a class called {@code Entry} in a
   * package called {@code java.util.Map}. Since we are operating as part of the compiler, our goal
   * should be complete correctness, and the only way to achieve that is to operate on the real
   * representations of types.
   *
   * @param codePackageName The name of the package where the class containing these references is
   *     defined. Other classes within the same package do not need to be imported.
   * @param referenced The complete set of declared types (classes and interfaces) that will be
   *     referenced in the generated code.
   * @param defined The complete set of declared types (classes and interfaces) that are defined
   *     within the scope of the generated class (i.e. nested somewhere in its superclass chain, or
   *     in its interface set)
   * @return a map where the keys are fully-qualified types and the corresponding values indicate
   *     whether the type should be imported, and how the type should be spelled in the source code.
   */
  private static Map<String, Spelling> findImports(
      Elements elementUtils,
      Types typeUtils,
      String codePackageName,
      Set<TypeMirror> referenced,
      Set<TypeMirror> defined) {
    Map<String, Spelling> imports = new HashMap<>();
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
      } else if (pkg.equals("java.lang")) {
        importIt = false;
        spelling = javaLangSpelling(elementUtils, codePackageName, typeElement);
      } else if (pkg.equals(codePackageName)) {
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
   * Handles the tricky case where the class being referred to is in {@code java.lang}, but the
   * package of the referring code contains another class of the same name. For example, if the
   * current package is {@code foo.bar} and there is a {@code foo.bar.Compiler}, then we will refer
   * to {@code java.lang.Compiler} by its full name. The plain name {@code Compiler} would reference
   * {@code foo.bar.Compiler} in this case. We need to write {@code java.lang.Compiler} even if the
   * other {@code Compiler} class is not being considered here, so the {@link #ambiguousNames} logic
   * is not enough. We have to look to see if the class exists.
   */
  private static String javaLangSpelling(
      Elements elementUtils, String codePackageName, TypeElement typeElement) {
    // If this is java.lang.Thread.State or the like, we have to look for a clash with Thread.
    TypeElement topLevelType = topLevelType(typeElement);
    TypeElement clash =
        elementUtils.getTypeElement(codePackageName + "." + topLevelType.getSimpleName());
    String fullName = typeElement.getQualifiedName().toString();
    return (clash == null) ? fullName.substring("java.lang.".length()) : fullName;
  }

  /**
   * Finds the top-level types for all the declared types (classes and interfaces) in the given
   * {@code Set<TypeMirror>}.
   *
   * <p>The returned set contains only top-level types. If we reference {@code java.util.Map.Entry}
   * then the returned set will contain {@code java.util.Map}. This is because we want to write
   * {@code Map.Entry} everywhere rather than {@code Entry}.
   */
  private static Set<TypeMirror> topLevelTypes(Types typeUtil, Set<TypeMirror> types) {
    return types.stream()
        .map(typeMirror -> MoreElements.asType(typeUtil.asElement(typeMirror)))
        .map(typeElement -> topLevelType(typeElement).asType())
        .collect(toCollection(TypeMirrorSet::new));
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
    Set<String> ambiguous = new HashSet<>();
    Map<String, Name> simpleNamesToQualifiedNames = new HashMap<>();
    for (TypeMirror type : types) {
      if (type.getKind() == TypeKind.ERROR) {
        throw new MissingTypeException(MoreTypes.asError(type));
      }
      String simpleName = typeUtils.asElement(type).getSimpleName().toString();
      /*
       * Compare by qualified names, because in Eclipse JDT, if Java 8 type annotations are used,
       * the same (unannotated) type may appear multiple times in the Set<TypeMirror>.
       * TODO(emcmanus): investigate further, because this might cause problems elsewhere.
       */
      Name qualifiedName = ((QualifiedNameable) typeUtils.asElement(type)).getQualifiedName();
      Name previous = simpleNamesToQualifiedNames.put(simpleName, qualifiedName);
      if (previous != null && !previous.equals(qualifiedName)) {
        ambiguous.add(simpleName);
      }
    }
    return ambiguous;
  }

  /**
   * Returns true if casting to the given type will elicit an unchecked warning from the compiler.
   * Only generic types such as {@code List<String>} produce such warnings. There will be no warning
   * if the type's only generic parameters are simple wildcards, as in {@code Map<?, ?>}.
   */
  static boolean isCastingUnchecked(TypeMirror type) {
    return CASTING_UNCHECKED_VISITOR.visit(type, null);
  }

  private static final TypeVisitor<Boolean, Void> CASTING_UNCHECKED_VISITOR =
      new SimpleTypeVisitor8<Boolean, Void>(false) {
        @Override
        public Boolean visitUnknown(TypeMirror t, Void p) {
          // We don't know whether casting is unchecked for this mysterious type but assume it is,
          // so we will insert a possibly unnecessary @SuppressWarnings("unchecked").
          return true;
        }

        @Override
        public Boolean visitArray(ArrayType t, Void p) {
          return visit(t.getComponentType(), p);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return t.getTypeArguments().stream().anyMatch(TypeSimplifier::uncheckedTypeArgument);
        }

        @Override
        public Boolean visitTypeVariable(TypeVariable t, Void p) {
          return true;
        }
      };

  // If a type has a type argument, then casting to the type is unchecked, except if the argument
  // is <?> or <? extends Object>. The same applies to all type arguments, so casting to Map<?, ?>
  // does not produce an unchecked warning for example.
  private static boolean uncheckedTypeArgument(TypeMirror arg) {
    if (arg.getKind() == TypeKind.WILDCARD) {
      WildcardType wildcard = (WildcardType) arg;
      if (wildcard.getExtendsBound() == null || isJavaLangObject(wildcard.getExtendsBound())) {
        // This is <?>, unless there's a super bound, in which case it is <? super Foo> and
        // is erased.
        return (wildcard.getSuperBound() != null);
      }
    }
    return true;
  }

  private static boolean isJavaLangObject(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    return typeElement.getQualifiedName().contentEquals("java.lang.Object");
  }
}
