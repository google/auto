package com.google.autofactory;

import com.google.common.base.Objects;
import com.google.common.base.Optional;

final class Key {
  private final String type;
  private final Optional<String> qualifier;

  Key(Optional<String> qualifier, String type) {
    this.qualifier = qualifier;
    this.type = type;
  }

  Optional<String> getQualifier() {
    return qualifier;
  }

  String getType() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof Key) {
      Key that = (Key) obj;
      return this.type.equals(that.type)
          && this.qualifier.equals(that.qualifier);
    }
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(qualifier, type);
  }

  @Override
  public String toString() {
    return qualifier.isPresent()
        ? qualifier.get() + '/' + type
        : type;
  }
}
