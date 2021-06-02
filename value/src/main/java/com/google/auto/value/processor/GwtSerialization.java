/*
 * Copyright 2014 Google LLC
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.processor.AutoValueishProcessor.GetterProperty;
import com.google.auto.value.processor.PropertyBuilderClassifier.PropertyBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.escapevelocity.Template;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Generates GWT serialization code for {@code @AutoValue} classes also marked
 * {@code @GwtCompatible(serializable = true)}.
 *
 * @author Éamonn McManus
 */
class GwtSerialization {
  private final GwtCompatibility gwtCompatibility;
  private final ProcessingEnvironment processingEnv;
  private final TypeElement type;

  GwtSerialization(
      GwtCompatibility gwtCompatibility, ProcessingEnvironment processingEnv, TypeElement type) {
    this.gwtCompatibility = gwtCompatibility;
    this.processingEnv = processingEnv;
    this.type = type;
  }

  private boolean shouldWriteGwtSerializer() {
    Optional<AnnotationMirror> optionalGwtCompatible = gwtCompatibility.gwtCompatibleAnnotation();
    if (optionalGwtCompatible.isPresent()) {
      AnnotationMirror gwtCompatible = optionalGwtCompatible.get();
      for (Map.Entry<ExecutableElement, AnnotationValue> entry :
          GwtCompatibility.getElementValues(gwtCompatible).entrySet()) {
        if (entry.getKey().getSimpleName().contentEquals("serializable")
            && entry.getValue().getValue().equals(true)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Writes the GWT serializer for the given type, if appropriate. An {@code @AutoValue} class gets
   * a GWT serializer if it is annotated with {@code @GwtCompatible(serializable = true)}, where the
   * {@code @GwtCompatible} annotation can come from any package.
   *
   * <p>If the type is com.example.Foo then the generated AutoValue subclass is
   * com.example.AutoValue_Foo and the GWT serializer is
   * com.example.AutoValue_Foo_CustomFieldSerializer.
   *
   * @param autoVars the template variables defined for this type.
   * @param finalSubclass the simple name of the AutoValue class being generated, AutoValue_Foo
   *     in the example.
   */
  void maybeWriteGwtSerializer(AutoValueTemplateVars autoVars, String finalSubclass) {
    if (shouldWriteGwtSerializer()) {
      GwtTemplateVars vars = new GwtTemplateVars();
      vars.pkg = autoVars.pkg;
      vars.subclass = finalSubclass;
      vars.formalTypes = autoVars.formalTypes;
      vars.actualTypes = autoVars.actualTypes;
      vars.useBuilder = !autoVars.builderTypeName.isEmpty();
      vars.builderSetters = autoVars.builderSetters;
      vars.builderPropertyBuilders = autoVars.builderPropertyBuilders;
      vars.generated = autoVars.generated;
      String className =
          (vars.pkg.isEmpty() ? "" : vars.pkg + ".") + vars.subclass + "_CustomFieldSerializer";
      vars.serializerClass = TypeSimplifier.simpleNameOf(className);
      vars.props =
          autoVars.props.stream().map(p -> new Property((GetterProperty) p)).collect(toList());
      vars.classHashString = computeClassHash(autoVars.props, vars.pkg);
      String text = vars.toText();
      text = TypeEncoder.decode(text, processingEnv, vars.pkg, type.asType());
      writeSourceFile(className, text, type);
    }
  }

  public static class Property {
    private final GetterProperty property;
    private final boolean isCastingUnchecked;

    Property(GetterProperty property) {
      this.property = property;
      this.isCastingUnchecked = TypeSimplifier.isCastingUnchecked(property.getTypeMirror());
    }

    @Override
    public String toString() {
      return property.toString();
    }

    public String getGetter() {
      return property.getGetter();
    }

    public String getType() {
      return property.getType();
    }

    public String getName() {
      return property.getName();
    }

    /**
     * Returns the suffix in serializer method names for values of the given type. For example, if
     * the type is "int" then the returned value will be "Int" because the serializer methods are
     * called readInt and writeInt. There are methods for all primitive types and String; every
     * other type uses readObject and writeObject.
     */
    public String getGwtType() {
      TypeMirror typeMirror = property.getTypeMirror();
      String type = typeMirror.toString();
      if (property.getKind().isPrimitive()) {
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
      } else if (type.equals("java.lang.String")) {
        return "String";
      } else {
        return "Object";
      }
    }

    /**
     * Returns a string to be inserted before the call to the readFoo() call so that the expression
     * can be assigned to the given type. For primitive types and String, the readInt() etc methods
     * already return the right type so the string is empty. For other types, the string is a cast
     * like "(Foo) ".
     */
    public String getGwtCast() {
      if (property.getKind().isPrimitive() || getType().equals("String")) {
        return "";
      } else {
        return "(" + getType() + ") ";
      }
    }

    public boolean isCastingUnchecked() {
      return isCastingUnchecked;
    }
  }

  @SuppressWarnings("unused") // some fields are only read through reflection
  static class GwtTemplateVars extends TemplateVars {
    /** The properties defined by the parent class's abstract methods. */
    List<Property> props;

    /**
     * The package of the class with the {@code @AutoValue} annotation and its generated subclass.
     */
    String pkg;

    /** The simple name of the generated subclass. */
    String subclass;

    /**
     * The formal generic signature of the class with the {@code @AutoValue} annotation and its
     * generated subclass. This is empty, or contains type variables with optional bounds, for
     * example {@code <K, V extends K>}.
     */
    String formalTypes;
    /**
     * The generic signature used by the generated subclass for its superclass reference. This is
     * empty, or contains only type variables with no bounds, for example {@code <K, V>}.
     */
    String actualTypes;

    /** True if the {@code @AutoValue} class is constructed using a generated builder. */
    Boolean useBuilder;

    /**
     * A multimap from property names (like foo) to the corresponding setter methods (foo or
     * setFoo).
     */
    Multimap<String, BuilderSpec.PropertySetter> builderSetters;

    /**
     * A map from property names to information about the associated property builder. A property
     * called foo (defined by a method foo() or getFoo()) can have a property builder called
     * fooBuilder(). The type of foo must be a type that has an associated builder following certain
     * conventions. Guava immutable types such as ImmutableList follow those conventions, as do many
     * {@code @AutoValue} types.
     */
    ImmutableMap<String, PropertyBuilder> builderPropertyBuilders = ImmutableMap.of();

    /** The simple name of the generated GWT serializer class. */
    String serializerClass;

    /**
     * The encoding of the {@code Generated} class. Empty if no {@code Generated} class is
     * available.
     */
    String generated;

    /** A string that should change if any salient details of the serialized class change. */
    String classHashString;

    private static final Template TEMPLATE = parsedTemplateForResource("gwtserializer.vm");

    @Override
    Template parsedTemplate() {
      return TEMPLATE;
    }
  }

  private void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      try (Writer writer = sourceFile.openWriter()) {
        writer.write(text);
      }
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.WARNING, "Could not write generated class " + className + ": " + e);
      // A warning rather than an error for the reason explained in
      // AutoValueishProcessor.writeSourceFile.
    }
  }

  // Compute a hash that is guaranteed to change if the names, types, or order of the fields
  // change. We use TypeEncoder so that we can get a defined string for types, since
  // TypeMirror.toString() isn't guaranteed to remain the same.
  private String computeClassHash(Iterable<AutoValueishProcessor.Property> props, String pkg) {
    CRC32 crc = new CRC32();
    String encodedType = TypeEncoder.encode(type.asType()) + ":";
    String decodedType = TypeEncoder.decode(encodedType, processingEnv, "", null);
    if (!decodedType.startsWith(pkg)) {
      // This is for compatibility with the way an earlier version did things. Preserving hash
      // codes probably isn't vital, since client and server should be in sync.
      decodedType = pkg + "." + decodedType;
    }
    crc.update(decodedType.getBytes(UTF_8));
    for (AutoValueishProcessor.Property prop : props) {
      String encodedProp = prop + ":" + TypeEncoder.encode(prop.getTypeMirror()) + ";";
      String decodedProp = TypeEncoder.decode(encodedProp, processingEnv, pkg, null);
      crc.update(decodedProp.getBytes(UTF_8));
    }
    return String.format("%08x", crc.getValue());
  }
}
