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
package com.google.auto.value.processor;

import com.google.auto.value.processor.AutoValueProcessor.Property;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Works around an Eclipse bug where methods are sorted into alphabetical order before being given
 * to annotation processors. Unfortunately this seems to be deeply built in to the JDT compiler
 * that Eclipse uses. The bug has been open for over three years with no progress.
 * <p>
 * To work around the problem, we access Eclipse-specific APIs to find the original source code of
 * the class with the {@code @AutoValue} annotation, and we do just enough parsing of that code to
 * be able to pick out the abstract method declarations so we can determine their order. The code
 * to access Eclipse-specific APIs will fail in environments other than Eclipse (for example, javac)
 * and the methods will be left in the order they came in, which in these other environments should
 * already be the correct order.
 * <p>
 * This is obviously a giant hack, and the right thing would be for the Eclipse compiler to be
 * fixed. The approach here works, but is vulnerable to future changes in the Eclipse API. If
 * {@code @AutoValue} constructor calls like {@code new AutoValue_Foo(...)} suddenly start being
 * redlined in a new Eclipse version then the likely cause is that the APIs have changed and this
 * hack will need to be updated to track the change.
 * <p>
 * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=300408">Eclipse bug 300408</a>
 *
 * @author Éamonn McManus
 */
class EclipseHack {
  static final String ENABLING_OPTION = "com.google.auto.value.EclipseHackTest";
  static final String ENABLING_OPTION_BATCH =
      "com.google.auto.value.EclipseBatchHackTest";

  private final ProcessingEnvironment processingEnv;
  private final boolean eclipseHackTest;
  private final boolean eclipseHackBatchTest;

  EclipseHack(ProcessingEnvironment processingEnv) {
    boolean eclipseHackTest = processingEnv.getOptions().containsKey(ENABLING_OPTION);
    this.processingEnv = eclipseHackTest
        ? new EclipseProcessingEnvironment(processingEnv)
        : processingEnv;
    this.eclipseHackTest = eclipseHackTest;
    eclipseHackBatchTest = processingEnv.getOptions().containsKey(ENABLING_OPTION_BATCH);
  }

  // Fake implementation of ProcessingEnvironment that looks like Eclipse's, for testing only.
  private static class EclipseProcessingEnvironment implements ProcessingEnvironment {
    private final ProcessingEnvironment processingEnv;

    EclipseProcessingEnvironment(ProcessingEnvironment processingEnv) {
      this.processingEnv = processingEnv;
    }

    @SuppressWarnings("unused") // accessed via reflection
    public EclipseIFile getEnclosingIFile(Element element) {
      return new EclipseIFile(processingEnv, (TypeElement) element);
    }

    @Override public Map<String, String> getOptions() {
      return processingEnv.getOptions();
    }

    @Override public Messager getMessager() {
      return processingEnv.getMessager();
    }

    @Override public Filer getFiler() {
      return processingEnv.getFiler();
    }

    @Override public Elements getElementUtils() {
      return processingEnv.getElementUtils();
    }

    @Override public Types getTypeUtils() {
      return processingEnv.getTypeUtils();
    }

    @Override public SourceVersion getSourceVersion() {
      return processingEnv.getSourceVersion();
    }

    @Override public Locale getLocale() {
      return processingEnv.getLocale();
    }
  }

  private static File getTestFile(ProcessingEnvironment processingEnv, TypeElement element) {
      Filer filer = processingEnv.getFiler();
      // walk up the enclosing elements until you find a top-level element
      Element topLevel = element;
      while (topLevel.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
        topLevel = topLevel.getEnclosingElement();
      }
      try {
        FileObject resource = filer.getResource(StandardLocation.SOURCE_PATH,
            processingEnv.getElementUtils().getPackageOf(element).getQualifiedName(),
            topLevel.getSimpleName() + ".java");
        File file = new File(resource.toUri());
        if (!file.canRead()) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot find source code in file " + file, element);
        }
        return file;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
  }

  /**
   * A simulation of an Eclipse IFile object used in the {@link EclipseProcessingEnvironment}.
   */
  private static class EclipseIFile {
    private final File file;

    EclipseIFile(ProcessingEnvironment processingEnv, TypeElement element) {
      this.file = getTestFile(processingEnv, element);
    }

    @SuppressWarnings("unused") // accessed via reflection
    public String getCharset() {
      return Charset.defaultCharset().name();
    }

    @SuppressWarnings("unused") // accessed via reflection
    public InputStream getContents() throws IOException {
      return new FileInputStream(file);
    }

