/*
 * Copyright 2015 Google LLC
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

import static com.google.auto.value.processor.AutoValueOrOneOfProcessor.nullableAnnotationFor;
import static com.google.common.collect.Sets.difference;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.processor.BuilderSpec.Copier;
import com.google.auto.value.processor.BuilderSpec.PropertySetter;
import com.google.auto.value.processor.PropertyBuilderClassifier.PropertyBuilder;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Classifies methods inside builder types, based on their names and parameter and return types.
 *
 * @author Éamonn McManus
 */
class BuilderMethodClassifier {
    private static final Equivalence<TypeMirror> TYPE_EQUIVALENCE = MoreTypes.equivalence();

    private final ErrorReporter errorReporter;
    private final Types typeUtils;
    private final Elements elementUtils;
    private final TypeElement autoValueClass;
    private final TypeElement builderType;
    private final ImmutableBiMap<ExecutableElement, String> getterToPropertyName;
    private final ImmutableMap<ExecutableElement, TypeMirror> getterToPropertyType;
    private final ImmutableMap<String, ExecutableElement> getterNameToGetter;

    private final Set<ExecutableElement> buildMethods = new LinkedHashSet<>();
    private final Map<String, BuilderSpec.PropertyGetter> builderGetters = new LinkedHashMap<>();
    private final Map<String, PropertyBuilder> propertyNameToPropertyBuilder = new LinkedHashMap<>();
    private final Multimap<String, PropertySetter> propertyNameToPrefixedSetters =
            LinkedListMultimap.create();
    private final Multimap<String, PropertySetter> propertyNameToUnprefixedSetters =
            LinkedListMultimap.create();
    private final EclipseHack eclipseHack;

    private boolean settersPrefixed;

    private BuilderMethodClassifier(
            ErrorReporter errorReporter,
            ProcessingEnvironment processingEnv,
            TypeElement autoValueClass,
            TypeElement builderType,
            ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
            ImmutableMap<ExecutableElement, TypeMirror> getterToPropertyType) {
        this.errorReporter = errorReporter;
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.autoValueClass = autoValueClass;
        this.builderType = builderType;
        this.getterToPropertyName = getterToPropertyName;
        this.getterToPropertyType = getterToPropertyType;
        ImmutableMap.Builder<String, ExecutableElement> getterToPropertyNameBuilder =
                ImmutableMap.builder();
        for (ExecutableElement getter : getterToPropertyName.keySet()) {
            getterToPropertyNameBuilder.put(getter.getSimpleName().toString(), getter);
        }
        this.getterNameToGetter = getterToPropertyNameBuilder.build();
        this.eclipseHack = new EclipseHack(processingEnv);
    }

