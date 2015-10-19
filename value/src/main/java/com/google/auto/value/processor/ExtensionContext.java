package com.google.auto.value.processor;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class ExtensionContext implements AutoValueExtension.Context {

  private final ProcessingEnvironment processingEnvironment;
  private final TypeElement typeElement;
  private ImmutableMap<String, ExecutableElement> properties;

  ExtensionContext(
      ProcessingEnvironment processingEnvironment,
      TypeElement typeElement,
      ImmutableMap<String, ExecutableElement> properties) {
    this.processingEnvironment = processingEnvironment;
    this.typeElement = typeElement;
    this.properties = properties;
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

  public void setProperties(Map<String, ExecutableElement> properties) {
    this.properties = ImmutableMap.copyOf(properties);
  }
}
