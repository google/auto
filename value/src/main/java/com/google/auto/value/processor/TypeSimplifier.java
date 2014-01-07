package com.google.auto.value.processor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;
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

  private final Types typeUtil;
  private final Map<String, Spelling> imports;

  TypeSimplifier(Types typeUtil, String packageName, Set<TypeMirror> types) {
    this.typeUtil = typeUtil;
    Set<TypeMirror> referenced = referencedClassTypes(typeUtil, types);
    this.imports = findImports(typeUtil, packageName, referenced);
  }

  /**
   * Returns the set of types to import. We import every type that is neither in java.lang nor in
   * the package containing the AutoValue class, provided that the result refers to the type
   * unambiguously. For example, if there is a property of type java.util.Map.Entry then we will
   * import java.util.Map.Entry and refer to the property as Entry. We could also import just
   * java.util.Map in this case and refer to Map.Entry, but currently we never do that.
   */
  SortedSet<String> typesToImport() {
    SortedSet<String> typesToImport = new TreeSet<String>();
    for (Map.Entry<String, Spelling> entry : imports.entrySet()) {
      if (entry.getValue().importIt) {
        typesToImport.add(entry.getKey());
      }
    }
    return typesToImport;
  }

  /**
   * Returns a string that can be used to refer to the given type given the imports defined by
   * {@link #typesToImport}.
   */
  String simplify(TypeMirror type) {
    return type.accept(new ToStringTypeVisitor(), new StringBuilder()).toString();
  }

  /**
   * Visitor that produces a string representation of a type for use in generated code.
   * The visitor takes into account the imports defined by {@link #typesToImport} and will use
   * the short names of those types.
   *
   * <p>A simpler alternative would be just to use TypeMirror.toString() and regular expressions to
   * pick apart the type references and replace fully-qualified types where possible. That depends
   * on unspecified behaviour of TypeMirror.toString(), though, and is vulnerable to formatting
   * quirks such as the way it omits the comma in
   * {@code java.util.Map<java.lang.String, java.lang.String>}.
   */
  private class ToStringTypeVisitor extends SimpleTypeVisitor6<StringBuilder, StringBuilder> {
    @Override protected StringBuilder defaultAction(TypeMirror type, StringBuilder sb) {
      return sb.append(type);
    }

    @Override public StringBuilder visitArray(ArrayType type, StringBuilder sb) {
      return visit(type.getComponentType(), sb).append("[]");
    }

    @Override public StringBuilder visitDeclared(DeclaredType type, StringBuilder sb) {
      TypeElement typeElement = (TypeElement) typeUtil.asElement(type);
      String typeString = typeElement.getQualifiedName().toString();
      if (imports.containsKey(typeString)) {
        sb.append(imports.get(typeString).spelling);
      } else {
        sb.append(typeString);
      }
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
      return sb;
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

  /**
   * Returns the name of the package that the given type is in. If the type is in the default
   * (unnamed) package then the name is the empty string.
   */
  static String packageNameOf(TypeElement type) {
    while (true) {
      Element enclosing = type.getEnclosingElement();
      if (enclosing instanceof PackageElement) {
        return ((PackageElement) enclosing).getQualifiedName().toString();
      }
      type = (TypeElement) enclosing;
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
   * @return a map where the keys are fully-qualified types and the corresponding values indicate
   *     whether the type should be imported, and how the type should be spelled in the source code.
   */
  private static Map<String, Spelling> findImports(
      Types typeUtil, String packageName, Set<TypeMirror> referenced) {
    Map<String, Spelling> imports = new HashMap<String, Spelling>();
    Set<String> ambiguous = ambiguousNames(typeUtil, referenced);
    for (TypeMirror type : referenced) {
      TypeElement typeElement = (TypeElement) typeUtil.asElement(type);
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
   */
  private static Set<TypeMirror> referencedClassTypes(Types typeUtil, Set<TypeMirror> types) {
    Set<TypeMirror> referenced = new HashSet<TypeMirror>();
    TypeVisitor<Void, Void> typeVisitor = new ReferencedClassTypeVisitor(typeUtil, referenced);
    for (TypeMirror type : types) {
      type.accept(typeVisitor, null);
    }
    return referenced;
  }

  private static class ReferencedClassTypeVisitor extends SimpleTypeVisitor6<Void, Void> {
    private final Types typeUtil;
    private final Set<TypeMirror> referenced;

    ReferencedClassTypeVisitor(Types typeUtil, Set<TypeMirror> referenced) {
      this.typeUtil = typeUtil;
      this.referenced = referenced;
    }

    @Override
    public Void visitArray(ArrayType t, Void p) {
      return visit(t.getComponentType(), p);
    }

    @Override
    public Void visitDeclared(DeclaredType t, Void p) {
      referenced.add(typeUtil.erasure(t));
      for (TypeMirror param : t.getTypeArguments()) {
        visit(param, p);
      }
      return null;
    }

    @Override
    public Void visitTypeVariable(TypeVariable t, Void p) {
      visit(t.getLowerBound(), p);
      return visit(t.getUpperBound(), p);
    }

    @Override
    public Void visitWildcard(WildcardType t, Void p) {
      for (TypeMirror bound : new TypeMirror[] {t.getSuperBound(), t.getExtendsBound()}) {
        if (bound != null) {
          visit(bound, p);
        }
      }
      return null;
    }
  }

  private static Set<String> ambiguousNames(Types typeUtil, Set<TypeMirror> types) {
    Set<String> ambiguous = new HashSet<String>();
    Set<String> simpleNames = new HashSet<String>();
    for (TypeMirror type : types) {
      String simpleName = typeUtil.asElement(type).getSimpleName().toString();
      if (!simpleNames.add(simpleName)) {
        ambiguous.add(simpleName);
      }
    }
    return ambiguous;
  }
}
