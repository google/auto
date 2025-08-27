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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtCompatible;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that the generated serializer class for a GWT-serializable class contains a dummy field to
 * influence the signature, and that different classes have different signatures.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class SerialSignatureTest {
  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class One {
    abstract int foo();

    static One create(int foo) {
      return new AutoValue_SerialSignatureTest_One(foo);
    }
  }

  @AutoValue
  @GwtCompatible(serializable = true)
  abstract static class Two {
    abstract int foo();

    static Two create(int foo) {
      return new AutoValue_SerialSignatureTest_Two(foo);
    }
  }

  @Test
  public void testSerialSignatures() {
    Class<?> serializerOne = AutoValue_SerialSignatureTest_One_CustomFieldSerializer.class;
    Class<?> serializerTwo = AutoValue_SerialSignatureTest_Two_CustomFieldSerializer.class;
    String fieldNameOne = dummySignatureFieldName(serializerOne);
    String fieldNameTwo = dummySignatureFieldName(serializerTwo);
    assertFalse(fieldNameOne.equals(fieldNameTwo));
  }

  private static String dummySignatureFieldName(Class<?> c) {
    String name = null;
    for (Field f : c.getDeclaredFields()) {
      if (f.getName().startsWith("dummy_")) {
        assertNull("More than one field begins with dummy_: " + name + ", " + f.getName(), name);
      }
      name = f.getName();
    }
    assertNotNull("No field begins with dummy_", name);
    return name;
  }
}