    /**
     * Classifies the given methods from a builder type and its ancestors.
     *
     * @param methods               the abstract methods in {@code builderType} and its ancestors.
     * @param errorReporter         where to report errors.
     * @param processingEnv         the ProcessingEnvironment for annotation processing.
     * @param autoValueClass        the {@code AutoValue} class containing the builder.
     * @param builderType           the builder class or interface within {@code autoValueClass}.
     * @param getterToPropertyName  a map from getter methods to the properties they get.
     * @param getterToPropertyType  a map from getter methods to their return types. The return types
     *                              here use type parameters from the builder class (if any) rather than from the {@code
     *                              AutoValue} class, even though the getter methods are in the latter.
     * @param autoValueHasToBuilder true if the containing {@code @AutoValue} class has a {@code
     *                              toBuilder()} method.
     * @return an {@code Optional} that contains the results of the classification if it was
     * successful or nothing if it was not.
     */
    static Optional<BuilderMethodClassifier> classify(
            Iterable<ExecutableElement> methods,
            ErrorReporter errorReporter,
            ProcessingEnvironment processingEnv,
            TypeElement autoValueClass,
            TypeElement builderType,
            ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
            ImmutableMap<ExecutableElement, TypeMirror> getterToPropertyType,
            boolean autoValueHasToBuilder) {
        BuilderMethodClassifier classifier =
                new BuilderMethodClassifier(
                        errorReporter,
                        processingEnv,
                        autoValueClass,
                        builderType,
                        getterToPropertyName,
                        getterToPropertyType);
        if (classifier.classifyMethods(methods, autoValueHasToBuilder)) {
            return Optional.of(classifier);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns a multimap from the name of a property to the methods that set it. If the property is
     * defined by an abstract method in the {@code @AutoValue} class called {@code foo()} or {@code
     * getFoo()} then the name of the property is {@code foo} and there will be an entry in the map
     * where the key is {@code "foo"} and the value describes a method in the builder called
     * {@code foo} or {@code setFoo}.
     */
    ImmutableMultimap<String, PropertySetter> propertyNameToSetters() {
        return ImmutableMultimap.copyOf(
                settersPrefixed ? propertyNameToPrefixedSetters : propertyNameToUnprefixedSetters);
    }

    Map<String, PropertyBuilder> propertyNameToPropertyBuilder() {
        return propertyNameToPropertyBuilder;
    }

    /**
     * Returns the set of properties that have getters in the builder. If a property is defined by an
     * abstract method in the {@code @AutoValue} class called {@code foo()} or {@code getFoo()} then
     * the name of the property is {@code foo}, If the builder also has a method of the same name
     * ({@code foo()} or {@code getFoo()}) then the set returned here will contain {@code foo}.
     */
    ImmutableMap<String, BuilderSpec.PropertyGetter> builderGetters() {
        return ImmutableMap.copyOf(builderGetters);
    }

    /**
     * Returns the methods that were identified as {@code build()} methods. These are methods that
     * have no parameters and return the {@code @AutoValue} type, conventionally called {@code
     * build()}.
     */
    Set<ExecutableElement> buildMethods() {
        return ImmutableSet.copyOf(buildMethods);
    }

    /** Classifies the given methods and sets the state of this object based on what is found. */
    private boolean classifyMethods(
            Iterable<ExecutableElement> methods, boolean autoValueHasToBuilder) {
        int startErrorCount = errorReporter.errorCount();
        for (ExecutableElement method : methods) {
            classifyMethod(method);
        }
        if (errorReporter.errorCount() > startErrorCount) {
            return false;
        }
        Multimap<String, PropertySetter> propertyNameToSetter;
        if (propertyNameToPrefixedSetters.isEmpty()) {
            propertyNameToSetter = propertyNameToUnprefixedSetters;
            this.settersPrefixed = false;
        } else if (propertyNameToUnprefixedSetters.isEmpty()) {
            propertyNameToSetter = propertyNameToPrefixedSetters;
            this.settersPrefixed = true;
        } else {
            errorReporter.reportError(
                    propertyNameToUnprefixedSetters.values().iterator().next().getSetter(),
                    "[AutoValueSetNotSet] If any setter methods use the setFoo convention then all must");
            return false;
        }
        getterToPropertyName.forEach(
                (getter, property) -> {
                    TypeMirror propertyType = getterToPropertyType.get(getter);
                    boolean hasSetter = propertyNameToSetter.containsKey(property);
                    PropertyBuilder propertyBuilder = propertyNameToPropertyBuilder.get(property);
                    boolean hasBuilder = propertyBuilder != null;
                    if (hasBuilder) {
                        // If property bar of type Bar has a barBuilder() that returns BarBuilder, then it must
                        // be possible to make a BarBuilder from a Bar if either (1) the @AutoValue class has a
                        // toBuilder() or (2) there is also a setBar(Bar). Making BarBuilder from Bar is
                        // possible if Bar either has a toBuilder() method or BarBuilder has an addAll or putAll
                        // method that accepts a Bar argument.
                        boolean canMakeBarBuilder =
                                (propertyBuilder.getBuiltToBuilder() != null
                                        || propertyBuilder.getCopyAll() != null);
                        boolean needToMakeBarBuilder = (autoValueHasToBuilder || hasSetter);
                        if (needToMakeBarBuilder && !canMakeBarBuilder) {
                            errorReporter.reportError(
                                    propertyBuilder.getPropertyBuilderMethod(),
                                    "[AutoValueCantMakeBuilder] Property builder method returns %1$s but there is no"
                                            + " way to make that type from %2$s: %2$s does not have a non-static"
                                            + " toBuilder() method that returns %1$s, and %1$s does not have a method"
                                            + " addAll or putAll that accepts an argument of type %2$s",
                                    propertyBuilder.getBuilderTypeMirror(),
                                    propertyType);
                        }
                    } else if (!hasSetter) {
                        // We have neither barBuilder() nor setBar(Bar), so we should complain.
                        String setterName = settersPrefixed ? prefixWithSet(property) : property;
                        errorReporter.reportError(
                                builderType,
                                "[AutoValueBuilderMissingMethod] Expected a method with this signature: %s%s"
                                        + " %s(%s), or a %sBuilder() method",
                                builderType,
                                typeParamsString(),
                                setterName,
                                propertyType,
                                property);
                    }
                });
        return errorReporter.errorCount() == startErrorCount;
    }

    /** Classifies a method and update the state of this object based on what is found. */
    private void classifyMethod(ExecutableElement method) {
        switch (method.getParameters().size()) {
            case 0:
                classifyMethodNoArgs(method);
                break;
            case 1:
                classifyMethodOneArg(method);
                break;
            default:
                errorReporter.reportError(
                        method, "[AutoValueBuilderArgs] Builder methods must have 0 or 1 parameters");
        }
    }

    /**
     * Classifies a method given that it has no arguments. Currently a method with no arguments can be
     * a {@code build()} method, meaning that its return type must be the {@code @AutoValue} class; it
     * can be a getter, with the same signature as one of the property getters in the
     * {@code @AutoValue} class; or it can be a property builder, like {@code
     * ImmutableList.Builder<String> foosBuilder()} for the property defined by {@code
     * ImmutableList<String> foos()} or {@code getFoos()}.
     */
    private void classifyMethodNoArgs(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        TypeMirror returnType = builderMethodReturnType(method);

        ExecutableElement getter = getterNameToGetter.get(methodName);
        if (getter != null) {
            classifyGetter(method, getter);
            return;
        }

        if (methodName.endsWith("Builder")) {
            String property = methodName.substring(0, methodName.length() - "Builder".length());
            if (getterToPropertyName.containsValue(property)) {
                PropertyBuilderClassifier propertyBuilderClassifier =
                        new PropertyBuilderClassifier(
                                errorReporter,
                                typeUtils,
                                elementUtils,
                                this,
                                getterToPropertyName,
                                getterToPropertyType,
                                eclipseHack);
                Optional<PropertyBuilder> propertyBuilder =
                        propertyBuilderClassifier.makePropertyBuilder(method, property);
                if (propertyBuilder.isPresent()) {
                    propertyNameToPropertyBuilder.put(property, propertyBuilder.get());
                }
                return;
            }
        }

        if (TYPE_EQUIVALENCE.equivalent(returnType, autoValueClass.asType())) {
            buildMethods.add(method);
        } else {
            errorReporter.reportError(
                    method,
                    "[AutoValueBuilderNoArg] Method without arguments should be a build method returning"
                            + " %1$s%2$s, or a getter method with the same name and type as a getter method of"
                            + " %1$s, or fooBuilder() where foo() or getFoo() is a getter method of %1$s",
                    autoValueClass,
                    typeParamsString());
        }
    }

    private void classifyGetter(ExecutableElement builderGetter, ExecutableElement originalGetter) {
        String propertyName = getterToPropertyName.get(originalGetter);
        TypeMirror originalGetterType = getterToPropertyType.get(originalGetter);
        TypeMirror builderGetterType = builderMethodReturnType(builderGetter);
        String builderGetterTypeString = TypeEncoder.encodeWithAnnotations(builderGetterType);
        if (TYPE_EQUIVALENCE.equivalent(builderGetterType, originalGetterType)) {
            builderGetters.put(
                    propertyName,
                    new BuilderSpec.PropertyGetter(builderGetter, builderGetterTypeString, null));
            return;
        }
        Optionalish optional = Optionalish.createIfOptional(builderGetterType);
        if (optional != null) {
            TypeMirror containedType = optional.getContainedType(typeUtils);
            // If the original method is int getFoo() then we allow Optional<Integer> here.
            // boxedOriginalType is Integer, and containedType is also Integer.
            // We don't need any special code for OptionalInt because containedType will be int then.
            TypeMirror boxedOriginalType =
                    originalGetterType.getKind().isPrimitive()
                            ? typeUtils.boxedClass(MoreTypes.asPrimitiveType(originalGetterType)).asType()
                            : null;
            if (TYPE_EQUIVALENCE.equivalent(containedType, originalGetterType)
                    || TYPE_EQUIVALENCE.equivalent(containedType, boxedOriginalType)) {
                builderGetters.put(
                        propertyName,
                        new BuilderSpec.PropertyGetter(builderGetter, builderGetterTypeString, optional));
                return;
            }
        }
        errorReporter.reportError(
                builderGetter,
                "[AutoValueBuilderReturnType] Method matches a property of %1$s but has return type %2$s"
                        + " instead of %3$s or an Optional wrapping of %3$s",
                autoValueClass,
                builderGetterType,
                originalGetterType);
    }

    /**
     * Classifies a method given that it has one argument.
     * A method with one argument can be:
     * <ul>
     *     <li>a setter, meaning that it must look like {@code foo(T)} or {@code setFoo(T)},
     *     where the {@code AutoValue} class has a property called {@code foo} of type {@code T}.</li>
     *     <li>a property builder with comparator, meaning it must look like {@code ImmutableSortedSet.Builder<V> foosBuilder(Comparator<V>)},
     *     where the {@code AutoValue} class has a property called {@code foo} of type <em>sorted collection</em></li>
     * </ul>
     */
    private void classifyMethodOneArg(ExecutableElement method) {
        if (classifiedAsSortedCollectionBuilder(method)) {
            return;
        }

        String methodName = method.getSimpleName().toString();
        Map<String, ExecutableElement> propertyNameToGetter = getterToPropertyName.inverse();
        String propertyName = null;
        ExecutableElement valueGetter = propertyNameToGetter.get(methodName);
        Multimap<String, PropertySetter> propertyNameToSetters = null;
        if (valueGetter != null) {
            propertyNameToSetters = propertyNameToUnprefixedSetters;
            propertyName = methodName;
        } else if (methodName.startsWith("set") && methodName.length() > 3) {
            propertyNameToSetters = propertyNameToPrefixedSetters;
            propertyName = PropertyNames.decapitalizeLikeJavaBeans(methodName.substring(3));
            valueGetter = propertyNameToGetter.get(propertyName);
            if (valueGetter == null) {
                // If our property is defined by a getter called getOAuth() then it is called "OAuth"
                // because of JavaBeans rules. Therefore we want JavaBeans rules to be used for the setter
                // too, so that you can write setOAuth(x). Meanwhile if the property is defined by a getter
                // called oAuth() then it is called "oAuth", but you would still expect to be able to set it
                // using setOAuth(x). Hence the second try using a decapitalize method without the quirky
                // two-leading-capitals rule.
                propertyName = PropertyNames.decapitalizeNormally(methodName.substring(3));
                valueGetter = propertyNameToGetter.get(propertyName);
            }
        } else {
            // We might also have an unprefixed setter, so the getter is called OAuth() or getOAuth() and
            // the setter is called oAuth(x), where again JavaBeans rules imply that it should be called
            // OAuth(x). Iterating over the properties here is a bit clunky but this case should be
            // unusual.
            propertyNameToSetters = propertyNameToUnprefixedSetters;
            for (Map.Entry<String, ExecutableElement> entry : propertyNameToGetter.entrySet()) {
                if (methodName.equals(PropertyNames.decapitalizeNormally(entry.getKey()))) {
                    propertyName = entry.getKey();
                    valueGetter = entry.getValue();
                    break;
                }
            }
        }
        if (valueGetter == null || propertyNameToSetters == null) {
            // The second disjunct isn't needed but convinces control-flow checkers that
            // propertyNameToSetters can't be null when we call put on it below.
            errorReporter.reportError(
                    method,
                    "[AutoValueBuilderWhatProp] Method does not correspond to a property of %s",
                    autoValueClass);
            checkForFailedJavaBean(method);
            return;
        }
        Optional<Copier> function = getSetterFunction(valueGetter, method);
        if (function.isPresent()) {
            DeclaredType builderTypeMirror = MoreTypes.asDeclared(builderType.asType());
            ExecutableType methodMirror =
                    MoreTypes.asExecutable(typeUtils.asMemberOf(builderTypeMirror, method));
            if (TYPE_EQUIVALENCE.equivalent(methodMirror.getReturnType(), builderType.asType())) {
                TypeMirror parameterType = Iterables.getOnlyElement(methodMirror.getParameterTypes());
                propertyNameToSetters.put(
                        propertyName, new PropertySetter(method, parameterType, function.get()));
            } else {
                errorReporter.reportError(
                        method, "Setter methods must return %s%s", builderType, typeParamsString());
            }
        }
    }

    /**
     * Classifies a method given that it has one argument and is a property builder with comparator,
     * like {@code ImmutableSortedSet.Builder<String> foosBuilder(Comparator<String>)}.
     *
     * @param method A methoid to classify
     * @return {@code true} if method has been classified successfully
     */
    private boolean classifiedAsSortedCollectionBuilder(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();

        if (!methodName.endsWith("Builder")) {
            return false;
        }

        String property = methodName.substring(0, methodName.length() - "Builder".length());
        if (!getterToPropertyName.containsValue(property)) {
            return false;
        }

        PropertyBuilderClassifier propertyBuilderClassifier = new PropertyBuilderClassifier(errorReporter,
                typeUtils,
                elementUtils,
                this,
                getterToPropertyName,
                getterToPropertyType,
                eclipseHack);

        Optional<PropertyBuilder> maybePropertyBuilder = propertyBuilderClassifier.makePropertyBuilder(method, property);
        maybePropertyBuilder.ifPresent(propertyBuilder -> propertyNameToPropertyBuilder.put(property, propertyBuilder));

        return maybePropertyBuilder.isPresent();
    }

    // A frequent source of problems is where the JavaBeans conventions have been followed for
    // most but not all getters. Then AutoValue considers that they haven't been followed at all,
    // so you might have a property called getFoo where you thought it was called just foo, and
    // you might not understand why your setter called setFoo is rejected (it would have to be called
    // setGetFoo).
    private void checkForFailedJavaBean(ExecutableElement rejectedSetter) {
        ImmutableSet<ExecutableElement> allGetters = getterToPropertyName.keySet();
        ImmutableSet<ExecutableElement> prefixedGetters =
                AutoValueProcessor.prefixedGettersIn(allGetters);
        if (prefixedGetters.size() < allGetters.size()
                && prefixedGetters.size() >= allGetters.size() / 2) {
            errorReporter.reportNote(
                    rejectedSetter,
                    "This might be because you are using the getFoo() convention"
                            + " for some but not all methods. These methods don't follow the convention: %s",
                    difference(allGetters, prefixedGetters));
        }
    }

    /**
     * Returns an {@code Optional} describing how to convert a value from the setter's parameter type
     * to the getter's return type, or {@code Optional.empty()} if the conversion isn't possible. An
     * error will have been reported in the latter case. We can convert if they are already the same
     * type, when the returned function will be the identity; or if the setter type can be copied
     * using a method like {@code ImmutableList.copyOf} or {@code Optional.of}, when the returned
     * function will be something like {@code s -> "Optional.of(" + s + ")"}.
     */
    private Optional<Copier> getSetterFunction(
            ExecutableElement valueGetter, ExecutableElement setter) {
        VariableElement parameterElement = Iterables.getOnlyElement(setter.getParameters());
        boolean nullableParameter =
                nullableAnnotationFor(parameterElement, parameterElement.asType()).isPresent();
        TypeMirror targetType = getterToPropertyType.get(valueGetter);
        ExecutableType finalSetter =
                MoreTypes.asExecutable(
                        typeUtils.asMemberOf(MoreTypes.asDeclared(builderType.asType()), setter));
        TypeMirror parameterType = finalSetter.getParameterTypes().get(0);
        // Two types are assignable to each other if they are the same type, or if one is primitive and
        // the other is the corresponding boxed type. There might be other cases where this is true, but
        // we're likely to want to accept those too.
        if (typeUtils.isAssignable(parameterType, targetType)
                && typeUtils.isAssignable(targetType, parameterType)) {
            if (nullableParameter) {
                boolean nullableProperty =
                        nullableAnnotationFor(valueGetter, valueGetter.getReturnType()).isPresent();
                if (!nullableProperty) {
                    errorReporter.reportError(
                            setter,
                            "[AutoValueNullNotNull] Parameter of setter method is @Nullable but property method"
                                    + " %s.%s() is not",
                            autoValueClass,
                            valueGetter.getSimpleName());
                    return Optional.empty();
                }
            }
            return Optional.of(Copier.IDENTITY);
        }

        // Parameter type is not equal to property type, but might be convertible with copyOf.
        ImmutableList<ExecutableElement> copyOfMethods = copyOfMethods(targetType, nullableParameter);
        if (!copyOfMethods.isEmpty()) {
            return getConvertingSetterFunction(copyOfMethods, valueGetter, setter, parameterType);
        }
        errorReporter.reportError(
                setter,
                "[AutoValueGetVsSet] Parameter type %s of setter method should be %s to match getter %s.%s",
                parameterType, targetType, autoValueClass, valueGetter.getSimpleName());
        return Optional.empty();
    }

    /**
     * Returns an {@code Optional} describing how to convert a value from the setter's parameter type
     * to the getter's return type using one of the given methods, or {@code Optional.empty()} if the
     * conversion isn't possible. An error will have been reported in the latter case.
     */
    private Optional<Copier> getConvertingSetterFunction(
            ImmutableList<ExecutableElement> copyOfMethods,
            ExecutableElement valueGetter,
            ExecutableElement setter,
            TypeMirror parameterType) {
        DeclaredType targetType = MoreTypes.asDeclared(getterToPropertyType.get(valueGetter));
        for (ExecutableElement copyOfMethod : copyOfMethods) {
            Optional<Copier> function =
                    getConvertingSetterFunction(copyOfMethod, targetType, parameterType);
            if (function.isPresent()) {
                return function;
            }
        }
        String targetTypeSimpleName = targetType.asElement().getSimpleName().toString();
        errorReporter.reportError(
                setter,
                "[AutoValueGetVsSetOrConvert] Parameter type %s of setter method should be %s to match"
                        + " getter %s.%s, or it should be a type that can be passed to %s.%s to produce %s",
                parameterType,
                targetType,
                autoValueClass,
                valueGetter.getSimpleName(),
                targetTypeSimpleName,
                copyOfMethods.get(0).getSimpleName(),
                targetType);
        return Optional.empty();
    }

    /**
     * Returns an {@code Optional} containing a function to use {@code copyOfMethod} to copy the
     * {@code parameterType} to the {@code targetType}, or {@code Optional.empty()} if the method
     * can't be used. For example, we might have a property of type {@code ImmutableSet<T>} and our
     * setter has a parameter of type {@code Set<? extends T>}. Can we use {@code ImmutableSet<E>
     * ImmutableSet.copyOf(Collection<? extends E>)} to set the property? What about {@code
     * ImmutableSet<E> ImmutableSet.copyOf(E[])}?
     *
     * <p>The example here is deliberately complicated, in that it has a type parameter of its own,
     * presumably because the {@code @AutoValue} class is {@code Foo<T>}. One subtle point is that the
     * builder will then be {@code Builder<T>} where this {@code T} is a <i>different</i> type
     * variable. However, we've used {@link TypeVariables} to ensure that the {@code T} in {@code
     * ImmutableSet<T>} is actually the one from {@code Builder<T>} instead of the original one from
     * {@code Foo<T>}.}
     *
     * @param copyOfMethod  the candidate method to do the copy, {@code
     *                      ImmutableSet.copyOf(Collection<? extends E>)} or {@code ImmutableSet.copyOf(E[])} in the
     *                      examples.
     * @param targetType    the type of the property to be set, {@code ImmutableSet<T>} in the example.
     * @param parameterType the type of the setter parameter, {@code Set<? extends T>} in the example.
     * @return a function that maps a string parameter to a method call using that parameter. For
     * example it might map {@code foo} to {@code ImmutableList.copyOf(foo)}.
     */
    private Optional<Copier> getConvertingSetterFunction(
            ExecutableElement copyOfMethod, DeclaredType targetType, TypeMirror parameterType) {
        // We have a parameter type, for example Set<? extends T>, and we want to know if it can be
        // passed to the given copyOf method, which might for example be one of these methods from
        // ImmutableSet:
        //    public static <E> ImmutableSet<E> copyOf(Collection<? extends E> elements)
        //    public static <E> ImmutableSet<E> copyOf(E[] elements)
        // Additionally, if it can indeed be passed to the method, we want to know whether the result
        // (here ImmutableSet<? extends T>) is compatible with the property to be set.
        // We can't use Types.asMemberOf to do the substitution for us, because the methods in question
        // are static. So even if our target type is ImmutableSet<String>, if we ask what the type of
        // copyOf is in ImmutableSet<String> it will still tell us <T> Optional<T> (T).
        // Instead, we do the variable substitutions ourselves.
        if (TypeVariables.canAssignStaticMethodResult(
                copyOfMethod, parameterType, targetType, typeUtils)) {
            String method = TypeEncoder.encodeRaw(targetType) + "." + copyOfMethod.getSimpleName();
            Function<String, String> callMethod = s -> method + "(" + s + ")";
            // This is a big old hack. We guess that the method can accept a null parameter if it has
            // "Nullable" in the name, which java.util.Optional.ofNullable and
            // com.google.common.base.Optional.fromNullable do.
            Copier copier =
                    method.contains("Nullable")
                            ? Copier.acceptingNull(callMethod)
                            : Copier.notAcceptingNull(callMethod);
            return Optional.of(copier);
        }
        return Optional.empty();
    }

    /**
     * Returns {@code copyOf} methods from the given type. These are static methods with a single
     * parameter, called {@code copyOf} or {@code copyOfSorted} for Guava collection types, and called
     * {@code of} or {@code ofNullable} for {@code Optional}. All of Guava's concrete immutable
     * collection types have at least one such method, but we will also accept other classes with an
     * appropriate {@code copyOf} method, such as {@link java.util.EnumSet}.
     */
    private ImmutableList<ExecutableElement> copyOfMethods(
            TypeMirror targetType, boolean nullableParameter) {
        if (!targetType.getKind().equals(TypeKind.DECLARED)) {
            return ImmutableList.of();
        }
        ImmutableSet<String> copyOfNames;
        Optionalish optionalish = Optionalish.createIfOptional(targetType);
        if (optionalish == null) {
            copyOfNames = ImmutableSet.of("copyOfSorted", "copyOf");
        } else {
            copyOfNames = ImmutableSet.of(nullableParameter ? optionalish.ofNullable() : "of");
        }
        TypeElement targetTypeElement = MoreElements.asType(typeUtils.asElement(targetType));
        ImmutableList.Builder<ExecutableElement> copyOfMethods = ImmutableList.builder();
        for (String copyOfName : copyOfNames) {
            for (ExecutableElement method :
                    ElementFilter.methodsIn(targetTypeElement.getEnclosedElements())) {
                if (method.getSimpleName().contentEquals(copyOfName)
                        && method.getParameters().size() == 1
                        && method.getModifiers().contains(Modifier.STATIC)) {
                    copyOfMethods.add(method);
                }
            }
        }
        return copyOfMethods.build();
    }

    /**
     * Returns the return type of the given method from the builder. This should be the final type of
     * the method when any bound type variables are substituted. Consider this example:
     *
     * <pre>{@code
     * abstract static class ParentBuilder<B extends ParentBuilder> {
     *   B setFoo(String s);
     * }
     * abstract static class ChildBuilder extends ParentBuilder<ChildBuilder> {
     *   ...
     * }
     * }</pre>
     *
     * If the builder is {@code ChildBuilder} then the return type of {@code setFoo} is also {@code
     * ChildBuilder}, and not {@code B} as its {@code getReturnType()} method would claim.
     *
     * <p>If the caller is in a version of Eclipse with <a
     * href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=382590">this bug</a> then the {@code
     * asMemberOf} call will fail if the method is inherited from an interface. We work around that
     * for methods in the {@code @AutoValue} class using {@link EclipseHack#methodReturnTypes} but we
     * don't try to do so here because it should be much less likely. You might need to change {@code
     * ParentBuilder} from an interface to an abstract class to make it work, but you'll often need to
     * do that anyway.
     */
    TypeMirror builderMethodReturnType(ExecutableElement builderMethod) {
        DeclaredType builderTypeMirror = MoreTypes.asDeclared(builderType.asType());
        TypeMirror methodMirror;
        try {
            methodMirror = typeUtils.asMemberOf(builderTypeMirror, builderMethod);
        } catch (IllegalArgumentException e) {
            // Presumably we've hit the Eclipse bug cited.
            return builderMethod.getReturnType();
        }
        return MoreTypes.asExecutable(methodMirror).getReturnType();
    }

    private static String prefixWithSet(String propertyName) {
        // This is not internationalizationally correct, but it corresponds to what
        // Introspector.decapitalize does.
        return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private String typeParamsString() {
        return TypeSimplifier.actualTypeParametersString(autoValueClass);
    }
}
