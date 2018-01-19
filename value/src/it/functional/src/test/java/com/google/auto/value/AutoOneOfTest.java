/*
 * Copyright (C) 2018 Google, Inc.
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
package com.google.auto.value;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.testing.EqualsTester;
import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class AutoOneOfTest {
  @AutoValue
  public abstract static class Dog {
    public abstract String name();

    public static Dog create(String name) {
      return new AutoValue_AutoOneOfTest_Dog(name);
    }

    public void bark() {}
  }

  @AutoValue
  public abstract static class Cat {
    public static Cat create() {
      return new AutoValue_AutoOneOfTest_Cat();
    }

    public void meow() {}
  }

  @AutoValue
  public abstract static class TigerShark {
    public static TigerShark create() {
      return new AutoValue_AutoOneOfTest_TigerShark();
    }

    public void chomp() {}
  }

  @AutoOneOf(Pet.Kind.class)
  public abstract static class Pet {
    public enum Kind {DOG, CAT, TIGER_SHARK}

    public abstract Dog dog();
    public abstract Cat cat();
    public abstract TigerShark tigerShark();

    public static Pet dog(Dog dog) {
      return AutoOneOf_AutoOneOfTest_Pet.dog(dog);
    }

    public static Pet cat(Cat cat) {
      return AutoOneOf_AutoOneOfTest_Pet.cat(cat);
    }

    public static Pet tigerShark(TigerShark shark) {
      return AutoOneOf_AutoOneOfTest_Pet.tigerShark(shark);
    }

    public abstract Kind getKind();
  }

  @Test
  public void equality() {
    Dog marvin1 = Dog.create("Marvin");
    Pet petMarvin1 = Pet.dog(marvin1);
    Dog marvin2 = Dog.create("Marvin");
    Pet petMarvin2 = Pet.dog(marvin2);
    Dog isis = Dog.create("Isis");
    Pet petIsis = Pet.dog(isis);
    Cat cat = Cat.create();
    new EqualsTester()
        .addEqualityGroup(marvin1, marvin2)
        .addEqualityGroup(petMarvin1, petMarvin2)
        .addEqualityGroup(petIsis)
        .addEqualityGroup(cat)
        .addEqualityGroup("foo")
        .testEquals();
  }

  @Test
  public void getCorrectType() {
    Dog marvin = Dog.create("Marvin");
    Pet petMarvin = Pet.dog(marvin);
    assertThat(petMarvin.dog()).isSameAs(marvin);
  }

  @Test
  public void getWrongType() {
    Cat cat = Cat.create();
    Pet petCat = Pet.cat(cat);
    try {
      petCat.tigerShark();
      fail();
    } catch (UnsupportedOperationException e) {
      assertThat(e).hasMessageThat().containsMatch("(?i:cat)");
    }
  }

  @Test
  public void string() {
    Dog marvin = Dog.create("Marvin");
    Pet petMarvin = Pet.dog(marvin);
    assertThat(petMarvin.toString()).isEqualTo("Pet{dog=Dog{name=Marvin}}");
  }

  @Test
  public void getKind() {
    Dog marvin = Dog.create("Marvin");
    Pet petMarvin = Pet.dog(marvin);
    Cat cat = Cat.create();
    Pet petCat = Pet.cat(cat);
    TigerShark shark = TigerShark.create();
    Pet petShark = Pet.tigerShark(shark);
    assertThat(petMarvin.getKind()).isEqualTo(Pet.Kind.DOG);
    assertThat(petCat.getKind()).isEqualTo(Pet.Kind.CAT);
    assertThat(petShark.getKind()).isEqualTo(Pet.Kind.TIGER_SHARK);
  }

  @Test
  public void cannotBeNull() {
    try {
      Pet.dog(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  // Package-private case.

  @AutoOneOf(IntegerOrString.Kind.class)
  abstract static class IntegerOrString {
    enum Kind {INTEGER, STRING}
    abstract Kind getKind();
    abstract int integer();
    abstract String string();

    static IntegerOrString of(int x) {
      return AutoOneOf_AutoOneOfTest_IntegerOrString.integer(x);
    }

    static IntegerOrString of(String x) {
      return AutoOneOf_AutoOneOfTest_IntegerOrString.string(x);
    }
  }

  @Test
  public void packagePrivate() {
    IntegerOrString integer = IntegerOrString.of(23);
    IntegerOrString string = IntegerOrString.of("23");
    assertThat(integer.getKind()).isEqualTo(IntegerOrString.Kind.INTEGER);
    assertThat(string.getKind()).isEqualTo(IntegerOrString.Kind.STRING);
    assertThat(integer.integer()).isEqualTo(23);
    assertThat(string.string()).isEqualTo("23");
    assertThat(integer).isNotEqualTo(string);
    try {
      integer.string();
      fail();
    } catch (UnsupportedOperationException e) {
      assertThat(e).hasMessageThat().containsMatch("(?i:integer)");
    }
  }

  @AutoOneOf(Pet.Kind.class)
  public abstract static class PetWithGet {
    public abstract Dog getDog();
    public abstract Cat getCat();
    public abstract TigerShark getTigerShark();

    public static PetWithGet dog(Dog dog) {
      return AutoOneOf_AutoOneOfTest_PetWithGet.dog(dog);
    }

    public static PetWithGet cat(Cat cat) {
      return AutoOneOf_AutoOneOfTest_PetWithGet.cat(cat);
    }

    public static PetWithGet tigerShark(TigerShark shark) {
      return AutoOneOf_AutoOneOfTest_PetWithGet.tigerShark(shark);
    }

    public abstract Pet.Kind getKind();
  }

  @Test
  public void getPrefix() {
    Dog marvin = Dog.create("Marvin");
    PetWithGet petMarvin = PetWithGet.dog(marvin);
    assertThat(petMarvin.toString()).isEqualTo("PetWithGet{dog=Dog{name=Marvin}}");
  }

  @AutoOneOf(Primitive.Kind.class)
  public abstract static class Primitive {
    public enum Kind {A_BYTE, A_SHORT, AN_INT, A_LONG, A_FLOAT, A_DOUBLE, A_CHAR, A_BOOLEAN}
    public abstract Kind getKind();

    public abstract byte aByte();
    public abstract short aShort();
    public abstract int anInt();
    public abstract long aLong();
    public abstract float aFloat();
    public abstract double aDouble();
    public abstract char aChar();
    public abstract boolean aBoolean();

    public static Primitive of(byte x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aByte(x);
    }

    public static Primitive of(short x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aShort(x);
    }

    public static Primitive of(int x) {
      return AutoOneOf_AutoOneOfTest_Primitive.anInt(x);
    }

    public static Primitive of(long x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aLong(x);
    }

    public static Primitive of(float x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aFloat(x);
    }

    public static Primitive of(double x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aDouble(x);
    }

    public static Primitive of(char x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aChar(x);
    }

    public static Primitive of(boolean x) {
      return AutoOneOf_AutoOneOfTest_Primitive.aBoolean(x);
    }
  }

  @Test
  public void primitive() {
    Primitive primitive = Primitive.of(17);
    assertThat(primitive.anInt()).isEqualTo(17);
    assertThat(primitive.toString()).isEqualTo("Primitive{anInt=17}");
  }

  @AutoOneOf(OneOfOne.Kind.class)
  public abstract static class OneOfOne {
    public enum Kind {DOG}

    public abstract Dog getDog();

    public static OneOfOne dog(Dog dog) {
      return AutoOneOf_AutoOneOfTest_OneOfOne.dog(dog);
    }

    public abstract Kind getKind();
  }

  @Test
  public void oneOfOne() {
    Dog marvin = Dog.create("Marvin");
    OneOfOne oneOfMarvin = OneOfOne.dog(marvin);
    assertThat(oneOfMarvin.toString()).isEqualTo("OneOfOne{dog=Dog{name=Marvin}}");
    assertThat(oneOfMarvin.getKind()).isEqualTo(OneOfOne.Kind.DOG);
  }

  // We allow this for consistency, even though it's obviously pretty useless.
  // The generated code might be rubbish, but it compiles. No concrete implementation is generated
  // so there isn't really anything to test beyond that it compiles.
  @AutoOneOf(OneOfNone.Kind.class)
  public abstract static class OneOfNone {
    public enum Kind {}

    public abstract Kind getKind();
  }

  // Testing generics. Typically generics will be a bit messy because the @AutoOneOf class must
  // have type parameters for every property that needs them, even though any given property
  // might not use all the type parameters.
  @AutoOneOf(TaskResult.Kind.class)
  public abstract static class TaskResult<V extends Serializable> {
    public enum Kind {VALUE, EXCEPTION}

    public abstract Kind getKind();

    public abstract V value();

    public abstract Throwable exception();

    public V get() throws ExecutionException {
      switch (getKind()) {
        case VALUE:
          return value();
        case EXCEPTION:
          throw new ExecutionException(exception());
      }
      throw new AssertionError(getKind());
    }

    static <V extends Serializable> TaskResult<V> value(V value) {
      return AutoOneOf_AutoOneOfTest_TaskResult.value(value);
    }

    static TaskResult<?> exception(Throwable exception) {
      return AutoOneOf_AutoOneOfTest_TaskResult.exception(exception);
    }
  }

  @Test
  public void taskResultValue() throws Exception {
    TaskResult<String> result = TaskResult.value("foo");
    assertThat(result.get()).isEqualTo("foo");
  }

  @Test
  public void taskResultException() {
    Exception exception = new IllegalArgumentException("oops");
    TaskResult<?> result = TaskResult.exception(exception);
    try {
      result.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isEqualTo(exception);
    }
  }

  @AutoOneOf(CustomToString.Kind.class)
  public abstract static class CustomToString {
    public enum Kind {ACE}
    public abstract Kind getKind();
    public abstract String ace();

    public static CustomToString ace(String ace) {
      return AutoOneOf_AutoOneOfTest_CustomToString.ace(ace);
    }

    @Override
    public String toString() {
      return "blim";
    }
  }

  // If you have an explicit toString() method, we won't override it.
  @Test
  public void customToString() {
    CustomToString x = CustomToString.ace("ceg");
    assertThat(x.toString()).isEqualTo("blim");
  }

  @AutoOneOf(AbstractToString.Kind.class)
  public abstract static class AbstractToString {
    public enum Kind {ACE}
    public abstract Kind getKind();
    public abstract String ace();

    public static AbstractToString ace(String ace) {
      return AutoOneOf_AutoOneOfTest_AbstractToString.ace(ace);
    }

    @Override
    public abstract String toString();
  }

  // If you have an explicit abstract toString() method, we will implement it.
  @Test
  public void abstractToString() {
    AbstractToString x = AbstractToString.ace("ceg");
    assertThat(x.toString()).isEqualTo("AbstractToString{ace=ceg}");
  }

  // "package" is a reserved word. You probably don't want to have a property with that name,
  // but if you insist, you can get one by using getFoo()-style methods. We leak our renaming
  // scheme here (package0) and for users that that bothers they can just avoid having properties
  // that are reserved words.
  @AutoOneOf(LetterOrPackage.Kind.class)
  public abstract static class LetterOrPackage {
    public enum Kind {LETTER, PACKAGE}
    public abstract Kind getKind();
    public abstract String getLetter();
    public abstract String getPackage();

    public static LetterOrPackage ofLetter(String letter) {
      return AutoOneOf_AutoOneOfTest_LetterOrPackage.letter(letter);
    }

    public static LetterOrPackage ofPackage(String pkg) {
      return AutoOneOf_AutoOneOfTest_LetterOrPackage.package0(pkg);
    }
  }

  @Test
  public void reservedWordProperty() {
    LetterOrPackage pkg = LetterOrPackage.ofPackage("pacquet");
    assertThat(pkg.toString()).isEqualTo("LetterOrPackage{package=pacquet}");
  }
}
