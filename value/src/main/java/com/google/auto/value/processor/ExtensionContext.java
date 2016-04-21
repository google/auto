package com.google.auto.value.processor;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class ExtensionContext implements AutoValueExtension.Context {

  private final ProcessingEnvironment processingEnvironment;
  private final TypeElement typeElement;
  private final ImmutableMap<String, ExecutableElement> properties;
  private final ImmutableSet<ExecutableElement> abstractMethods;

  ExtensionContext(
      ProcessingEnvironment processingEnvironment,
      TypeElement typeElement,
      ImmutableMap<String, ExecutableElement> properties,
      ImmutableSet<ExecutableElement> abstractMethods) {
    this.processingEnvironment = processingEnvironment;
    this.typeElement = typeElement;
    this.properties = properties;
    this.abstractMethods = abstractMethods;
  }

  @Override
  public ProcessingEnvironment processingEnvironment() {
    return processingEnvironment;
  }

  @Override
  public String packageName() {
    return TypeSimplifier.packageNameOf(typeElement);
  }

  @Override
  public TypeElement autoValueClass() {
    return typeElement;
  }

  @Override
  public Map<String, ExecutableElement> properties() {
    return properties;
  }

  @Override
  public Set<ExecutableElement> abstractMethods() {
    return abstractMethods;
  }
}
