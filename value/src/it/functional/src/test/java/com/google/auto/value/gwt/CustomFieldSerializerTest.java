/*
 * Copyright 2014 Google LLC
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
package com.google.auto.value.gwt;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.Reflection;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that the generated GWT serializer for GwtValueType serializes fields in the expected way.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class CustomFieldSerializerTest {
  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class ValueType implements Serializable {
    abstract String string();

    abstract int integer();

    @Nullable
    abstract ValueType other();

    abstract List<ValueType> others();

    static ValueType create(String string, int integer, @Nullable ValueType other) {
      return create(string, integer, other, Collections.<ValueType>emptyList());
    }

    static ValueType create(
        String string, int integer, @Nullable ValueType other, List<ValueType> others) {
      return new AutoValue_CustomFieldSerializerTest_ValueType(string, integer, other, others);
    }
  }

  private static final ValueType SIMPLE = ValueType.create("anotherstring", 1729, null);
  private static final ValueType CONS = ValueType.create("whatever", 1296, SIMPLE);
  private static final ValueType WITH_LIST =
      ValueType.create("blim", 11881376, SIMPLE, ImmutableList.of(SIMPLE, CONS));

  private final MickeyMouseMock<SerializationStreamWriter> mock =
      new MickeyMouseMock<>(SerializationStreamWriter.class);
  private final SerializationStreamWriter streamWriter = mock.proxy();

  @Test
  public void testCustomFieldSerializer() throws SerializationException {
    AutoValue_CustomFieldSerializerTest_ValueType withList =
        (AutoValue_CustomFieldSerializerTest_ValueType) WITH_LIST;
    AutoValue_CustomFieldSerializerTest_ValueType_CustomFieldSerializer.serialize(
        streamWriter, withList);
    mock.verify(
        () -> {
          streamWriter.writeString("blim");
          streamWriter.writeInt(11881376);
          streamWriter.writeObject(SIMPLE);
          streamWriter.writeObject(ImmutableList.of(SIMPLE, CONS));
        });
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class ValueTypeWithGetters implements Serializable {
    abstract String getPackage();

    abstract boolean isDefault();

    static ValueTypeWithGetters create(String pkg, boolean dflt) {
      return new AutoValue_CustomFieldSerializerTest_ValueTypeWithGetters(pkg, dflt);
    }
  }

  @Test
  public void testCustomFieldSerializerWithGetters() throws SerializationException {
    AutoValue_CustomFieldSerializerTest_ValueTypeWithGetters instance =
        (AutoValue_CustomFieldSerializerTest_ValueTypeWithGetters)
            ValueTypeWithGetters.create("package", true);
    AutoValue_CustomFieldSerializerTest_ValueTypeWithGetters_CustomFieldSerializer.serialize(
        streamWriter, instance);
    mock.verify(
        () -> {
          streamWriter.writeString("package");
          streamWriter.writeBoolean(true);
        });
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class GenericValueType<K extends Comparable<K>, V extends K>
      implements Serializable {
    abstract Map<K, V> map();

    static <K extends Comparable<K>, V extends K> GenericValueType<K, V> create(Map<K, V> map) {
      return new AutoValue_CustomFieldSerializerTest_GenericValueType<K, V>(map);
    }
  }

  @Test
  public void testCustomFieldSerializerGeneric() throws SerializationException {
    Map<Integer, Integer> map = ImmutableMap.of(2, 2);
    AutoValue_CustomFieldSerializerTest_GenericValueType<Integer, Integer> instance =
        (AutoValue_CustomFieldSerializerTest_GenericValueType<Integer, Integer>)
            GenericValueType.create(map);
    AutoValue_CustomFieldSerializerTest_GenericValueType_CustomFieldSerializer.serialize(
        streamWriter, instance);
    mock.verify(
        () -> {
          streamWriter.writeObject(map);
        });
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class ValueTypeWithBuilder implements Serializable {
    abstract String string();

    abstract ImmutableList<String> strings();

    static Builder builder() {
      return new AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilder.Builder();
    }

    @AutoValue.Builder
    interface Builder {
      Builder string(String x);

      Builder strings(ImmutableList<String> x);

      ValueTypeWithBuilder build();
    }
  }

  @Test
  public void testCustomFieldSerializerWithBuilder() throws SerializationException {
    AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilder instance =
        (AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilder)
            ValueTypeWithBuilder.builder().string("s").strings(ImmutableList.of("a", "b")).build();
    AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilder_CustomFieldSerializer.serialize(
        streamWriter, instance);
    mock.verify(
        () -> {
          streamWriter.writeString("s");
          streamWriter.writeObject(ImmutableList.of("a", "b"));
        });
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class ValueTypeWithBuilderAndGetters implements Serializable {
    abstract String getPackage();

    abstract boolean isDefault();

    static Builder builder() {
      return new AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilderAndGetters.Builder();
    }

    @AutoValue.Builder
    interface Builder {
      Builder setPackage(String x);

      Builder setDefault(boolean x);

      ValueTypeWithBuilderAndGetters build();
    }
  }

  @Test
  public void testCustomFieldSerializerWithBuilderAndGetters() throws SerializationException {
    AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilderAndGetters instance =
        (AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilderAndGetters)
            ValueTypeWithBuilderAndGetters.builder().setPackage("s").setDefault(false).build();
    AutoValue_CustomFieldSerializerTest_ValueTypeWithBuilderAndGetters_CustomFieldSerializer
        .serialize(streamWriter, instance);
    mock.verify(
        () -> {
          streamWriter.writeString("s");
          streamWriter.writeBoolean(false);
        });
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class GenericValueTypeWithBuilder<K extends Comparable<K>, V extends K>
      implements Serializable {
    abstract Map<K, V> map();

    static <K extends Comparable<K>, V extends K> Builder<K, V> builder() {
      return new AutoValue_CustomFieldSerializerTest_GenericValueTypeWithBuilder.Builder<K, V>();
    }

    @AutoValue.Builder
    interface Builder<K extends Comparable<K>, V extends K> {
      Builder<K, V> map(Map<K, V> map);

      GenericValueTypeWithBuilder<K, V> build();
    }
  }

  @Test
  public void testCustomFieldSerializerGenericWithBuilder() throws SerializationException {
    Map<Integer, Integer> map = ImmutableMap.of(2, 2);
    AutoValue_CustomFieldSerializerTest_GenericValueTypeWithBuilder<Integer, Integer> instance =
        (AutoValue_CustomFieldSerializerTest_GenericValueTypeWithBuilder<Integer, Integer>)
            GenericValueTypeWithBuilder.<Integer, Integer>builder().map(map).build();
    AutoValue_CustomFieldSerializerTest_GenericValueTypeWithBuilder_CustomFieldSerializer.serialize(
        streamWriter, instance);
    mock.verify(
        () -> {
          streamWriter.writeObject(map);
        });
  }

  @AutoValue
  abstract static class MethodCall {
    abstract String method();

    abstract ImmutableList<Object> args();

    static MethodCall of(String method, ImmutableList<Object> args) {
      return new AutoValue_CustomFieldSerializerTest_MethodCall(method, args);
    }
  }

  /**
   * A trivial home-made mocking framework.
   *
   * <p>Mockito 5 no longer supports Java 8, and we do, so rather than pinning to an older version
   * of Mockito we fake it with {@link Reflection}. This is only really possible because the thing
   * we want to mock ({@link SerializationStreamWriter}) is an interface. Furthermore all its
   * methods return void so we don't even need a way to specify what to return.
   *
   * <p>The idea is that you make an instance of this class, have the code under test call methods
   * on it, then call {@link #verify} with a lambda that repeats the expected calls. If they match
   * the actual calls then the test passes.
   */
  private static class MickeyMouseMock<T> {
    private boolean recording = true;
    private final Deque<MethodCall> methodCalls = new ArrayDeque<>();
    private final T proxy;

    MickeyMouseMock(Class<T> intf) {
      this.proxy = Reflection.newProxy(intf, this::invocationHandler);
    }

    T proxy() {
      return proxy;
    }

    void verify(ThrowingRunnable actions) {
      assertThat(recording).isTrue();
      recording = false;
      try {
        actions.run();
      } catch (AssertionError e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      assertThat(methodCalls).isEmpty();
    }

    private Object invocationHandler(Object proxy, Method method, Object[] args) {
      if (args == null) {
        args = new Object[0];
      }
      MethodCall methodCall = MethodCall.of(method.getName(), ImmutableList.copyOf(args));
      if (recording) {
        methodCalls.add(methodCall);
      } else { // verifying
        assertWithMessage("Missing call %s", methodCall).that(methodCalls).isNotEmpty();
        MethodCall recorded = methodCalls.removeFirst();
        assertThat(methodCall).isEqualTo(recorded);
      }
      return null;
    }
  }
}
