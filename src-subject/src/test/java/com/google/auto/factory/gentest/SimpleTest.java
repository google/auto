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
