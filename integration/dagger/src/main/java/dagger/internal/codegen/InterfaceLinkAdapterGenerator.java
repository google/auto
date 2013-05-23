/*
 * Copyright (C) 2013 Square, Inc.
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
package dagger.internal.codegen;

import com.google.autofactory.AbstractGenerator;
import com.google.autofactory.ProcessorJavadocs;
import com.squareup.java.JavaWriter;
import dagger.internal.Binding;
import dagger.internal.Linker;
import java.io.IOException;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Provider;
import javax.lang.model.element.TypeElement;

import static com.squareup.java.JavaWriter.stringLiteral;
import static dagger.internal.plugins.loading.ClassloadingPlugin.INJECT_ADAPTER_SUFFIX;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.util.Arrays.asList;



/**
 * A class that generates a StaticInjection adapter for a given class.
 */
public final class InterfaceLinkAdapterGenerator extends AbstractGenerator {

  InterfaceLinkAdapterGenerator(ProcessingEnvironment env) {
    super(env);
  }

  /**
   * Write a companion class for {@code type} that extends {@link Binding}.
   *
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  void generate(TypeElement type, String implementation) throws IOException {
    String packageName = getPackage(type).getQualifiedName().toString();
    String typeName = type.getQualifiedName().toString();
    String strippedTypeName = strippedTypeName(typeName, packageName);
    String adapterName = adapterName(type, INJECT_ADAPTER_SUFFIX);
    JavaWriter writer = newWriter(adapterName, type);

    writer.emitEndOfLineComment(ProcessorJavadocs.GENERATED_AUTOMATICALLY);
    writer.emitPackage(packageName);
    writer.emitEmptyLine();
    writer.emitImports(asList(
        typeName,
        Set.class.getName(),
        Binding.class.getName(),
        Provider.class.getName()));

    writer.emitEmptyLine();
    writer.emitJavadoc(INTERFACE_LINK_ADAPTER_TYPE, writer.compressType(typeName), implementation);
    writer.beginType(adapterName, "class", PUBLIC | FINAL,
        parameterizedType(Binding.class, typeName),
        parameterizedType(Provider.class, typeName));
    writer.emitField(
        writer.compressType(parameterizedType(Binding.class, typeName)),
        "implementation",
        PRIVATE);

    writer.emitEmptyLine();
    writer.beginMethod(null, adapterName, PUBLIC);

    writer.emitStatement("super(%s, %s, %s, %s.class)",
        stringLiteral(GeneratorKeys.get(type.asType())), null, false, strippedTypeName);
    writer.endMethod();

    writer.emitEmptyLine();
    writer.emitJavadoc(ProcessorJavadocs.ATTACH_METHOD);
    writer.emitAnnotation(Override.class);
    writer.emitAnnotation(SuppressWarnings.class, stringLiteral("unchecked"));
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    writer.emitStatement("%s = (%s) linker.requestBinding(%s, %s.class)",
        "implementation",
        writer.compressType(parameterizedType(Binding.class, typeName)),
        stringLiteral(rawTypeToString(implementation,  packageName, '$')),
        strippedTypeName);

    writer.endMethod();

    writer.emitEmptyLine();
    writer.emitJavadoc(ProcessorJavadocs.GET_DEPENDENCIES_METHOD);
    writer.emitAnnotation(Override.class);
    String setOfBindings = parameterizedType(Set.class, "Binding<?>");
    writer.beginMethod("void", "getDependencies", PUBLIC, setOfBindings, "getBindings",
        setOfBindings, "injectMembersBindings");
    writer.emitStatement("getBindings.add(%s)", "implementation");
    writer.endMethod();

    writer.emitEmptyLine();
    writer.emitJavadoc(ProcessorJavadocs.GET_METHOD, strippedTypeName);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(strippedTypeName, "get", PUBLIC);
    writer.emitStatement("return implementation.get()");
    writer.endMethod();

    writer.endType();
    writer.close();
  }
}
