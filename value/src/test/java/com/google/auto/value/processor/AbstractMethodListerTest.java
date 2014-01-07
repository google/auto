package com.google.auto.value.processor;

import junit.framework.TestCase;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link AbstractMethodLister}.
 *
 * @author Éamonn McManus
 */
public class AbstractMethodListerTest extends TestCase {
  /** Test class for the abstractNoArgMethods() test cases. */
  public abstract class Abstract {
    abstract int foo();
    abstract String baz(int x);
    abstract boolean bar();
    abstract void irrelevantVoid();
    abstract Thread baz();
    void irrelevant() {}
    int alsoIrrelevant() {
      return 0;
    }
  }

  /** Test class for the abstractNoArgMethods() test cases. */
  abstract class AbstractSub extends Abstract {
    @Override boolean bar() {
      return false;
    }

    public abstract String buh();
  }

  // Test that the abstract no-arg non-void methods we scan out of the given class correspond
  // to the expected ones. We use the hallowed trick of looking up the class file as a resource
  // in order to be able to read its contents.
  private void testAbstractNoArgMethods(Class<?> abstractClass, String... expectedMethods)
      throws Exception {
    ClassLoader loader = abstractClass.getClassLoader();
    String resourceName = abstractClass.getName().replace('.', '/') + ".class";
    InputStream inputStream = loader.getResourceAsStream(resourceName);
    AbstractMethodLister abstractMethodLister = new AbstractMethodLister(inputStream);
    List<String> methods = abstractMethodLister.abstractNoArgMethods();
    assertEquals(Arrays.asList(expectedMethods), methods);
  }

  public void testAbstractNoParent() throws Exception {
    testAbstractNoArgMethods(Abstract.class, "foo", "bar", "baz");
  }

  public void testAbstractWithParent() throws Exception {
    testAbstractNoArgMethods(AbstractSub.class, "buh");
  }
}