    @SuppressWarnings("unused") // accessed via reflection
    public URI getRawLocationURI() {
      return file.toURI();
    }
  }

  /**
   * A wrapper around {@link TypeElement} that simulates the Eclipse implementation of that class.
   * The {@link #getFileName()} method is used to get the filename and is added to this class.
   */
  private class EclipseTypeElement implements TypeElement {
    private final TypeElement delegate;

    EclipseTypeElement(TypeElement delegate) {
      this.delegate = delegate;
    }

    @Override public TypeMirror asType() {
      return delegate.asType();
    }

    @Override public ElementKind getKind() {
      return delegate.getKind();
    }

    @Override public List<? extends AnnotationMirror> getAnnotationMirrors() {
      return delegate.getAnnotationMirrors();
    }

    @Override public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
      return delegate.getAnnotation(annotationType);
    }

    @Override public Set<Modifier> getModifiers() {
      return delegate.getModifiers();
    }

    @Override public <R, P> R accept(ElementVisitor<R, P> v, P p) {
      return delegate.accept(v, p);
    }

    @Override public List<? extends Element> getEnclosedElements() {
      return delegate.getEnclosedElements();
    }

    @Override public NestingKind getNestingKind() {
      return delegate.getNestingKind();
    }

    @Override public Name getQualifiedName() {
      return delegate.getQualifiedName();
    }

    @Override public Name getSimpleName() {
      return delegate.getSimpleName();
    }

    @Override public TypeMirror getSuperclass() {
      return delegate.getSuperclass();
    }

    @Override public List<? extends TypeMirror> getInterfaces() {
      return delegate.getInterfaces();
    }

    @Override public List<? extends TypeParameterElement> getTypeParameters() {
      return delegate.getTypeParameters();
    }

    @Override public Element getEnclosingElement() {
      return delegate.getEnclosingElement();
    }

