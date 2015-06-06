
package com.example.annotations;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoAnnotationProcessor")
final class AutoAnnotation_Erroneous_newEmpty implements Erroneous.Empty {

  AutoAnnotation_Erroneous_newEmpty(
 ) {
  }

  @Override
  public Class<? extends Erroneous.Empty> annotationType() {
    return Erroneous.Empty.class;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("@com.example.annotations.Erroneous.Empty(");
    return sb.append(')').toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Erroneous.Empty) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 0;
  }

}
