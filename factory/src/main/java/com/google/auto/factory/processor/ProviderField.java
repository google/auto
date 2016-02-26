package com.google.auto.factory.processor;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import javax.lang.model.element.AnnotationMirror;

import static com.google.auto.factory.processor.Mirrors.unwrapOptionalEquivalence;
import static com.google.auto.factory.processor.Mirrors.wrapOptionalInEquivalence;

@AutoValue
abstract class ProviderField {
  abstract String name();
  abstract Key key();
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> nullableWrapper();

  Optional<AnnotationMirror> nullable() {
    return unwrapOptionalEquivalence(nullableWrapper());
  }

  static ProviderField create(String name, Key key, Optional<AnnotationMirror> nullable) {
    return new AutoValue_ProviderField(
        name, key, wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), nullable));
  }
}
