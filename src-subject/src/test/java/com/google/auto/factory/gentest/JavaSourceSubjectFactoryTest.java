/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.auto.factory.gentest;

import static com.google.auto.factory.gentest.JavaSourceSubjectFactory.JAVA_SOURCE;
import static org.junit.Assert.assertTrue;
import static org.truth0.Truth.ASSERT;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.io.Files;
import com.google.common.io.Resources;

/**
 * Tests {@link JavaSourceSubjectFactory}.
 * 
 * @author Gregory Kick
 */
@RunWith(JUnit4.class)
public class JavaSourceSubjectFactoryTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();
  
  private File reference;
  
  @Before public void createReferenceFile() throws IOException {
    reference = folder.newFile("HelloWorld.java");
    Resources.asByteSource(Resources.getResource("HelloWorld.java"))
        .copyTo(Files.asByteSink(reference));
  }
  
  @Test
  public void equivalent() throws IOException {
    File v2 = folder.newFile("HelloWorld-v2.java");
    Resources.asByteSource(Resources.getResource("HelloWorld-v2.java"))
        .copyTo(Files.asByteSink(v2));
    ASSERT.about(JAVA_SOURCE).that(reference).isEquivalentTo(v2);
  }
  
  @Test
  public void throwsForBrokenSource() throws IOException {
    File broken = folder.newFile("HelloWorld-broken.java");
    Resources.asByteSource(Resources.getResource("HelloWorld-broken.java"))
        .copyTo(Files.asByteSink(broken));
    boolean threw = true;
    try {
      ASSERT.about(JAVA_SOURCE).that(reference).isEquivalentTo(broken);
      threw = false;
    } catch (AssertionError expected) {}
    assertTrue(threw);
  }

  @Test
  public void throwsForInequivalent() throws IOException {
    File different = folder.newFile("HelloWorld-different.java");
    Resources.asByteSource(Resources.getResource("HelloWorld-broken.java"))
        .copyTo(Files.asByteSink(different));
    boolean threw = true;
    try {
      ASSERT.about(JAVA_SOURCE).that(reference).isEquivalentTo(different);
      threw = false;
    } catch (AssertionError expected) {}
    assertTrue(threw);
  }
}
