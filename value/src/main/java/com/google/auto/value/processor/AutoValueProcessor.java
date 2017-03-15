/*
 * Copyright (C) 2012 Google, Inc.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.Sets.union;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.beans.Introspector;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see AutoValue
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
public class AutoValueProcessor extends AbstractProcessor {
  public AutoValueProcessor() {
    this(AutoValueProcessor.class.getClassLoader());
  }

  @VisibleForTesting
  AutoValueProcessor(ClassLoader loaderForExtensions) {
    this.extensions = null;
    this.loaderForExtensions = loaderForExtensions;
  }

  @VisibleForTesting
  public AutoValueProcessor(Iterable<? extends AutoValueExtension> extensions) {
    this.extensions = ImmutableList.<AutoValueExtension>copyOf(extensions);
    this.loaderForExtensions = null;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoValue.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Used to test whether a fully-qualified name is AutoValue.class.getCanonicalName() or one of its
   * nested annotations.
   */
  private static final Pattern AUTO_VALUE_CLASSNAME_PATTERN =
      Pattern.compile(Pattern.quote(AutoValue.class.getCanonicalName()) + "(\\..*)?");

  private ErrorReporter errorReporter;

  /**
   * Qualified names of {@code @AutoValue} classes that we attempted to process but had to abandon
   * because we needed other types that they referenced and those other types were missing.
   */
  private final List<String> deferredTypeNames = new ArrayList<String>();

  // Depending on how this AutoValueProcessor was constructed, we might already have a list of
  // extensions when init() is run, or, if `extensions` is null, we have a ClassLoader that will be
  // used to get the list using the ServiceLoader API.
  private ImmutableList<AutoValueExtension> extensions;
  private final ClassLoader loaderForExtensions;

  private Types typeUtils;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    errorReporter = new ErrorReporter(processingEnv);
    typeUtils = processingEnv.getTypeUtils();

    if (extensions == null) {
      try {
        extensions = ImmutableList.copyOf(
            ServiceLoader.load(AutoValueExtension.class, loaderForExtensions));
        // ServiceLoader.load returns a lazily-evaluated Iterable, so evaluate it eagerly now
        // to discover any exceptions.
      } catch (Throwable t) {
        StringBuilder warning = new StringBuilder();
        warning.append(
            "An exception occurred while looking for AutoValue extensions. "
                + "No extensions will function.");
        if (t instanceof ServiceConfigurationError) {
          warning.append(" This may be due to a corrupt jar file in the compiler's classpath.");
        }
        warning.append(" Exception: ").append(t);
        errorReporter.reportWarning(warning.toString(), null);
        extensions = ImmutableList.of();
      }
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    List<TypeElement> deferredTypes = new ArrayList<TypeElement>();
    for (String deferred : deferredTypeNames) {
      deferredTypes.add(processingEnv.getElementUtils().getTypeElement(deferred));
    }
    if (roundEnv.processingOver()) {
      // This means that the previous round didn't generate any new sources, so we can't have found
      // any new instances of @AutoValue; and we can't have any new types that are the reason a type
      // was in deferredTypes.
      for (TypeElement type : deferredTypes) {
        errorReporter.reportError("Did not generate @AutoValue class for " + type.getQualifiedName()
            + " because it references undefined types", type);
      }
      return false;
    }
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(AutoValue.class);
    List<TypeElement> types = new ImmutableList.Builder<TypeElement>()
        .addAll(deferredTypes)
        .addAll(ElementFilter.typesIn(annotatedElements))
        .build();
    deferredTypeNames.clear();
    for (TypeElement type : types) {
      try {
        processType(type);
      } catch (AbortProcessingException e) {
        // We abandoned this type; continue with the next.
      } catch (MissingTypeException e) {
        // We abandoned this type, but only because we needed another type that it references and
        // that other type was missing. It is possible that the missing type will be generated by
        // further annotation processing, so we will try again on the next round (perhaps failing
        // again and adding it back to the list). We save the name of the @AutoValue type rather
        // than its TypeElement because it is not guaranteed that it will be represented by
        // the same TypeElement on the next round.
        deferredTypeNames.add(type.getQualifiedName().toString());
      } catch (RuntimeException e) {
        // Don't propagate this exception, which will confusingly crash the compiler.
        // Instead, report a compiler error with the stack trace.
        String trace = Throwables.getStackTraceAsString(e);
        errorReporter.reportError("@AutoValue processor threw an exception: " + trace, type);
      }
    }
    return false;  // never claim annotation, because who knows what other processors want?
  }

  private String generatedClassName(TypeElement type, String prefix) {
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = (TypeElement) type.getEnclosingElement();
      name = type.getSimpleName() + "_" + name;
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    String dot = pkg.isEmpty() ? "" : ".";
    return pkg + dot + prefix + name;
  }

  private String generatedSubclassName(TypeElement type, int depth) {
    return generatedClassName(type, Strings.repeat("$", depth) + "AutoValue_");
  }

  /**
   * A property of an {@code @AutoValue} class, defined by one of its abstract methods.
   * An instance of this class is made available to the Velocity template engine for
   * each property. The public methods of this class define JavaBeans-style properties
   * that are accessible from templates. For example {@link #getType()} means we can
   * write {@code $p.type} for a Velocity variable {@code $p} that is a {@code Property}.
   */
  public static class Property {
    private final String name;
    private final String identifier;
    private final ExecutableElement method;
    private final String type;
    private final ImmutableList<String> annotations;
    private final Optionalish optional;

    Property(
        String name,
        String identifier,
        ExecutableElement method,
        String type,
        TypeSimplifier typeSimplifier,
        ImmutableSet<String> excludedAnnotations) {
      this.name = name;
      this.identifier = identifier;
      this.method = method;
      this.type = type;
      this.annotations = buildAnnotations(typeSimplifier, excludedAnnotations);
      TypeMirror propertyType = method.getReturnType();
      this.optional =
          Optionalish.createIfOptional(propertyType, typeSimplifier.simplifyRaw(propertyType));
    }

    private ImmutableList<String> buildAnnotations(
        TypeSimplifier typeSimplifier, ImmutableSet<String> excludedAnnotations) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();

      builder.addAll(copyAnnotations(method, typeSimplifier, excludedAnnotations));

      for (AnnotationMirror annotationMirror :
          Java8Support.getAnnotationMirrors(method.getReturnType())) {
        AnnotationOutput annotationOutput = new AnnotationOutput(typeSimplifier);
        builder.add(annotationOutput.sourceFormForAnnotation(annotationMirror));
      }
      return builder.build();
    }

    /**
     * Returns the name of the property as it should be used when declaring identifiers (fields and
     * parameters). If the original getter method was {@code foo()} then this will be {@code foo}.
     * If it was {@code getFoo()} then it will be {@code foo}. If it was {@code getPackage()} then
     * it will be something like {@code package0}, since {@code package} is a reserved word.
     */
    @Override
    public String toString() {
      return identifier;
    }

    /**
     * Returns the name of the property as it should be used in strings visible to users. This is
     * usually the same as {@code toString()}, except that if we had to use an identifier like
     * "package0" because "package" is a reserved word, the name here will be the original
     * "package".
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the name of the getter method for this property as defined by the {@code @AutoValue}
     * class. For property {@code foo}, this will be {@code foo} or {@code getFoo} or {@code isFoo}.
     */
    public String getGetter() {
      return method.getSimpleName().toString();
    }

    TypeElement getOwner() {
      return (TypeElement) method.getEnclosingElement();
    }

    public TypeMirror getTypeMirror() {
      return method.getReturnType();
    }

    public String getType() {
      return type;
    }

    public TypeKind getKind() {
      return method.getReturnType().getKind();
    }

    public List<String> getAnnotations() {
      return annotations;
    }

    /**
     * Returns an {@link Optionalish} representing the kind of Optional that this property's type
     * is, or null if the type is not an Optional of any kind.
     */
    public Optionalish getOptional() {
      return optional;
    }

    /**
     * Returns the string to use as an annotation to indicate the nullability of this property.
     * It is either the empty string, if the property is not nullable, or an annotation string
     * with a trailing space, such as {@code "@Nullable "} or {@code "@javax.annotation.Nullable "}.
     */
    public String getNullableAnnotation() {
      for (String annotationString : annotations) {
        if (annotationString.equals("@Nullable") || annotationString.endsWith(".Nullable")) {
          return annotationString + " ";
        }
      }
      return "";
    }

    public boolean isNullable() {
      return !getNullableAnnotation().isEmpty();
    }

    public String getAccess() {
      return access(method);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Property && ((Property) obj).method.equals(method);
    }

    @Override
    public int hashCode() {
      return method.hashCode();
    }
  }

  /**
   * A basic method on an @AutoValue class with no specific attached information, such as a {@code
   * toBuilder()} method, or a {@code build()} method, where only the name and access type is needed
   * in context.
   *
   * <p>It implements JavaBean-style getters akin to {@link Property}.
   */
  public static class SimpleMethod {
    private final String access;
    private final String name;

    SimpleMethod(ExecutableElement method) {
      this.access = access(method);
      this.name = method.getSimpleName().toString();
    }

    public String getAccess() {
      return access;
    }

    public String getName() {
      return name;
    }
  }

  static String access(ExecutableElement method) {
    Set<Modifier> mods = method.getModifiers();
    if (mods.contains(Modifier.PUBLIC)) {
      return "public ";
    } else if (mods.contains(Modifier.PROTECTED)) {
      return "protected ";
    } else {
      return "";
    }
  }

  private static boolean isJavaLangObject(TypeElement type) {
    return type.getSuperclass().getKind() == TypeKind.NONE && type.getKind() == ElementKind.CLASS;
  }

  private enum ObjectMethodToOverride {
    NONE, TO_STRING, EQUALS, HASH_CODE
  }

  private static ObjectMethodToOverride objectMethodToOverride(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    switch (method.getParameters().size()) {
      case 0:
        if (name.equals("toString")) {
          return ObjectMethodToOverride.TO_STRING;
        } else if (name.equals("hashCode")) {
          return ObjectMethodToOverride.HASH_CODE;
        }
        break;
      case 1:
        if (name.equals("equals")
            && method.getParameters().get(0).asType().toString().equals("java.lang.Object")) {
          return ObjectMethodToOverride.EQUALS;
        }
        break;
      default:
        // No relevant Object methods have more than one parameter.
    }
    return ObjectMethodToOverride.NONE;
  }

  private void processType(TypeElement type) {
    AutoValue autoValue = type.getAnnotation(AutoValue.class);
    if (autoValue == null) {
      // This shouldn't happen unless the compilation environment is buggy,
      // but it has happened in the past and can crash the compiler.
      errorReporter.abortWithError("annotation processor for @AutoValue was invoked with a type"
          + " that does not have that annotation; this is probably a compiler bug", type);
    }
    if (type.getKind() != ElementKind.CLASS) {
      errorReporter.abortWithError(
          "@" + AutoValue.class.getName() + " only applies to classes", type);
    }
    if (ancestorIsAutoValue(type)) {
      errorReporter.abortWithError("One @AutoValue class may not extend another", type);
    }
    if (implementsAnnotation(type)) {
      errorReporter.abortWithError("@AutoValue may not be used to implement an annotation"
          + " interface; try using @AutoAnnotation instead", type);
    }
    checkModifiersIfNested(type);

    // We are going to classify the methods of the @AutoValue class into several categories.
    // This covers the methods in the class itself and the ones it inherits from supertypes.
    // First, the only concrete (non-abstract) methods we are interested in are overrides of
    // Object methods (equals, hashCode, toString), which signal that we should not generate
    // an implementation of those methods.
    // Then, each abstract method is one of the following:
    // (1) A property getter, like "abstract String foo()" or "abstract String getFoo()".
    // (2) A toBuilder() method, which is any abstract no-arg method returning the Builder for
    //     this @AutoValue class.
    // (3) An abstract method that will be consumed by an extension, such as
    //     Parcelable.describeContents() or Parcelable.writeToParcel(Parcel, int).
    // The describeContents() example shows a quirk here: initially we will identify it as a
    // property, which means that we need to reconstruct the list of properties after allowing
    // extensions to consume abstract methods.
    // If there are abstract methods that don't fit any of the categories above, that is an error
    // which we signal explicitly to avoid confusion.

    ImmutableSet<ExecutableElement> methods = getLocalAndInheritedMethods(
        type, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
    ImmutableSet<ExecutableElement> abstractMethods = abstractMethodsIn(methods);

    BuilderSpec builderSpec = new BuilderSpec(type, processingEnv, errorReporter);
    Optional<BuilderSpec.Builder> builder = builderSpec.getBuilder();
    ImmutableSet<ExecutableElement> toBuilderMethods;
    if (builder.isPresent()) {
      toBuilderMethods = builder.get().toBuilderMethods(typeUtils, abstractMethods);
    } else {
      toBuilderMethods = ImmutableSet.of();
    }

    ImmutableSet<ExecutableElement> propertyMethods =
        propertyMethodsIn(immutableSetDifference(abstractMethods, toBuilderMethods));
    ImmutableBiMap<String, ExecutableElement> properties = propertyNameToMethodMap(propertyMethods);

    ExtensionContext context =
        new ExtensionContext(processingEnv, type, properties, abstractMethods);
    ImmutableList<AutoValueExtension> applicableExtensions = applicableExtensions(type, context);
    ImmutableSet<ExecutableElement> consumedMethods = methodsConsumedByExtensions(
        type, applicableExtensions, context, abstractMethods, properties);

    if (!consumedMethods.isEmpty()) {
      ImmutableSet<ExecutableElement> allAbstractMethods = abstractMethods;
      abstractMethods = immutableSetDifference(abstractMethods, consumedMethods);
      toBuilderMethods = immutableSetDifference(toBuilderMethods, consumedMethods);
      propertyMethods =
          propertyMethodsIn(immutableSetDifference(abstractMethods, toBuilderMethods));
      properties = propertyNameToMethodMap(propertyMethods);
      context = new ExtensionContext(processingEnv, type, properties, allAbstractMethods);
    }

    boolean extensionsPresent = !applicableExtensions.isEmpty();
    validateMethods(type, abstractMethods, toBuilderMethods, propertyMethods, extensionsPresent);

    String finalSubclass = generatedSubclassName(type, 0);
    AutoValueTemplateVars vars = new AutoValueTemplateVars();
    vars.pkg = TypeSimplifier.packageNameOf(type);
    vars.origClass = TypeSimplifier.classNameOf(type);
    vars.simpleClassName = TypeSimplifier.simpleNameOf(vars.origClass);
    vars.finalSubclass = TypeSimplifier.simpleNameOf(finalSubclass);
    vars.types = processingEnv.getTypeUtils();
    determineObjectMethodsToGenerate(methods, vars);
    TypeSimplifier typeSimplifier =
        defineVarsForType(type, vars, toBuilderMethods, propertyMethods, builder);

    // Only copy annotations from a class if it has @AutoValue.CopyAnnotations.
    if (isAnnotationPresent(type, AutoValue.CopyAnnotations.class)) {
      Set<String> excludedAnnotations =
          union(
              getFieldOfClasses(
                  type,
                  AutoValue.CopyAnnotations.class,
                  "exclude",
                  processingEnv.getElementUtils()),
              getAnnotationsMarkedWithInherited(type));

      vars.annotations = copyAnnotations(type, typeSimplifier, excludedAnnotations);
    } else {
      vars.annotations = ImmutableList.of();
    }

    GwtCompatibility gwtCompatibility = new GwtCompatibility(type);
    vars.gwtCompatibleAnnotation = gwtCompatibility.gwtCompatibleAnnotationString();

    int subclassDepth = writeExtensions(type, context, applicableExtensions);
    String subclass = generatedSubclassName(type, subclassDepth);
    vars.subclass = TypeSimplifier.simpleNameOf(subclass);
    vars.isFinal = (subclassDepth == 0);

    String text = vars.toText();
    text = Reformatter.fixup(text);
    writeSourceFile(subclass, text, type);
    GwtSerialization gwtSerialization = new GwtSerialization(gwtCompatibility, processingEnv, type);
    gwtSerialization.maybeWriteGwtSerializer(vars);
  }

  /** Implements the semantics of {@link AutoValue.CopyAnnotations}; see its javadoc. */
  private static ImmutableList<String> copyAnnotations(
      Element type, TypeSimplifier typeSimplifier, Set<String> excludedAnnotations) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    AnnotationOutput annotationOutput = new AnnotationOutput(typeSimplifier);
    for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
      String annotationFqName = getAnnotationFqName(annotation);
      if (AUTO_VALUE_CLASSNAME_PATTERN.matcher(annotationFqName).matches()) {
        // Skip AutoValue itself (and any of its nested annotations).
        continue;
      }
      if (!excludedAnnotations.contains(annotationFqName)) {
        result.add(annotationOutput.sourceFormForAnnotation(annotation));
      }
    }

    return result.build();
  }

  /**
   * Returns the fully-qualified name of an annotation-mirror, e.g.
   * "com.google.auto.value.AutoValue".
   */
  private static String getAnnotationFqName(AnnotationMirror annotation) {
    return ((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName().toString();
  }

  /**
   * Returns the contents of a {@code Class[]}-typed field in an annotation.
   *
   * <p>This method is needed because directly reading the value of such a field from an
   * AnnotationMirror throws: <pre>
   * javax.lang.model.type.MirroredTypeException: Attempt to access Class object for TypeMirror Foo.
   * </pre>
   *
   * @param element The element on which the annotation is present. e.g. the class being processed
   *     by AutoValue.
   * @param annotation The class of the annotation to read from., e.g. {@link
   *     AutoValue.CopyAnnotations}.
   * @param fieldName The name of the field to read, e.g. "exclude".
   * @return a set of fully-qualified names of classes appearing in 'fieldName' on 'annotation' on
   *     'element'.
   */
  private ImmutableSet<String> getFieldOfClasses(
      Element element,
      Class<? extends Annotation> annotation,
      String fieldName,
      Elements elementUtils) {
    TypeElement annotationElement = elementUtils.getTypeElement(annotation.getCanonicalName());
    if (annotationElement == null) {
      // This can happen if the annotation is on the -processorpath but not on the -classpath.
      return ImmutableSet.of();
    }
    TypeMirror annotationMirror = annotationElement.asType();

    for (AnnotationMirror annot : element.getAnnotationMirrors()) {
      if (!typeUtils.isSameType(annot.getAnnotationType(), annotationMirror)) {
        continue;
      }
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
          annot.getElementValues().entrySet()) {
        if (fieldName.contentEquals(entry.getKey().getSimpleName())) {
          ImmutableSet.Builder<String> result = ImmutableSet.builder();

          @SuppressWarnings("unchecked")
          List<AnnotationValue> annotationsToCopy =
              (List<AnnotationValue>) entry.getValue().getValue();
          for (AnnotationValue annotationValue : annotationsToCopy) {
            String qualifiedName =
                ((TypeElement) ((DeclaredType) annotationValue.getValue()).asElement())
                    .getQualifiedName()
                    .toString();
            result.add(qualifiedName);
          }
          return result.build();
        }
      }
    }
    return ImmutableSet.of();
  }

  private ImmutableSet<String> getAnnotationsMarkedWithInherited(Element element) {
    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (isAnnotationPresent(annotation.getAnnotationType().asElement(), Inherited.class)) {
        result.add(getAnnotationFqName(annotation));
      }
    }
    return result.build();
  }

  // Invokes each of the given extensions to generate its subclass, and returns the number of
  // hierarchy classes that extensions generated. This number is then the number of $ characters
  // that should precede the name of the AutoValue implementation class.
  // Assume the @AutoValue class is com.example.Foo.Bar. Then if there are no
  // extensions the returned value will be 0, so the AutoValue implementation will be
  // com.example.AutoValue_Foo_Bar. If there is one extension, it will be asked to
  // generate AutoValue_Foo_Bar with parent $AutoValue_Foo_Bar. If it does so (returns
  // non-null) then the returned value will be 1, so the AutoValue implementation will be
  // com.example.$AutoValue_Foo_Bar. Otherwise, the returned value will still be 0. Likewise,
  // if there is a second extension and both extensions return non-null, the first one will
  // generate AutoValue_Foo_Bar with parent $AutoValue_Foo_Bar, the second will generate
  // $AutoValue_Foo_Bar with parent $$AutoValue_Foo_Bar, and the returned value will be 2 for
  // com.example.$$AutoValue_Foo_Bar.
  private int writeExtensions(
      TypeElement type,
      ExtensionContext context,
      ImmutableList<AutoValueExtension> applicableExtensions) {
    int writtenSoFar = 0;
    for (AutoValueExtension extension : applicableExtensions) {
      String parentFqName = generatedSubclassName(type, writtenSoFar + 1);
      String parentSimpleName = TypeSimplifier.simpleNameOf(parentFqName);
      String classFqName = generatedSubclassName(type, writtenSoFar);
      String classSimpleName = TypeSimplifier.simpleNameOf(classFqName);
      boolean isFinal = (writtenSoFar == 0);
      String source = extension.generateClass(context, classSimpleName, parentSimpleName, isFinal);
      if (source != null) {
        source = Reformatter.fixup(source);
        writeSourceFile(classFqName, source, type);
        writtenSoFar++;
      }
    }
    return writtenSoFar;
  }

  private ImmutableList<AutoValueExtension> applicableExtensions(
      TypeElement type, ExtensionContext context) {
    List<AutoValueExtension> applicableExtensions = Lists.newArrayList();
    List<AutoValueExtension> finalExtensions = Lists.newArrayList();
    for (AutoValueExtension extension : extensions) {
      if (extension.applicable(context)) {
        if (extension.mustBeFinal(context)) {
          finalExtensions.add(extension);
        } else {
          applicableExtensions.add(extension);
        }
      }
    }
    switch (finalExtensions.size()) {
      case 0:
        break;
      case 1:
        applicableExtensions.add(0, finalExtensions.get(0));
        break;
      default:
        errorReporter.reportError(
            "More than one extension wants to generate the final class: "
                + FluentIterable.from(finalExtensions).transform(ExtensionName.INSTANCE)
                    .join(Joiner.on(", ")),
            type);
        break;
    }
    return ImmutableList.copyOf(applicableExtensions);
  }

  private ImmutableSet<ExecutableElement> methodsConsumedByExtensions(
      TypeElement type,
      ImmutableList<AutoValueExtension> applicableExtensions,
      ExtensionContext context,
      ImmutableSet<ExecutableElement> abstractMethods,
      ImmutableBiMap<String, ExecutableElement> properties) {
    Set<ExecutableElement> consumed = Sets.newHashSet();
    for (AutoValueExtension extension : applicableExtensions) {
      Set<ExecutableElement> consumedHere = Sets.newHashSet();
      for (String consumedProperty : extension.consumeProperties(context)) {
        ExecutableElement propertyMethod = properties.get(consumedProperty);
        if (propertyMethod == null) {
          errorReporter.reportError(
              "Extension " + extensionName(extension)
                  + " wants to consume a property that does not exist: " + consumedProperty,
              type);
        } else {
          consumedHere.add(propertyMethod);
        }
      }
      for (ExecutableElement consumedMethod : extension.consumeMethods(context)) {
        if (!abstractMethods.contains(consumedMethod)) {
          errorReporter.reportError(
              "Extension " + extensionName(extension)
                  + " wants to consume a method that is not one of the abstract methods in this"
                  + " class: " + consumedMethod,
              type);
        } else {
          consumedHere.add(consumedMethod);
        }
      }
      for (ExecutableElement repeat : Sets.intersection(consumed, consumedHere)) {
        errorReporter.reportError(
            "Extension " + extensionName(extension) + " wants to consume a method that was already"
                + " consumed by another extension", repeat);
      }
      consumed.addAll(consumedHere);
    }
    return ImmutableSet.copyOf(consumed);
  }

  private void validateMethods(
      TypeElement type,
      ImmutableSet<ExecutableElement> abstractMethods,
      ImmutableSet<ExecutableElement> toBuilderMethods,
      ImmutableSet<ExecutableElement> propertyMethods,
      boolean extensionsPresent) {
    boolean ok = true;
    for (ExecutableElement method : abstractMethods) {
      if (propertyMethods.contains(method)) {
        ok &= checkReturnType(type, method);
      } else if (!toBuilderMethods.contains(method)
          && objectMethodToOverride(method) == ObjectMethodToOverride.NONE) {
        // This could reasonably be an error, were it not for an Eclipse bug in
        // ElementUtils.override that sometimes fails to recognize that one method overrides
        // another, and therefore leaves us with both an abstract method and the subclass method
        // that overrides it. This shows up in AutoValueTest.LukesBase for example.
        String message = "Abstract method is neither a property getter nor a Builder converter";
        if (extensionsPresent) {
          message += ", and no extension consumed it";
        }
        errorReporter.reportWarning(message, method);
      }
    }
    if (!ok) {
      throw new AbortProcessingException();
    }
  }

  private static String extensionName(AutoValueExtension extension) {
    return extension.getClass().getName();
  }

  private enum ExtensionName implements Function<AutoValueExtension, String> {
    INSTANCE;

    @Override public String apply(AutoValueExtension input) {
      return extensionName(input);
    }
  }

  private enum SimpleMethodFunction implements Function<ExecutableElement, SimpleMethod> {
    INSTANCE;

    @Override
    public SimpleMethod apply(ExecutableElement input) {
      return new SimpleMethod(input);
    }
  }

  private TypeSimplifier defineVarsForType(
      TypeElement type,
      AutoValueTemplateVars vars,
      ImmutableSet<ExecutableElement> toBuilderMethods,
      ImmutableSet<ExecutableElement> propertyMethods,
      Optional<BuilderSpec.Builder> builder) {
    DeclaredType declaredType = MoreTypes.asDeclared(type.asType());
    Set<TypeMirror> types = new TypeMirrorSet();
    types.addAll(returnTypesOf(propertyMethods));
    if (builder.isPresent()) {
      types.addAll(builder.get().referencedTypes());
    }
    TypeElement generatedTypeElement =
        processingEnv.getElementUtils().getTypeElement("javax.annotation.Generated");
    if (generatedTypeElement != null) {
      types.add(generatedTypeElement.asType());
    }
    TypeMirror javaUtilArrays = getTypeMirror(Arrays.class);
    if (containsArrayType(types)) {
      // If there are array properties then we will be referencing java.util.Arrays.
      // Arrange to import it unless that would introduce ambiguity.
      types.add(javaUtilArrays);
    }
    vars.toBuilderMethods =
        FluentIterable.from(toBuilderMethods).transform(SimpleMethodFunction.INSTANCE).toList();
    ImmutableSetMultimap<ExecutableElement, String> excludedAnnotationsMap =
        allMethodExcludedAnnotations(propertyMethods);
    types.addAll(allMethodAnnotationTypes(propertyMethods, excludedAnnotationsMap));
    String pkg = TypeSimplifier.packageNameOf(type);
    TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtils, pkg, types, declaredType);
    vars.imports = typeSimplifier.typesToImport();
    vars.generated = generatedTypeElement == null
        ? ""
        : typeSimplifier.simplify(generatedTypeElement.asType());
    vars.arrays = typeSimplifier.simplify(javaUtilArrays);
    ImmutableBiMap<ExecutableElement, String> methodToPropertyName =
        propertyNameToMethodMap(propertyMethods).inverse();
    Map<ExecutableElement, String> methodToIdentifier = Maps.newLinkedHashMap(methodToPropertyName);
    fixReservedIdentifiers(methodToIdentifier);
    List<Property> props = new ArrayList<Property>();
    EclipseHack eclipseHack = eclipseHack();
    ImmutableMap<ExecutableElement, TypeMirror> returnTypes =
        eclipseHack.methodReturnTypes(propertyMethods, declaredType);
    for (ExecutableElement method : propertyMethods) {
      TypeMirror returnType = returnTypes.get(method);
      String propertyType = typeSimplifier.simplify(returnType);
      String propertyName = methodToPropertyName.get(method);
      String identifier = methodToIdentifier.get(method);
      ImmutableSet<String> excludedAnnotations =
          ImmutableSet.<String>builder()
              .addAll(excludedAnnotationsMap.get(method))
              .add(Override.class.getCanonicalName())
              .build();
      Property p =
          new Property(
              propertyName, identifier, method, propertyType, typeSimplifier, excludedAnnotations);
      props.add(p);
      if (p.isNullable() && returnType.getKind().isPrimitive()) {
        errorReporter.reportError("Primitive types cannot be @Nullable", method);
      }
    }
    // If we are running from Eclipse, undo the work of its compiler which sorts methods.
    eclipseHack.reorderProperties(props);
    vars.props = ImmutableSet.copyOf(props);
    vars.serialVersionUID = getSerialVersionUID(type);
    vars.formalTypes = typeSimplifier.formalTypeParametersString(type);
    vars.actualTypes = TypeSimplifier.actualTypeParametersString(type);
    vars.wildcardTypes = wildcardTypeParametersString(type);
    // Check for @AutoValue.Builder and add appropriate variables if it is present.
    if (builder.isPresent()) {
      builder.get().defineVars(vars, typeSimplifier, methodToPropertyName);
    }
    return typeSimplifier;
  }

  private ImmutableSetMultimap<ExecutableElement, String> allMethodExcludedAnnotations(
      Iterable<ExecutableElement> methods) {
    ImmutableSetMultimap.Builder<ExecutableElement, String> result = ImmutableSetMultimap.builder();
    for (ExecutableElement method : methods) {
      result.putAll(
          method,
          getFieldOfClasses(
              method, AutoValue.CopyAnnotations.class, "exclude", processingEnv.getElementUtils()));
    }
    return result.build();
  }

  private ImmutableBiMap<String, ExecutableElement> propertyNameToMethodMap(
      Set<ExecutableElement> propertyMethods) {
    Map<String, ExecutableElement> map = Maps.newLinkedHashMap();
    boolean allPrefixed = gettersAllPrefixed(propertyMethods);
    for (ExecutableElement method : propertyMethods) {
      String methodName = method.getSimpleName().toString();
      String name = allPrefixed ? nameWithoutPrefix(methodName) : methodName;
      Object old = map.put(name, method);
      if (old != null) {
        errorReporter.reportError("More than one @AutoValue property called " + name, method);
      }
    }
    return ImmutableBiMap.copyOf(map);
  }

  private static boolean gettersAllPrefixed(Set<ExecutableElement> methods) {
    return prefixedGettersIn(methods).size() == methods.size();
  }

  static ImmutableSet<ExecutableElement> prefixedGettersIn(Iterable<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> getters = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      String name = method.getSimpleName().toString();
      // TODO(emcmanus): decide whether getfoo() (without a capital) is a getter. Currently it is.
      boolean get = name.startsWith("get") && !name.equals("get");
      boolean is = name.startsWith("is") && !name.equals("is")
          && method.getReturnType().getKind() == TypeKind.BOOLEAN;
      if (get || is) {
        getters.add(method);
      }
    }
    return getters.build();
  }

  /**
   * Returns all method annotations that should be imported in the generated class. Doesn't include
   * AutoValue itself (and its nested annotations), any @Inherited annotations, and anything that's
   * in excludedAnnotationsMap.
   */
  private Set<TypeMirror> allMethodAnnotationTypes(
      Iterable<ExecutableElement> methods,
      ImmutableSetMultimap<ExecutableElement, String> excludedAnnotationsMap) {
    Set<TypeMirror> annotationTypes = new TypeMirrorSet();
    for (ExecutableElement method : methods) {
      ImmutableSet<String> excludedAnnotations = excludedAnnotationsMap.get(method);
      for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
        String annotationFqName = getAnnotationFqName(annotationMirror);
        if (excludedAnnotations.contains(annotationFqName)) {
          continue;
        }
        if (isAnnotationPresent(
            annotationMirror.getAnnotationType().asElement(), Inherited.class)) {
          continue;
        }
        if (AUTO_VALUE_CLASSNAME_PATTERN.matcher(annotationFqName).matches()) {
          continue;
        }
        annotationTypes.add(annotationMirror.getAnnotationType());
      }
      for (AnnotationMirror annotationMirror :
          Java8Support.getAnnotationMirrors(method.getReturnType())) {
        annotationTypes.add(annotationMirror.getAnnotationType());
      }
    }
    return annotationTypes;
  }

  /**
   * Returns the name of the property defined by the given getter. A getter called {@code getFoo()}
   * or {@code isFoo()} defines a property called {@code foo}. For consistency with JavaBeans, a
   * getter called {@code getHTMLPage()} defines a property called {@code HTMLPage}. The
   * <a href="https://docs.oracle.com/javase/8/docs/api/java/beans/Introspector.html#decapitalize-java.lang.String-">
   * rule</a> is: the name of the property is the part after {@code get} or {@code is}, with the
   * first letter lowercased <i>unless</i> the first two letters are uppercase. This works well
   * for the {@code HTMLPage} example, but in these more enlightened times we use {@code HtmlPage}
   * anyway, so the special behaviour is not useful, and of course it behaves poorly with examples
   * like {@code OAuth}.
   */
  private String nameWithoutPrefix(String name) {
    if (name.startsWith("get")) {
      name = name.substring(3);
    } else {
      assert name.startsWith("is");
      name = name.substring(2);
    }
    return Introspector.decapitalize(name);
  }

  private void checkModifiersIfNested(TypeElement type) {
    ElementKind enclosingKind = type.getEnclosingElement().getKind();
    if (enclosingKind.isClass() || enclosingKind.isInterface()) {
      if (type.getModifiers().contains(Modifier.PRIVATE)) {
        errorReporter.abortWithError("@AutoValue class must not be private", type);
      }
      if (!type.getModifiers().contains(Modifier.STATIC)) {
        errorReporter.abortWithError("Nested @AutoValue class must be static", type);
      }
    }
    // In principle type.getEnclosingElement() could be an ExecutableElement (for a class
    // declared inside a method), but since RoundEnvironment.getElementsAnnotatedWith doesn't
    // return such classes we won't see them here.
  }

  // If we have a getter called getPackage() then we can't use the identifier "package" to represent
  // its value since that's a reserved word.
  private void fixReservedIdentifiers(Map<ExecutableElement, String> methodToIdentifier) {
    for (Map.Entry<ExecutableElement, String> entry : methodToIdentifier.entrySet()) {
      if (SourceVersion.isKeyword(entry.getValue())) {
        entry.setValue(disambiguate(entry.getValue(), methodToIdentifier.values()));
      }
    }
  }

  private String disambiguate(String name, Collection<String> existingNames) {
    for (int i = 0; ; i++) {
      String candidate = name + i;
      if (!existingNames.contains(candidate)) {
        return candidate;
      }
    }
  }

  private Set<TypeMirror> returnTypesOf(Iterable<ExecutableElement> methods) {
    Set<TypeMirror> returnTypes = new TypeMirrorSet();
    for (ExecutableElement method : methods) {
      returnTypes.add(method.getReturnType());
    }
    return returnTypes;
  }

  private static boolean containsArrayType(Set<TypeMirror> types) {
    for (TypeMirror type : types) {
      if (type.getKind() == TypeKind.ARRAY) {
        return true;
      }
    }
    return false;
  }

  /**
   * Given a list of all methods defined in or inherited by a class, sets the equals, hashCode, and
   * toString fields of vars according as the corresponding methods should be generated.
   */
  private static void determineObjectMethodsToGenerate(
      Set<ExecutableElement> methods, AutoValueTemplateVars vars) {
    // The defaults here only come into play when an ancestor class doesn't exist.
    // Compilation will fail in that case, but we don't want it to crash the compiler with
    // an exception before it does. If all ancestors do exist then we will definitely find
    // definitions of these three methods (perhaps the ones in Object) so we will overwrite these:
    vars.equals = false;
    vars.hashCode = false;
    vars.toString = false;
    for (ExecutableElement method : methods) {
      ObjectMethodToOverride override = objectMethodToOverride(method);
      boolean canGenerate = method.getModifiers().contains(Modifier.ABSTRACT)
          || isJavaLangObject((TypeElement) method.getEnclosingElement());
      switch (override) {
        case EQUALS:
          vars.equals = canGenerate;
          break;
        case HASH_CODE:
          vars.hashCode = canGenerate;
          break;
        case TO_STRING:
          vars.toString = canGenerate;
          break;
        default:
          // Not a method from Object, nothing to do.
      }
    }
  }

  private ImmutableSet<ExecutableElement> abstractMethodsIn(
      ImmutableSet<ExecutableElement> methods) {
    Set<Name> noArgMethods = Sets.newHashSet();
    ImmutableSet.Builder<ExecutableElement> abstracts = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        boolean hasArgs = !method.getParameters().isEmpty();
        if (hasArgs || noArgMethods.add(method.getSimpleName())) {
          // If an abstract method with the same signature is inherited on more than one path,
          // we only add it once. At the moment we only do this check for no-arg methods. All
          // methods that AutoValue will implement are either no-arg methods or equals(Object).
          // The former is covered by this check and the latter will lead to vars.equals being
          // set to true, regardless of how many times it appears. So the only case that is
          // covered imperfectly here is that of a method that is inherited on more than one path
          // and that will be consumed by an extension. We could check parameters as well, but that
          // can be a bit tricky if any of the parameters are generic.
          abstracts.add(method);
        }
      }
    }
    return abstracts.build();
  }

  private ImmutableSet<ExecutableElement> propertyMethodsIn(
      ImmutableSet<ExecutableElement> abstractMethods) {
    ImmutableSet.Builder<ExecutableElement> properties = ImmutableSet.builder();
    for (ExecutableElement method : abstractMethods) {
      if (method.getParameters().isEmpty()
          && method.getReturnType().getKind() != TypeKind.VOID
          && objectMethodToOverride(method) == ObjectMethodToOverride.NONE) {
        properties.add(method);
      }
    }
    return properties.build();
  }

  private boolean checkReturnType(TypeElement autoValueClass, ExecutableElement getter) {
    TypeMirror type = getter.getReturnType();
    if (type.getKind() == TypeKind.ARRAY) {
      TypeMirror componentType = ((ArrayType) type).getComponentType();
      if (componentType.getKind().isPrimitive()) {
        warnAboutPrimitiveArrays(autoValueClass, getter);
        return true;
      } else {
        errorReporter.reportError("An @AutoValue class cannot define an array-valued property"
            + " unless it is a primitive array", getter);
        return false;
      }
    } else {
      return true;
    }
  }

  // Detects whether the visited AnnotationValue is an array that contains the string "mutable".
  // The simpler approach using Element.getAnnotation(SuppressWarnings.class) doesn't work if
  // the annotation has an undefined reference, like @SuppressWarnings(UNDEFINED).
  // TODO(emcmanus): replace with a method from auto-common when that is available.
  private static class ContainsMutableVisitor extends SimpleAnnotationValueVisitor6<Boolean, Void> {
    @Override
    public Boolean visitArray(List<? extends AnnotationValue> list, Void p) {
      for (AnnotationValue value : list) {
        if ("mutable".equals(value.getValue())) {
          return true;
        }
      }
      return false;
    }
  }

  private void warnAboutPrimitiveArrays(TypeElement autoValueClass, ExecutableElement getter) {
    boolean suppressed = false;
    Optional<AnnotationMirror> maybeAnnotation =
        getAnnotationMirror(getter, SuppressWarnings.class);
    if (maybeAnnotation.isPresent()) {
      AnnotationValue listValue = getAnnotationValue(maybeAnnotation.get(), "value");
      suppressed = listValue.accept(new ContainsMutableVisitor(), null);
    }
    if (!suppressed) {
      // If the primitive-array property method is defined directly inside the @AutoValue class,
      // then our error message should point directly to it. But if it is inherited, we don't
      // want to try to make the error message point to the inherited definition, since that would
      // be confusing (there is nothing wrong with the definition itself), and won't work if the
      // inherited class is not being recompiled. Instead, in this case we point to the @AutoValue
      // class itself, and we include extra text in the error message that shows the full name of
      // the inherited method.
      String warning =
          "An @AutoValue property that is a primitive array returns the original array, "
              + "which can therefore be modified by the caller. If this OK, you can "
              + "suppress this warning with @SuppressWarnings(\"mutable\"). Otherwise, you "
              + "should replace the property with an immutable type, perhaps a simple wrapper "
              + "around the original array.";
      boolean sameClass = getter.getEnclosingElement().equals(autoValueClass);
      if (sameClass) {
        errorReporter.reportWarning(warning, getter);
      } else {
        errorReporter.reportWarning(
            warning + " Method: " + getter.getEnclosingElement() + "." + getter, autoValueClass);
      }
    }
  }

  private void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      Writer writer = sourceFile.openWriter();
      try {
        writer.write(text);
      } finally {
        writer.close();
      }
    } catch (IOException e) {
      // This should really be an error, but we make it a warning in the hope of resisting Eclipse
      // bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599. If that bug manifests, we may get
      // invoked more than once for the same file, so ignoring the ability to overwrite it is the
      // right thing to do. If we are unable to write for some other reason, we should get a compile
      // error later because user code will have a reference to the code we were supposed to
      // generate (new AutoValue_Foo() or whatever) and that reference will be undefined.
      processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
          "Could not write generated class " + className + ": " + e);
    }
  }

  private boolean ancestorIsAutoValue(TypeElement type) {
    while (true) {
      TypeMirror parentMirror = type.getSuperclass();
      if (parentMirror.getKind() == TypeKind.NONE) {
        return false;
      }
      TypeElement parentElement = (TypeElement) typeUtils.asElement(parentMirror);
      if (MoreElements.isAnnotationPresent(parentElement, AutoValue.class)) {
        return true;
      }
      type = parentElement;
    }
  }

  private boolean implementsAnnotation(TypeElement type) {
    return typeUtils.isAssignable(type.asType(), getTypeMirror(Annotation.class));
  }

  // Return a string like "1234L" if type instanceof Serializable and defines
  // serialVersionUID = 1234L, otherwise "".
  private String getSerialVersionUID(TypeElement type) {
    TypeMirror serializable = getTypeMirror(Serializable.class);
    if (typeUtils.isAssignable(type.asType(), serializable)) {
      List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
      for (VariableElement field : fields) {
        if (field.getSimpleName().contentEquals("serialVersionUID")) {
          Object value = field.getConstantValue();
          if (field.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.FINAL))
              && field.asType().getKind() == TypeKind.LONG
              && value != null) {
            return value + "L";
          } else {
            errorReporter.reportError(
                "serialVersionUID must be a static final long compile-time constant", field);
            break;
          }
        }
      }
    }
    return "";
  }

  private TypeMirror getTypeMirror(Class<?> c) {
    return processingEnv.getElementUtils().getTypeElement(c.getName()).asType();
  }

  // The @AutoValue type, with a ? for every type.
  // If we have @AutoValue abstract class Foo<T extends Something> then this method will return
  // just <?>.
  private static String wildcardTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      return "<"
          + Joiner.on(", ").join(
              FluentIterable.from(typeParameters).transform(Functions.constant("?")))
          + ">";
    }
  }

  private static <E> ImmutableSet<E> immutableSetDifference(ImmutableSet<E> a, ImmutableSet<E> b) {
    if (Collections.disjoint(a, b)) {
      return a;
    } else {
      return ImmutableSet.copyOf(Sets.difference(a, b));
    }
  }

  private EclipseHack eclipseHack() {
    return new EclipseHack(processingEnv);
  }
}
