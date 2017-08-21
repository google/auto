# Generated example


For the code shown in the [introduction](index.md), the following is typical
code AutoValue might generate:

```java
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Animal extends Animal {
  private final String name;
  private final int numberOfLegs;

  AutoValue_Animal(String name, int numberOfLegs) {
    if (name == null) {
      throw new NullPointerException("Null name");
    }
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
        + "numberOfLegs=" + numberOfLegs + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Animal) {
      Animal that = (Animal) o;
      return this.name.equals(that.name())
          && this.numberOfLegs == that.numberOfLegs();
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
}
```
