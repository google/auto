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
package com.google.auto.factory.processor;

import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.factory.processor.Elements2.isValidSupertypeForClass;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.lang.model.util.ElementFilter.typesIn;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.factory.AutoFactory;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * This is a value object that mirrors the static declaration of an {@link AutoFactory} annotation.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class AutoFactoryDeclaration {
  abstract TypeElement targetType();
  abstract Element target();
  abstract Optional<String> className();
  abstract TypeElement extendingType();
  abstract ImmutableSet<TypeElement> implementingTypes();
  abstract boolean allowSubclasses();
  abstract AnnotationMirror mirror();
  abstract ImmutableMap<String, AnnotationValue> valuesMap();

  String getFactoryName() {
    CharSequence packageName = getPackage(targetType()).getQualifiedName();
    StringBuilder builder = new StringBuilder(packageName);
    if (packageName.length() > 0) {
      builder.append('.');
    }
    if (className().isPresent()) {
      builder.append(className().get());
    } else {
      for (String enclosingSimpleName : targetEnclosingSimpleNames()) {
        builder.append(enclosingSimpleName).append('_');
      }
      builder.append(targetType().getSimpleName()).append("Factory");
    }
    return builder.toString();
  }

  private ImmutableList<String> targetEnclosingSimpleNames() {
    ImmutableList.Builder<String> simpleNames = ImmutableList.builder();
    for (Element element = targetType().getEnclosingElement();
        !element.getKind().equals(PACKAGE);
        element = element.getEnclosingElement()) {
      simpleNames.add(element.getSimpleName().toString());
    }
    return simpleNames.build().reverse();
  }

  static final class Factory {
    private final Elements elements;
    private final Messager messager;

    Factory(Elements elements, Messager messager) {
      this.elements = elements;
      this.messager = messager;
    }

    Optional<AutoFactoryDeclaration> createIfValid(Element element) {
      checkNotNull(element);
      AnnotationMirror mirror = Mirrors.getAnnotationMirror(element, AutoFactory.class).get();
      checkArgument(Mirrors.getQualifiedName(mirror.getAnnotationType()).
          contentEquals(AutoFactory.class.getName()));
      Map<String, AnnotationValue> values =
          Mirrors.simplifyAnnotationValueMap(elements.getElementValuesWithDefaults(mirror));
      checkState(values.size() == 4);

      // className value is a string, so we can just call toString
      AnnotationValue classNameValue = values.get("className");
      String className = classNameValue.getValue().toString();
      if (!className.isEmpty() && !isValidIdentifier(className)) {
        messager.printMessage(ERROR,
            String.format("\"%s\" is not a valid Java identifier", className),
            element, mirror, classNameValue);
        return Optional.absent();
      }

      AnnotationValue extendingValue = checkNotNull(values.get("extending"));
      TypeElement extendingType = AnnotationValues.asType(extendingValue);
      if (extendingType == null) {
        messager.printMessage(ERROR, "Unable to find the type: "
            + extendingValue.getValue().toString(),
                element, mirror, extendingValue);
        return Optional.absent();
      } else if (!isValidSupertypeForClass(extendingType)) {
        messager.printMessage(ERROR,
            String.format("%s is not a valid supertype for a factory. "
                + "Supertypes must be non-final classes.",
                    extendingType.getQualifiedName()),
            element, mirror, extendingValue);
        return Optional.absent();
      }
      ImmutableList<ExecutableElement> noParameterConstructors =
          FluentIterable.from(ElementFilter.constructorsIn(extendingType.getEnclosedElements()))
              .filter(new Predicate<ExecutableElement>() {
                @Override public boolean apply(ExecutableElement constructor) {
                  return constructor.getParameters().isEmpty();
                }
              })
              .toList();
      if (noParameterConstructors.size() == 0) {
        messager.printMessage(ERROR,
            String.format("%s is not a valid supertype for a factory. "
                + "Factory supertypes must have a no-arg constructor.",
                    extendingType.getQualifiedName()),
            element, mirror, extendingValue);
        return Optional.absent();
      } else if (noParameterConstructors.size() > 1) {
        throw new IllegalStateException("Multiple constructors with no parameters??");
      }

      AnnotationValue implementingValue = checkNotNull(values.get("implementing"));
      ImmutableSet.Builder<TypeElement> builder = ImmutableSet.builder();
      for (AnnotationValue implementingTypeValue : AnnotationValues.asList(implementingValue)) {
        builder.add(AnnotationValues.asType(implementingTypeValue));
      }
      ImmutableSet<TypeElement> implementingTypes = builder.build();

      AnnotationValue allowSubclassesValue = checkNotNull(values.get("allowSubclasses"));
      boolean allowSubclasses = AnnotationValues.asBoolean(allowSubclassesValue);

      return Optional.<AutoFactoryDeclaration>of(
          new AutoValue_AutoFactoryDeclaration(
              getAnnotatedType(element),
              element,
              className.isEmpty() ? Optional.<String>absent() : Optional.of(className),
              extendingType,
              implementingTypes,
              allowSubclasses,
              mirror,
              ImmutableMap.copyOf(values)));
    }

    private static TypeElement getAnnotatedType(Element element) {
      List<TypeElement> types = ImmutableList.of();
      while (types.isEmpty()) {
        types = typesIn(Arrays.asList(element));
        element = element.getEnclosingElement();
      }
      return getOnlyElement(types);
    }

    static boolean isValidIdentifier(String identifier) {
      return SourceVersion.isIdentifier(identifier) && !SourceVersion.isKeyword(identifier);
    }
  }
}
