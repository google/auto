# Generated builder example


For the code shown in the [builder documentation](builders.md), the following is
typical code AutoValue might generate:

```java
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Animal extends Animal {
  private final String name;
  private final int numberOfLegs;

  private AutoValue_Animal(
      String name,
      int numberOfLegs) {
    this.name = name;
    this.numberOfLegs = numberOfLegs;
  }

  @Override
  String name() {
    return name;
  }

  @Override
  int numberOfLegs() {
    return numberOfLegs;
  }

  @Override
  public String toString() {
    return "Animal{"
        + "name=" + name + ", "
        + "numberOfLegs=" + numberOfLegs
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Animal) {
      Animal that = (Animal) o;
      return (this.name.equals(that.name()))
           && (this.numberOfLegs == that.numberOfLegs());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.name.hashCode();
    h *= 1000003;
    h ^= this.numberOfLegs;
    return h;
  }

  static final class Builder extends Animal.Builder {
    private String name;
    private Integer numberOfLegs;

    Builder() {
    }

    @Override
    Animal.Builder setName(String name) {
      if (name == null) {
        throw new NullPointerException("Null name");
      }
      this.name = name;
      return this;
    }

    @Override
    Animal.Builder setNumberOfLegs(int numberOfLegs) {
      this.numberOfLegs = numberOfLegs;
      return this;
    }

    @Override
    Animal build() {
      String missing = "";
      if (this.name == null) {
        missing += " name";
      }
      if (this.numberOfLegs == null) {
        missing += " numberOfLegs";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Animal(
          this.name,
          this.numberOfLegs);
    }
  }
}
```
