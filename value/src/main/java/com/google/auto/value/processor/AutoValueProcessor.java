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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.AutoValueExtension;
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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.beans.Introspector;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see AutoValue
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
public class AutoValueProcessor extends AbstractProcessor {
  public AutoValueProcessor() {
    this(ServiceLoader.load(AutoValueExtension.class, AutoValueProcessor.class.getClassLoader()));
  }

  /* testing */ AutoValueProcessor(Iterable<? extends AutoValueExtension> extensions) {
    this.extensions = extensions;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoValue.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private ErrorReporter errorReporter;

  /**
   * Qualified names of {@code @AutoValue} classes that we attempted to process but had to abandon
   * because we needed other types that they referenced and those other types were missing.
   */
  private final List<String> deferredTypeNames = new ArrayList<String>();

  private Iterable<? extends AutoValueExtension> extensions;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    errorReporter = new ErrorReporter(processingEnv);
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

    Property(
        String name,
        String identifier,
        ExecutableElement method,
        String type,
        TypeSimplifier typeSimplifier) {
      this.name = name;
      this.identifier = identifier;
      this.method = method;
      this.type = type;
      this.annotations = buildAnnotations(typeSimplifier);
    }

    private ImmutableList<String> buildAnnotations(TypeSimplifier typeSimplifier) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();

      for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
        TypeElement annotationElement =
            (TypeElement) annotationMirror.getAnnotationType().asElement();
        if (annotationElement.getQualifiedName().toString().equals(Override.class.getName())) {
          // Don't copy @Override if present, since we will be adding our own @Override in the
          // implementation.
          continue;
        }
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
      Set<Modifier> mods = method.getModifiers();
      if (mods.contains(Modifier.PUBLIC)) {
        return "public ";
      } else if (mods.contains(Modifier.PROTECTED)) {
        return "protected ";
      } else {
        return "";
      }
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

    ImmutableSet<ExecutableElement> methods =
        getLocalAndInheritedMethods(type, processingEnv.getElementUtils());
    ImmutableSet<ExecutableElement> methodsToImplement = methodsToImplement(type, methods);

    String fqExtClass = TypeSimplifier.classNameOf(type);
    List<AutoValueExtension> appliedExtensions = new ArrayList<AutoValueExtension>();
    AutoValueExtension.Context context = makeExtensionContext(type, methodsToImplement);
    for (AutoValueExtension extension : extensions) {
      if (extension.applicable(context)) {
        if (extension.mustBeAtEnd(context)) {
          appliedExtensions.add(0, extension);
        } else {
          appliedExtensions.add(extension);
        }
      }
    }

    String finalSubclass = generatedSubclassName(type, 0);
    String subclass = generatedSubclassName(type, appliedExtensions.size());
    AutoValueTemplateVars vars = new AutoValueTemplateVars();
    vars.pkg = TypeSimplifier.packageNameOf(type);
    vars.origClass = fqExtClass;
    vars.simpleClassName = TypeSimplifier.simpleNameOf(vars.origClass);
    vars.subclass = TypeSimplifier.simpleNameOf(subclass);
    vars.finalSubclass = TypeSimplifier.simpleNameOf(finalSubclass);
    vars.isFinal = appliedExtensions.isEmpty();
    vars.types = processingEnv.getTypeUtils();
    defineVarsForType(type, vars, methods);
    GwtCompatibility gwtCompatibility = new GwtCompatibility(type);
    vars.gwtCompatibleAnnotation = gwtCompatibility.gwtCompatibleAnnotationString();
    String text = vars.toText();
    text = Reformatter.fixup(text);
    writeSourceFile(subclass, text, type);
    GwtSerialization gwtSerialization = new GwtSerialization(gwtCompatibility, processingEnv, type);
    gwtSerialization.maybeWriteGwtSerializer(vars);

    String extClass = TypeSimplifier.simpleNameOf(subclass);
    for (int i = appliedExtensions.size() - 1; i >= 0; i--) {
      AutoValueExtension extension = appliedExtensions.remove(i);
      String fqClassName = generatedSubclassName(type, i);
      String className = TypeSimplifier.simpleNameOf(fqClassName);
      boolean isFinal = (i == 0);
      String source = extension.generateClass(context, className, extClass, isFinal);
      if (source == null || source.isEmpty()) {
        errorReporter.reportError("Extension returned no source code.", type);
        return;
      }
      source = Reformatter.fixup(source);
      writeSourceFile(fqClassName, source, type);

      extClass = className;
    }
  }

