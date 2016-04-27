package com.google.auto.value.processor;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A wrapper for properties of Optional-like classes. This can be com.google.common.base.Optional,
 * or any of Optional, OptionalDouble, OptionalInt, OptionalLong in java.util.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class Optionalish {
  private static final ImmutableSet<String> OPTIONAL_CLASS_NAMES = ImmutableSet.of(
      "com.".concat("google.common.base.Optional"),  // subterfuge to foil shading
      "java.util.Optional",
      "java.util.OptionalDouble",
      "java.util.OptionalInt",
      "java.util.OptionalLong");

  private final TypeElement optionalType;
  private final String rawTypeSpelling;

  private Optionalish(TypeElement optionalType, String rawTypeSpelling) {
    this.optionalType = optionalType;
    this.rawTypeSpelling = rawTypeSpelling;
  }

  /**
   * Returns an instance wrapping the given TypeMirror, or null if it is not any kind of Optional.
   *
   * @param type the TypeMirror for the original optional type, for example
   *     {@code Optional<String>}.
   * @param rawTypeSpelling the representation of the base Optional type in source code, given
   *     the imports that will be present. Usually this will be {@code Optional},
   *     {@code OptionalInt}, etc. In cases of ambiguity it might be {@code java.util.Optional} etc.
   */
  static Optionalish createIfOptional(TypeMirror type, String rawTypeSpelling) {
    TypeElement optionalType = asOptionalTypeElement(type);
    if (optionalType == null) {
      return null;
    } else {
      return new Optionalish(optionalType, Preconditions.checkNotNull(rawTypeSpelling));
    }
  }

  static boolean isOptional(TypeMirror type) {
    return asOptionalTypeElement(type) != null;
  }

  private static TypeElement asOptionalTypeElement(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    return OPTIONAL_CLASS_NAMES.contains(typeElement.getQualifiedName().toString())
        ? typeElement
        : null;
  }

  /**
   * Returns a string representing the method call to obtain the empty version of this Optional.
   * This will be something like {@code "Optional.empty()"} or possibly
   * {@code "java.util.Optional.empty()"}. It does not have a final semicolon.
   *
   * <p>This method is public so that it can be referenced as {@code p.optional.empty} from
   * templates.
   */
  public String getEmpty() {
    String empty = optionalType.getQualifiedName().toString().startsWith("java.util.")
        ? ".empty()"
        : ".absent()";
    return rawTypeSpelling + empty;
  }
}