    @SuppressWarnings("unused")
    public String getFileName() {
      return getTestFile(processingEnv, delegate).toString();
    }
  }

  private static final Comparator<ExecutableElement> ELEMENT_COMPARATOR =
      new Comparator<ExecutableElement>() {
    @Override public int compare(ExecutableElement a, ExecutableElement b) {
      return a.getSimpleName().toString().compareTo(b.getSimpleName().toString());
    }
  };

  void sortMethodsIfSimulatingEclipse(List<ExecutableElement> methods) {
    if (eclipseHackTest) {
      Collections.sort(methods, ELEMENT_COMPARATOR);
    }
  }

  /**
   * Reorders the properties (abstract methods) in the given list to correspond to the order found
   * by parsing the source code of the given type. In environments other than Eclipse this method
   * has no effect.
   */
  void reorderProperties(List<Property> properties) {
    // Eclipse sorts methods in each class. Because of the way we construct the list, we will see
    // all the abstract property methods from a given class or interface consecutively. So we can
    // fix each sublist independently.
    int index = 0;
    while (index < properties.size()) {
      TypeElement owner = properties.get(index).owner();
      int nextIndex = index + 1;
      while (nextIndex < properties.size() && properties.get(nextIndex).owner().equals(owner)) {
        nextIndex++;
      }
      List<Property> subList = properties.subList(index, nextIndex);
      reorderProperties(owner, subList);
      index = nextIndex;
    }
  }

  private void reorderProperties(TypeElement type, List<Property> properties) {
    PropertyOrderer propertyOrderer = getPropertyOrderer(type);
    if (propertyOrderer == null) {
      return;
    }
    final List<String> order;
    try {
      order = propertyOrderer.determinePropertyOrder();
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, e.toString());
      return;
    }
    // We expect that all the properties will be found, but if not then we won't try reordering.
    boolean allFound = true;
    for (Property property : properties) {
      allFound &= order.contains(property.toString());
    }
    if (allFound) {
      // We successfully found the abstract methods corresponding to all the properties, so now
      // reorder the List<Property> to reflect the order of the methods.
      Comparator<Property> comparator = new Comparator<Property>() {
        @Override
        public int compare(Property a, Property b) {
          String aName = a.toString();
          String bName = b.toString();
          return order.indexOf(aName) - order.indexOf(bName);
        }
      };
      Collections.sort(properties, comparator);
    }
  }

  private PropertyOrderer getPropertyOrderer(TypeElement originalType) {
    TypeElement type = eclipseHackBatchTest ? new EclipseTypeElement(originalType) : originalType;

    try {
      // If we are in Eclipse, then processingEnv will be an instance of
      // org.eclipse.jdt.internal.apt.pluggable.core.dispatch.IdeProcessingEnvImpl
      // and we can access its getEnclosingIFile method to obtain an Eclipse
      // org.eclipse.core.resources.IFile. Then we will access the
      //   String getCharset();
      // and
      //   InputStream getContents();
      // methods to access the whole source file that includes this class.
      // If the class in question has not changed since Eclipse last succeessfully compiled it
      // then the IFile will be the compiled class file rather than the source, and we will need
      // to read the order of the methods out of the class file. The method
      //    URI getRawLocationURI();
      // will tell us this because the URI will end with .class instead of .java.
      // If we are not in Eclipse then the reflection here will fail and we will return null,
      // which will mean that the caller won't try to reorder.
      Method getEnclosingIFile =
          processingEnv.getClass().getMethod("getEnclosingIFile", Element.class);
      final Object iFile = getEnclosingIFile.invoke(processingEnv, type);
      URI uri = (URI) iFile.getClass().getMethod("getRawLocationURI").invoke(iFile);
      if (uri.getPath().endsWith(".class")) {
        return new BinaryPropertyOrderer(uri);
      } else {
        Method getCharset = iFile.getClass().getMethod("getCharset");
        final String charset = (String) getCharset.invoke(iFile);
        final Method getContents = iFile.getClass().getMethod("getContents");
        Callable<Reader> readerProvider = new Callable<Reader>() {
          @Override
          public Reader call() throws Exception {
            InputStream inputStream = (InputStream) getContents.invoke(iFile);
            return new InputStreamReader(inputStream, charset);
          }
        };
        return new SourcePropertyOrderer(type, readerProvider);
      }
    } catch (Exception e) {
      // The method getRawLocationURI used above exists on the Eclipse IDE environment, but not on
      // the batch compiler environment. However, the file can also be obtained from the TypeElement
      // through the getFileName method.
      if (!type.getClass().getName().toLowerCase().contains("eclipse")) {
        // Guard against the case where a non-Eclipse type happens to have a getFileName method
        return null;
      }
      try {
        final String filename = (String) type.getClass().getMethod("getFileName").invoke(type);
        Callable<Reader> readerProvider = new Callable<Reader>() {
          @Override
          public Reader call() throws Exception {
            return new FileReader(filename);
          }
        };
        return new SourcePropertyOrderer(type, readerProvider);
      } catch (Exception e2) {
        // Reflection failed (twice), so we are presumably not in Eclipse.
        return null;
      }
    }
  }

  private interface PropertyOrderer {
    List<String> determinePropertyOrder() throws IOException;
  }

  private class SourcePropertyOrderer implements PropertyOrderer {
    private final TypeElement type;
    private final Callable<Reader> readerProvider;

    /**
     * Constructs an object that scans the source code of the given type and returns the names of
     * all abstract methods directly declared in the type (not in nested types). The type itself may
     * be nested inside another class. Returns an empty list if the order could not be determined.
     *
     * @param type The type whose source is being scanned.
     * @param readerProvider A Callable that returns a Reader that will read the source of the whole
     *     file in which the class is declared.
     */
    SourcePropertyOrderer(TypeElement type, Callable<Reader> readerProvider) {
      this.type = type;
      this.readerProvider = readerProvider;
    }

    @Override public List<String> determinePropertyOrder() throws IOException {
      Reader sourceReader;
      try {
        sourceReader = readerProvider.call();
      } catch (Exception e) {
        return Collections.emptyList();
      }
      try {
        String packageName = TypeSimplifier.packageNameOf(type);
        String className = type.getQualifiedName().toString();
        AbstractMethodExtractor extractor = new AbstractMethodExtractor();
        JavaTokenizer tokenizer = new JavaTokenizer(sourceReader);
        Map<String, List<String>> methodOrders = extractor.abstractMethods(tokenizer, packageName);
        if (methodOrders.containsKey(className)) {
          return methodOrders.get(className);
        } else {
          return Collections.emptyList();
        }
      } finally {
        sourceReader.close();
      }
    }
  }

  private class BinaryPropertyOrderer implements PropertyOrderer {
    private final URI classFileUri;

    BinaryPropertyOrderer(URI classFileUri) {
      this.classFileUri = classFileUri;
    }

    @Override
    public List<String> determinePropertyOrder() throws IOException {
      InputStream inputStream = null;
      try {
        URL classFileUrl = classFileUri.toURL();
        inputStream = classFileUrl.openStream();
        AbstractMethodLister lister = new AbstractMethodLister(inputStream);
        return lister.abstractNoArgMethods();
      } finally {
        if (inputStream != null) {
          inputStream.close();
        }
      }
    }
  }
}