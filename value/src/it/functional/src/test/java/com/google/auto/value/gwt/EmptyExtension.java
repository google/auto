/*
 * Copyright 2018 Google LLC
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
package com.google.auto.value.gwt;

import static java.util.stream.Collectors.joining;

import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.escapevelocity.Template;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

/**
 * An AutoValue extension that generates a subclass that does nothing useful.
 */
@AutoService(AutoValueExtension.class)
public class EmptyExtension extends AutoValueExtension {
  // TODO(emcmanus): it is way too difficult to write a trivial extension. Problems we have here:
  //   (1) We have to generate a constructor that calls the superclass constructor, which means
  //       declaring the appropriate constructor parameters and then forwarding them to a super
  //       call.
  //   (2) We have to avoid generating variable names that are keywords (we append $ here
  //       to avoid that).
  //   (3) We have to concoct appropriate type parameter strings, for example
  //       final class AutoValue_Foo<K extends Comparable<K>, V> extends $AutoValue_Foo<K, V>.
  //   These problems show up with the template approach here, but also using JavaPoet as the
  //   Memoize extension does.
  private static final ImmutableList<String> TEMPLATE_LINES =
      ImmutableList.of(
          "package $package;",
          "\n",
          "#if ($isFinal) final #end class ${className}${formalTypes}"
              + " extends ${classToExtend}${actualTypes} {\n",
          "  ${className}(",
          "    #foreach ($property in $propertyTypes.keySet())",
          "    $propertyTypes[$property] ${property}$ #if ($foreach.hasNext) , #end",
          "    #end",
          "  ) {",
          "    super(",
          "      #foreach ($property in $propertyTypes.keySet())",
          "      ${property}$ #if ($foreach.hasNext) , #end",
          "      #end",
          "    );",
          "  }",
          "}");

  @Override
  public boolean applicable(Context context) {
    return true;
  }

  @Override
  public String generateClass(
      Context context, String className, String classToExtend, boolean isFinal) {
    String templateString = Joiner.on('\n').join(TEMPLATE_LINES);
    StringReader templateReader = new StringReader(templateString);
    Template template;
    try {
      template = Template.parseFrom(templateReader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    TypeElement autoValueClass = context.autoValueClass();
    ImmutableMap<String, Object> vars =
        ImmutableMap.<String, Object>builder()
            .put("package", context.packageName())
            .put("className", className)
            .put("classToExtend", classToExtend)
            .put("isFinal", isFinal)
            .put("propertyTypes", context.propertyTypes())
            .put("formalTypes", formalTypeParametersString(autoValueClass))
            .put("actualTypes", actualTypeParametersString(autoValueClass))
            .build();
    return template.evaluate(vars);
  }

  private static String actualTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    }
    return typeParameters.stream()
        .map(e -> e.getSimpleName().toString())
        .collect(joining(", ", "<", ">"));
  }

  private static String formalTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("<");
    String sep = "";
    for (TypeParameterElement typeParameter : typeParameters) {
      sb.append(sep);
      sep = ", ";
      appendTypeParameterWithBounds(typeParameter, sb);
    }
    return sb.append(">").toString();
  }

  private static void appendTypeParameterWithBounds(
      TypeParameterElement typeParameter, StringBuilder sb) {
    sb.append(typeParameter.getSimpleName());
    String sep = " extends ";
    for (TypeMirror bound : typeParameter.getBounds()) {
      if (!bound.toString().equals("java.lang.Object")) {
        sb.append(sep);
        sep = " & ";
        sb.append(bound);
      }
    }
  }
}
