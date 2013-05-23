/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
package com.google.autofactory;


import com.google.autofactory.ProcessorUtils.InjectedClass;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.google.autofactory.ProcessorUtils.error;
import static com.google.autofactory.ProcessorUtils.getInjectedClass;
import static com.google.autofactory.ProcessorUtils.getTypesWithAnnotatedMembers;


/**
 * Generates an implementation of {@link com.google.autofactory.internal.Binding} that injects the
 * {@literal @}{@code Inject}-annotated members of a class.
 */
@SupportedAnnotationTypes({ "com.google.autofactory.AutoFactory", "com.google.autofactory.Param" })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class FactoryProcessor extends AbstractProcessor {
  private final Set<String> remainingTypeNames = new LinkedHashSet<String>();

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      remainingTypeNames.addAll(getTypesWithAnnotatedMembers(env, Param.class));
      for (String factoryName : getFactories(env)) {
        TypeElement factoryType = processingEnv.getElementUtils().getTypeElement(factoryName);
        Set<InjectedClass> valueTypes =
            getValueTypes(ProcessorUtils.getReturnTypesForMethods(processingEnv, factoryType));
        for (InjectedClass valueType : valueTypes) {
          TypeElement type = valueType.type;
          Name typeName = type.getQualifiedName();
          if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            error(processingEnv, "%s cannot be an abstract class.", typeName);
            continue;
          }
          if (valueType.constructor != null || !valueType.fields.isEmpty()) {
            if (!factoryType.getKind().equals(ElementKind.INTERFACE)) {
              error(processingEnv,
                  "%s for %s value must be a single-method factory interface.",
                  factoryType.getQualifiedName(), typeName);
              continue;
            }
            ExecutableElement method = getFactoryMethod(factoryType);
            if (method == null) continue;
            new FactoryAdapterGenerator(processingEnv)
                .generate(valueType, factoryType, method);
            remainingTypeNames.remove(valueType.type.getQualifiedName().toString());
          }
        }
      }
    } catch (IOException e) {
      error(processingEnv, "Code gen failed: %s", e);
    }
    if (env.processingOver() && !remainingTypeNames.isEmpty()) {
      error(processingEnv, "Could not find a AutoFactory interface ford %s!", remainingTypeNames);
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private Set<InjectedClass> getValueTypes(Set<String> valueTypeNames) {
    Set<InjectedClass> types = new LinkedHashSet<InjectedClass>();
    for (String valueType : valueTypeNames) {
      types.add(getInjectedClass(processingEnv, valueType, Inject.class, Param.class));
    }
    return types;
  }

  /**
   * Gather the set of types annotated with {@link AutoFactory}.
   */
  private Set<String> getFactories(RoundEnvironment env) {
    Set<String> injectedTypeNames = new LinkedHashSet<String>();
    for (Element element : env.getElementsAnnotatedWith(AutoFactory.class)) {
      TypeMirror type = null;
      switch (element.getKind()) {
        case INTERFACE:
          type = element.asType();
          break;
        default:
          error(processingEnv, "%s annotated with AutoFactory must be an Interface.",
              element.getSimpleName());
          throw new AssertionError("Unsupported element type.");
      }
      injectedTypeNames.add(CodeGen.rawTypeToString(type, '.'));
    }
    return injectedTypeNames;
  }

  private ExecutableElement getFactoryMethod(TypeElement factory) {
    List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(factory);
    ExecutableElement method = null;
    for (Element member : members) {
      if (method != null) {
        error(processingEnv, "AutoFactory interface %s must only have a single method.",
            factory.getQualifiedName());
        return null;
      }
      if (member.getKind().equals(ElementKind.METHOD)
          && member.getEnclosingElement().equals(factory)) {
        method = (ExecutableElement) member;
      }
    }
    if (method == null) {
      error(processingEnv, "AutoFactory interface %s has no factory method.",
          factory.getQualifiedName());
      return null;
    } else {
      return method;
    }
  }

}
