/*
 * Copyright 2008 Google LLC
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
package com.google.auto.service.processor;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Processes {@link AutoService} annotations and generates the service provider configuration files
 * described in {@link java.util.ServiceLoader}.
 *
 * <p>Processor Options:
 *
 * <ul>
 *   <li>{@code -Adebug} - turns on debug statements
 *   <li>{@code -Averify=true} - turns on extra verification
 * </ul>
 */
@SupportedOptions({"debug", "verify"})
public class AutoServiceProcessor extends AbstractProcessor {

  @VisibleForTesting
  static final String MISSING_SERVICES_ERROR = "No service interfaces provided for element!";

  private final List<String> exceptionStacks = Collections.synchronizedList(new ArrayList<>());

  /**
   * Maps the class names of service provider interfaces to the class names of the concrete classes
   * which implement them.
   *
   * <p>For example, {@code "com.google.apphosting.LocalRpcService" ->
   * "com.google.apphosting.datastore.LocalDatastoreService"}
   */
  private final SortedSetMultimap<String, String> providers = TreeMultimap.create();

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoService.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   *
   *
   * <ol>
   *   <li>For each class annotated with {@link AutoService}
   *       <ul>
   *         <li>Verify the {@link AutoService} interface value is correct
   *         <li>Categorize the class by its service interface
   *       </ul>
   *   <li>For each {@link AutoService} interface
   *       <ul>
   *         <li>Create a file named {@code META-INF/services/<interface>}
   *         <li>For each {@link AutoService} annotated class for this interface
   *             <ul>
   *               <li>Create an entry in the file
   *             </ul>
   *       </ul>
   * </ol>
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      processImpl(annotations, roundEnv);
    } catch (RuntimeException e) {
      // We don't allow exceptions of any kind to propagate to the compiler
      String trace = getStackTraceAsString(e);
      exceptionStacks.add(trace);
      fatalError(trace);
    }
    return false;
  }

  ImmutableList<String> exceptionStacks() {
    return ImmutableList.copyOf(exceptionStacks);
  }

  private void processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      generateConfigFiles();
    } else {
      processAnnotations(annotations, roundEnv);
    }
  }

  private void processAnnotations(
      Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AutoService.class);

    log(annotations.toString());
    log(elements.toString());

    for (Element e : elements) {
      // TODO(gak): check for error trees?
      TypeElement providerImplementer = MoreElements.asType(e);
      AnnotationMirror annotationMirror = getAnnotationMirror(e, AutoService.class).get();
      ImmutableSet<DeclaredType> providerInterfaces = getValueFieldOfClasses(annotationMirror);
      if (providerInterfaces.isEmpty()) {
        error(MISSING_SERVICES_ERROR, e, annotationMirror);
        continue;
      }
      for (DeclaredType providerInterface : providerInterfaces) {
        TypeElement providerType = MoreTypes.asTypeElement(providerInterface);

        log("provider interface: " + providerType.getQualifiedName());
        log("provider implementer: " + providerImplementer.getQualifiedName());

        if (checkImplementer(providerImplementer, providerType, annotationMirror)) {
          providers.put(getBinaryName(providerType), getBinaryName(providerImplementer));
        } else {
          String message =
              "ServiceProviders must implement their service provider interface. "
                  + providerImplementer.getQualifiedName()
                  + " does not implement "
                  + providerType.getQualifiedName();
          error(message, e, annotationMirror);
        }
      }
    }
  }

  private void generateConfigFiles() {
    Filer filer = processingEnv.getFiler();

    for (String providerInterface : providers.keySet()) {
      String resourceFile = "META-INF/services/" + providerInterface;
      log("Working on resource file: " + resourceFile);
      try {
        SortedSet<String> newServices = providers.get(providerInterface);

        log("New service file contents: " + newServices);
        FileObject fileObject =
            filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);
        try (OutputStream out = fileObject.openOutputStream()) {
          ServicesFiles.writeServiceFile(newServices, out);
        }
        log("Wrote to: " + resourceFile);
      } catch (IOException e) {
        fatalError("Unable to create " + resourceFile + ", " + getStackTraceAsString(e));
        return;
      }
    }
  }

  /**
   * Verifies {@link ServiceProvider} constraints on the concrete provider class. Note that these
   * constraints are enforced at runtime via the ServiceLoader, we're just checking them at compile
   * time to be extra nice to our users.
   */
  private boolean checkImplementer(
      TypeElement providerImplementer,
      TypeElement providerType,
      AnnotationMirror annotationMirror) {

    if (!Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("verify", "true"))
        || suppresses(providerImplementer, "AutoService")) {
      return true;
    }

    // We check that providerImplementer does indeed inherit from providerType, and that it is not
    // abstract (an abstract class or interface). For ServiceLoader, we could also check that it has
    // a public no-arg constructor. But it turns out that people also use AutoService in contexts
    // where the META-INF/services entries are read by things other than ServiceLoader. Those things
    // still require the class to exist and inherit from providerType, but they don't necessarily
    // require a public no-arg constructor.
    // More background: https://github.com/google/auto/issues/1505.

    Types types = processingEnv.getTypeUtils();

    if (types.isSubtype(providerImplementer.asType(), providerType.asType())) {
      return checkNotAbstract(providerImplementer, annotationMirror);
    }

    // Maybe the provider has generic type, but the argument to @AutoService can't be generic.
    // So we allow that with a warning, which can be suppressed with @SuppressWarnings("rawtypes").
    // See https://github.com/google/auto/issues/870.
    if (types.isSubtype(providerImplementer.asType(), types.erasure(providerType.asType()))) {
      if (!suppresses(providerImplementer, "rawtypes")) {
        warning(
            "Service provider "
                + providerType
                + " is generic, so it can't be named exactly by @AutoService."
                + " If this is OK, add @SuppressWarnings(\"rawtypes\").",
            providerImplementer,
            annotationMirror);
      }
      return checkNotAbstract(providerImplementer, annotationMirror);
    }

    String message =
        "ServiceProviders must implement their service provider interface. "
            + providerImplementer.getQualifiedName()
            + " does not implement "
            + providerType.getQualifiedName();
    error(message, providerImplementer, annotationMirror);

    return false;
  }

  private boolean checkNotAbstract(
      TypeElement providerImplementer, AnnotationMirror annotationMirror) {
    if (providerImplementer.getModifiers().contains(Modifier.ABSTRACT)) {
      error(
          "@AutoService can only be applied to a concrete class",
          providerImplementer,
          annotationMirror);
      return false;
    }
    return true;
  }

  private static boolean suppresses(Element element, String warning) {
    for (; element != null; element = element.getEnclosingElement()) {
      SuppressWarnings suppress = element.getAnnotation(SuppressWarnings.class);
      if (suppress != null && Arrays.asList(suppress.value()).contains(warning)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the binary name of a reference type. For example, {@code com.google.Foo$Bar}, instead
   * of {@code com.google.Foo.Bar}.
   */
  private String getBinaryName(TypeElement element) {
    return getBinaryNameImpl(element, element.getSimpleName().toString());
  }

  private String getBinaryNameImpl(TypeElement element, String className) {
    Element enclosingElement = element.getEnclosingElement();

    if (enclosingElement instanceof PackageElement) {
      PackageElement pkg = MoreElements.asPackage(enclosingElement);
      if (pkg.isUnnamed()) {
        return className;
      }
      return pkg.getQualifiedName() + "." + className;
    }

    TypeElement typeElement = MoreElements.asType(enclosingElement);
    return getBinaryNameImpl(typeElement, typeElement.getSimpleName() + "$" + className);
  }

  /**
   * Returns the contents of a {@code Class[]}-typed "value" field in a given {@code
   * annotationMirror}.
   */
  private ImmutableSet<DeclaredType> getValueFieldOfClasses(AnnotationMirror annotationMirror) {
    return getAnnotationValue(annotationMirror, "value")
        .accept(
            new SimpleAnnotationValueVisitor8<ImmutableSet<DeclaredType>, Void>(ImmutableSet.of()) {
              @Override
              public ImmutableSet<DeclaredType> visitType(TypeMirror typeMirror, Void v) {
                // TODO(ronshapiro): class literals may not always be declared types, i.e.
                // int.class, int[].class
                return ImmutableSet.of(MoreTypes.asDeclared(typeMirror));
              }

              @Override
              public ImmutableSet<DeclaredType> visitArray(
                  List<? extends AnnotationValue> values, Void v) {
                return values.stream()
                    .flatMap(value -> value.accept(this, null).stream())
                    .collect(toImmutableSet());
              }
            },
            null);
  }

  private void log(String msg) {
    if (processingEnv.getOptions().containsKey("debug")) {
      processingEnv.getMessager().printMessage(Kind.NOTE, msg);
    }
  }

  private void warning(String msg, Element element, AnnotationMirror annotation) {
    processingEnv.getMessager().printMessage(Kind.WARNING, msg, element, annotation);
  }

  private void error(String msg, Element element, AnnotationMirror annotation) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element, annotation);
  }

  private void fatalError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, "FATAL ERROR: " + msg);
  }
}
