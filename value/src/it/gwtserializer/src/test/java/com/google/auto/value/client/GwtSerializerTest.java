/*
 * Copyright 2015 Google LLC
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
package com.google.auto.value.client;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class GwtSerializerTest extends GWTTestCase {

  @RemoteServiceRelativePath("test")
  public interface TestService extends RemoteService {
    Simple echo(Simple simple);

    SimpleWithBuilder echo(SimpleWithBuilder simple);

    Nested echo(Nested nested);

    NestedWithBuilder echo(NestedWithBuilder nested);

    Generics<Simple> echo(Generics<Simple> generics);

    GenericsWithBuilder<SimpleWithBuilder> echo(GenericsWithBuilder<SimpleWithBuilder> generics);
  }

  interface TestServiceAsync {
    void echo(Simple simple, AsyncCallback<Simple> callback);

    void echo(SimpleWithBuilder simple, AsyncCallback<SimpleWithBuilder> callback);

    void echo(Nested nested, AsyncCallback<Nested> callback);

    void echo(NestedWithBuilder nested, AsyncCallback<NestedWithBuilder> callback);

    void echo(Generics<Simple> generics, AsyncCallback<Generics<Simple>> callback);

    void echo(
        GenericsWithBuilder<SimpleWithBuilder> generics,
        AsyncCallback<GenericsWithBuilder<SimpleWithBuilder>> callback);
  }

  class AssertEqualsCallback<T> implements AsyncCallback<T> {
    private final T expected;

    AssertEqualsCallback(T expected) {
      this.expected = expected;
    }

    @Override
    public void onSuccess(T actual) {
      assertEquals(expected, actual);
      finishTest();
    }

    @Override
    public void onFailure(Throwable caught) {
      fail();
    }
  }

  @GwtIncompatible("RemoteServiceServlet")
  @SuppressWarnings("serial")
  public static class TestServiceImpl extends RemoteServiceServlet implements TestService {
    @Override
    public Simple echo(Simple simple) {
      return Simple.create(simple.message());
    }

    @Override
    public SimpleWithBuilder echo(SimpleWithBuilder simple) {
      return SimpleWithBuilder.builder().message(simple.message()).build();
    }

    @Override
    public Nested echo(Nested nested) {
      return Nested.create(nested.message(), echo(nested.simple()));
    }

    @Override
    public NestedWithBuilder echo(NestedWithBuilder nested) {
      return NestedWithBuilder.builder()
          .message(nested.message())
          .simple(echo(nested.simple()))
          .build();
    }

    @Override
    public Generics<Simple> echo(Generics<Simple> generics) {
      return Generics.create(echo(generics.simple()));
    }

    @Override
    public GenericsWithBuilder<SimpleWithBuilder> echo(
        GenericsWithBuilder<SimpleWithBuilder> generics) {
      return GenericsWithBuilder.<SimpleWithBuilder>builder()
          .simple(echo(generics.simple()))
          .build();
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class Simple {
    public abstract String message();

    public static Simple create(String message) {
      return new AutoValue_GwtSerializerTest_Simple(message);
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class SimpleWithBuilder {
    public abstract String message();

    public static Builder builder() {
      return new AutoValue_GwtSerializerTest_SimpleWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder message(String message);

      SimpleWithBuilder build();
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class Nested {
    public abstract String message();

    public abstract Simple simple();

    public static Nested create(String message, Simple simple) {
      return new AutoValue_GwtSerializerTest_Nested(message, simple);
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class NestedWithBuilder {
    public abstract String message();

    public abstract SimpleWithBuilder simple();

    public static Builder builder() {
      return new AutoValue_GwtSerializerTest_NestedWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder message(String message);

      Builder simple(SimpleWithBuilder simple);

      NestedWithBuilder build();
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class Generics<T> {
    public abstract T simple();

    public static <T> Generics<T> create(T simple) {
      return new AutoValue_GwtSerializerTest_Generics<T>(simple);
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class GenericsWithBuilder<T> {
    public abstract T simple();

    public static <T> Builder<T> builder() {
      return new AutoValue_GwtSerializerTest_GenericsWithBuilder.Builder<T>();
    }

    @AutoValue.Builder
    public interface Builder<T> {
      Builder<T> simple(T simple);

      GenericsWithBuilder<T> build();
    }
  }

  private TestServiceAsync testService;

  @Override
  public String getModuleName() {
    return "com.google.auto.value.GwtSerializerSuite";
  }

  @Override
  public void gwtSetUp() {
    testService = GWT.create(TestService.class);
  }

  public void testSimple() {
    delayTestFinish(2000);
    Simple simple = Simple.create("able");
    testService.echo(simple, new AssertEqualsCallback<Simple>(simple));
  }

  public void testSimpleWithBuilder() {
    delayTestFinish(2000);
    SimpleWithBuilder simple = SimpleWithBuilder.builder().message("able").build();
    testService.echo(simple, new AssertEqualsCallback<SimpleWithBuilder>(simple));
  }

  public void testNested() {
    delayTestFinish(2000);
    Nested nested = Nested.create("able", Simple.create("baker"));
    testService.echo(nested, new AssertEqualsCallback<Nested>(nested));
  }

  public void testNestedWithBuilder() {
    delayTestFinish(2000);
    NestedWithBuilder nested =
        NestedWithBuilder.builder()
            .message("able")
            .simple(SimpleWithBuilder.builder().message("baker").build())
            .build();
    testService.echo(nested, new AssertEqualsCallback<NestedWithBuilder>(nested));
  }

  public void testGenerics() {
    delayTestFinish(2000);
    Generics<Simple> generics = Generics.create(Simple.create("able"));
    testService.echo(generics, new AssertEqualsCallback<Generics<Simple>>(generics));
  }

  public void testGenericsWithBuilder() {
    delayTestFinish(2000);
    GenericsWithBuilder<SimpleWithBuilder> generics =
        GenericsWithBuilder.<SimpleWithBuilder>builder()
            .simple(SimpleWithBuilder.builder().message("able").build())
            .build();
    testService.echo(
        generics, new AssertEqualsCallback<GenericsWithBuilder<SimpleWithBuilder>>(generics));
  }
}
