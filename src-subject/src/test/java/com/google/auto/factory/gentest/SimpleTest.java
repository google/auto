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
import static org.junit.Assert.*;
import static org.truth0.Truth.ASSERT;

import java.io.File;

import org.junit.Test;

public class SimpleTest {

	@Test
	public void test() {
		File file1 = new File("/Users/gak/Desktop/App2.java");
		File file2 = new File("/Users/gak/Desktop/App.java");
		ASSERT.about(JAVA_SOURCE).that(file1).isEquivalentTo(file2);
	}

	@Test
	public void otherTest() {
		File file1 = new File("/Users/gak/Desktop/App2.java");
		File file2 = new File("/Users/gak/Desktop/App3.java");
		ASSERT.about(JAVA_SOURCE).that(file1).isEquivalentTo(file2);
	}

}