  private AutoValueExtension.Context makeExtensionContext(
      final TypeElement type, final Iterable<ExecutableElement> methods) {
    return new AutoValueExtension.Context() {

      @Override public ProcessingEnvironment processingEnvironment() {
        return processingEnv;
      }

      @Override public String packageName() {
        return TypeSimplifier.packageNameOf(type);
      }

      @Override public TypeElement autoValueClass() {
        return type;
      }

      @Override public Map<String, ExecutableElement> properties() {
        return propertyNameToMethodMap(methods);
      }
    };
  }

  private void defineVarsForType(TypeElement type, AutoValueTemplateVars vars,
                                 Set<ExecutableElement> methods) {
    Types typeUtils = processingEnv.getTypeUtils();
    determineObjectMethodsToGenerate(methods, vars);
    ImmutableSet<ExecutableElement> methodsToImplement = methodsToImplement(type, methods);
    Set<TypeMirror> types = new TypeMirrorSet();
    types.addAll(returnTypesOf(methodsToImplement));
    TypeElement generatedTypeElement =
        processingEnv.getElementUtils().getTypeElement(Generated.class.getName());
    if (generatedTypeElement != null) {
      types.add(generatedTypeElement.asType());
    }
    TypeMirror javaUtilArrays = getTypeMirror(Arrays.class);
    if (containsArrayType(types)) {
      // If there are array properties then we will be referencing java.util.Arrays.
      // Arrange to import it unless that would introduce ambiguity.
      types.add(javaUtilArrays);
    }
    BuilderSpec builderSpec = new BuilderSpec(type, processingEnv, errorReporter);
    Optional<BuilderSpec.Builder> builder = builderSpec.getBuilder();
    ImmutableSet<ExecutableElement> toBuilderMethods;
    if (builder.isPresent()) {
      toBuilderMethods = builder.get().toBuilderMethods(typeUtils, methodsToImplement);
      types.addAll(builder.get().referencedTypes());
    } else {
      toBuilderMethods = ImmutableSet.of();
    }
    vars.toBuilderMethods =
        FluentIterable.from(toBuilderMethods).transform(SimpleNameFunction.INSTANCE).toList();
    Set<ExecutableElement> propertyMethods = Sets.difference(methodsToImplement, toBuilderMethods);
    types.addAll(allMethodAnnotationTypes(propertyMethods));
    String pkg = TypeSimplifier.packageNameOf(type);
    TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtils, pkg, types, type.asType());
    vars.imports = typeSimplifier.typesToImport();
    vars.generated = generatedTypeElement == null
        ? ""
        : typeSimplifier.simplify(generatedTypeElement.asType());
    vars.arrays = typeSimplifier.simplify(javaUtilArrays);
    ImmutableBiMap<ExecutableElement, String> methodToPropertyName =
        methodToPropertyNameMap(propertyMethods);
    Map<ExecutableElement, String> methodToIdentifier =
        Maps.newLinkedHashMap(methodToPropertyName);
    fixReservedIdentifiers(methodToIdentifier);
    List<Property> props = new ArrayList<Property>();
    for (ExecutableElement method : propertyMethods) {
      String propertyType = typeSimplifier.simplify(method.getReturnType());
      String propertyName = methodToPropertyName.get(method);
      String identifier = methodToIdentifier.get(method);
      props.add(new Property(propertyName, identifier, method, propertyType, typeSimplifier));
    }
    // If we are running from Eclipse, undo the work of its compiler which sorts methods.
    eclipseHack().reorderProperties(props);
    vars.props = ImmutableSet.copyOf(props);
    vars.serialVersionUID = getSerialVersionUID(type);
    vars.formalTypes = typeSimplifier.formalTypeParametersString(type);
    vars.actualTypes = TypeSimplifier.actualTypeParametersString(type);
    vars.wildcardTypes = wildcardTypeParametersString(type);
    // Check for @AutoValue.Builder and add appropriate variables if it is present.
    if (builder.isPresent()) {
      builder.get().defineVars(vars, typeSimplifier, methodToPropertyName);
    }
  }

  private ImmutableBiMap<ExecutableElement, String> methodToPropertyNameMap(
      Iterable<ExecutableElement> propertyMethods) {
    ImmutableMap.Builder<ExecutableElement, String> builder = ImmutableMap.builder();
    boolean allGetters = allGetters(propertyMethods);
    for (ExecutableElement method : propertyMethods) {
      String methodName = method.getSimpleName().toString();
      String name = allGetters ? nameWithoutPrefix(methodName) : methodName;
      builder.put(method, name);
    }
    ImmutableMap<ExecutableElement, String> map = builder.build();
    if (allGetters) {
      checkDuplicateGetters(map);
    }
    return ImmutableBiMap.copyOf(map);
  }

  private ImmutableBiMap<String, ExecutableElement> propertyNameToMethodMap(
      Iterable<ExecutableElement> propertyMethods) {
    ImmutableMap.Builder<String, ExecutableElement> builder = ImmutableMap.builder();
    boolean allGetters = allGetters(propertyMethods);
    for (ExecutableElement method : propertyMethods) {
      String methodName = method.getSimpleName().toString();
      String name = allGetters ? nameWithoutPrefix(methodName) : methodName;
      builder.put(name, method);
    }
    ImmutableMap<String, ExecutableElement> map = builder.build();
    return ImmutableBiMap.copyOf(map);
  }

  private static boolean allGetters(Iterable<ExecutableElement> methods) {
    for (ExecutableElement method : methods) {
      String name = method.getSimpleName().toString();
      // TODO(emcmanus): decide whether getfoo() (without a capital) is a getter. Currently it is.
      boolean get = name.startsWith("get") && !name.equals("get");
      boolean is = name.startsWith("is") && !name.equals("is")
          && method.getReturnType().getKind() == TypeKind.BOOLEAN;
      if (!get && !is) {
        return false;
      }
    }
    return true;
  }

  private Set<TypeMirror> allMethodAnnotationTypes(Iterable<ExecutableElement> methods) {
    Set<TypeMirror> annotationTypes = new TypeMirrorSet();
    for (ExecutableElement method : methods) {
      for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
        annotationTypes.add(annotationMirror.getAnnotationType());
      }
    }
    return annotationTypes;
  }

  private String nameWithoutPrefix(String name) {
    if (name.startsWith("get")) {
      name = name.substring(3);
    } else {
      assert name.startsWith("is");
      name = name.substring(2);
    }
    return Introspector.decapitalize(name);
  }

  private void checkDuplicateGetters(Map<ExecutableElement, String> methodToIdentifier) {
    Set<String> seen = Sets.newHashSet();
    for (Map.Entry<ExecutableElement, String> entry : methodToIdentifier.entrySet()) {
      if (!seen.add(entry.getValue())) {
        errorReporter.reportError(
            "More than one @AutoValue property called " + entry.getValue(), entry.getKey());
      }
    }
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
      }
    }
  }

  private ImmutableSet<ExecutableElement> methodsToImplement(
      TypeElement autoValueClass, Set<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> toImplement = ImmutableSet.builder();
    Set<Name> toImplementNames = Sets.newHashSet();
    boolean ok = true;
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)
          && objectMethodToOverride(method) == ObjectMethodToOverride.NONE) {
        if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID) {
          ok &= checkReturnType(autoValueClass, method);
          if (toImplementNames.add(method.getSimpleName())) {
            // If an abstract method with the same signature is inherited on more than one path,
            // we only add it once.
            toImplement.add(method);
          }
        } else {
          // This could reasonably be an error, were it not for an Eclipse bug in
          // ElementUtils.override that sometimes fails to recognize that one method overrides
          // another, and therefore leaves us with both an abstract method and the subclass method
          // that overrides it. This shows up in AutoValueTest.LukesBase for example.
          errorReporter.reportWarning("@AutoValue classes cannot have abstract methods other than"
              + " property getters and Builder converters", method);
        }
      }
    }
    if (!ok) {
      throw new AbortProcessingException();
    }
    return toImplement.build();
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

  private void warnAboutPrimitiveArrays(TypeElement autoValueClass, ExecutableElement getter) {
    SuppressWarnings suppressWarnings = getter.getAnnotation(SuppressWarnings.class);
    if (suppressWarnings == null || !Arrays.asList(suppressWarnings.value()).contains("mutable")) {
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
      Types typeUtils = processingEnv.getTypeUtils();
      TypeElement parentElement = (TypeElement) typeUtils.asElement(parentMirror);
      if (MoreElements.isAnnotationPresent(parentElement, AutoValue.class)) {
        return true;
      }
      type = parentElement;
    }
  }

  private boolean implementsAnnotation(TypeElement type) {
    Types typeUtils = processingEnv.getTypeUtils();
    return typeUtils.isAssignable(type.asType(), getTypeMirror(Annotation.class));
  }

  // Return a string like "1234L" if type instanceof Serializable and defines
  // serialVersionUID = 1234L, otherwise "".
  private String getSerialVersionUID(TypeElement type) {
    Types typeUtils = processingEnv.getTypeUtils();
    TypeMirror serializable = getTypeMirror(Serializable.class);
    if (typeUtils.isAssignable(type.asType(), serializable)) {
      List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
      for (VariableElement field : fields) {
        if (field.getSimpleName().toString().equals("serialVersionUID")) {
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

  private EclipseHack eclipseHack() {
    return new EclipseHack(processingEnv);
  }
}
