/*
 * Copyright 2021 Google LLC
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

package com.google.auto.value.extension.toprettystring.processor;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.auto.value.extension.toprettystring.processor.ExtensionClassTypeSpecBuilder.extensionClassTypeSpecBuilder;
import static com.google.auto.value.extension.toprettystring.processor.ToPrettyStringMethods.toPrettyStringMethod;
import static com.google.auto.value.extension.toprettystring.processor.ToPrettyStringMethods.toPrettyStringMethods;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.intersection;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.toprettystring.processor.ToPrettyStringExtension.PrettyPrintableKind.KindVisitor;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

/**
 * Generates implementations of {@link
 * com.google.auto.value.extension.toprettystring.ToPrettyString} annotated methods in {@link
 * com.google.auto.value.AutoValue} types.
 */
@AutoService(AutoValueExtension.class)
public final class ToPrettyStringExtension extends AutoValueExtension {
  private static final ImmutableSet<Modifier> INHERITED_VISIBILITY_MODIFIERS =
      ImmutableSet.of(PUBLIC, PROTECTED);
  private static final String INDENT = "  ";
  private static final String INDENT_METHOD_NAME = "$indent";
  private static final CodeBlock KEY_VALUE_SEPARATOR = CodeBlock.of("$S", ": ");

  @Override
  public String generateClass(
      Context context, String className, String classToExtend, boolean isFinal) {
    TypeSpec type =
        extensionClassTypeSpecBuilder(context, className, classToExtend, isFinal)
            .addMethods(toPrettyStringMethodSpecs(context))
            .build();
    return JavaFile.builder(context.packageName(), type)
        .skipJavaLangImports(true)
        .build()
        .toString();
  }

  private ImmutableList<MethodSpec> toPrettyStringMethodSpecs(Context context) {
    ExecutableElement toPrettyStringMethod = getOnlyElement(toPrettyStringMethods(context));
    MethodSpec.Builder method =
        methodBuilder(toPrettyStringMethod.getSimpleName().toString())
            .addAnnotation(Override.class)
            .returns(ClassName.get(String.class))
            .addModifiers(FINAL)
            .addModifiers(
                intersection(toPrettyStringMethod.getModifiers(), INHERITED_VISIBILITY_MODIFIERS));

    method.addCode("return $S", context.autoValueClass().getSimpleName() + " {");
    ToPrettyStringImplementation implementation = ToPrettyStringImplementation.create(context);
    method.addCode(implementation.toStringCodeBlock.build());

    if (!context.properties().isEmpty()) {
      method.addCode(" + $S", "\n");
    }
    method.addCode(" + $S;\n", "}");

    return ImmutableList.<MethodSpec>builder()
        .add(method.build())
        .addAll(implementation.delegateMethods.values())
        .add(indentMethod())
        .build();
  }

  private static MethodSpec indentMethod() {
    return methodBuilder(INDENT_METHOD_NAME)
        .addModifiers(PRIVATE, STATIC)
        .returns(ClassName.get(String.class))
        .addParameter(TypeName.INT, "level")
        .addStatement("$1T builder = new $1T()", StringBuilder.class)
        .beginControlFlow("for (int i = 0; i < level; i++)")
        .addStatement("builder.append($S)", INDENT)
        .endControlFlow()
        .addStatement("return builder.toString()")
        .build();
  }

  private static class ToPrettyStringImplementation {
    private final Types types;
    private final Elements elements;

    private final CodeBlock.Builder toStringCodeBlock = CodeBlock.builder();
    private final Map<Equivalence.Wrapper<TypeMirror>, MethodSpec> delegateMethods =
        new LinkedHashMap<>();
    private final Set<String> methodNames = new HashSet<>();

    private ToPrettyStringImplementation(Context context) {
      this.types = context.processingEnvironment().getTypeUtils();
      this.elements = context.processingEnvironment().getElementUtils();
      // do not submit: what about "inherited" static methods?
      getLocalAndInheritedMethods(context.autoValueClass(), types, elements)
          .forEach(method -> methodNames.add(method.getSimpleName().toString()));
    }

    static ToPrettyStringImplementation create(Context context) {
      ToPrettyStringImplementation implemention = new ToPrettyStringImplementation(context);
      context
          .propertyTypes()
          .forEach(
              (propertyName, type) -> {
                String methodName =
                    context.properties().get(propertyName).getSimpleName().toString();
                implemention.toStringCodeBlock.add(
                    "\n + $S + $L + $S",
                    String.format("\n%s%s = ", INDENT, propertyName),
                    implemention.format(CodeBlock.of("$N()", methodName), CodeBlock.of("1"), type),
                    ",");
              });
      return implemention;
    }

    /**
     * Returns {@code propertyAccess} formatted for use within the {@link
     * com.google.auto.value.extension.toprettystring.ToPrettyString} implementation.
     *
     * <p>If a helper method is necessary for formatting, a {@link MethodSpec} will be added to
     * {@link #delegateMethods}.
     *
     * @param propertyAccess a reference to the variable that should be formatted.
     * @param indentAccess a reference to an {@code int} representing how many indent levels should
     *     be used for this property.
     * @param type the type of the {@code propertyAccess}.
     */
    private CodeBlock format(CodeBlock propertyAccess, CodeBlock indentAccess, TypeMirror type) {
      PrettyPrintableKind printableKind = type.accept(new KindVisitor(types, elements), null);
      DelegateMethod delegateMethod = new DelegateMethod(propertyAccess, indentAccess);
      switch (printableKind) {
        case PRIMITIVE:
          return propertyAccess;
        case REGULAR_OBJECT:
          return delegateMethod
              .methodName("format")
              .invocation(
                  elements.getTypeElement("java.lang.Object").asType(), () -> reindent("toString"));
        case HAS_TO_PRETTY_STRING_METHOD:
          ExecutableElement method =
              toPrettyStringMethod(asTypeElement(type), types, elements).get();
          return delegateMethod.invocation(type, () -> reindent(method.getSimpleName()));
        case ARRAY:
          TypeMirror componentType = MoreTypes.asArray(type).getComponentType();
          return delegateMethod.invocation(type, () -> forEachLoopMethodBody(componentType));
        case COLLECTION:
          TypeMirror elementType =
              getOnlyElement(resolvedTypeParameters(type, "java.util.Collection"));
          return delegateMethod.invocation(
              collectionOf(elementType), () -> forEachLoopMethodBody(elementType));
        case IMMUTABLE_PRIMITIVE_ARRAY:
          return delegateMethod.invocation(type, this::forLoopMethodBody);
        case OPTIONAL:
        case GUAVA_OPTIONAL:
          TypeMirror optionalType = getOnlyElement(MoreTypes.asDeclared(type).getTypeArguments());
          return delegateMethod.invocation(
              type, () -> optionalMethodBody(optionalType, printableKind));
        case MAP:
          return formatMap(type, delegateMethod);
        case MULTIMAP:
          return formatMultimap(type, delegateMethod);
      }
      throw new AssertionError(printableKind);
    }

    private CodeBlock formatMap(TypeMirror type, DelegateMethod delegateMethod) {
      ImmutableList<TypeMirror> typeParameters = resolvedTypeParameters(type, "java.util.Map");
      TypeMirror keyType = typeParameters.get(0);
      TypeMirror valueType = typeParameters.get(1);
      return delegateMethod.invocation(
          mapOf(keyType, valueType), () -> mapMethodBody(keyType, valueType));
    }

    private CodeBlock formatMultimap(TypeMirror type, DelegateMethod delegateMethod) {
      ImmutableList<TypeMirror> typeParameters =
          resolvedTypeParameters(type, "com.google.common.collect.Multimap");
      TypeMirror keyType = typeParameters.get(0);
      TypeMirror valueType = typeParameters.get(1);
      return delegateMethod.invocation(
          multimapOf(keyType, valueType),
          () -> multimapMethodBody(keyType, collectionOf(valueType)));
    }

    /**
     * Parameter object to simplify the branches of {@link #format(CodeBlock, CodeBlock,
     * TypeMirror)} that call a delegate method.
     */
    private class DelegateMethod {

      private final CodeBlock propertyAccess;
      private final CodeBlock indentAccess;
      private Optional<String> methodName = Optional.empty();

      DelegateMethod(CodeBlock propertyAccess, CodeBlock indentAccess) {
        this.propertyAccess = propertyAccess;
        this.indentAccess = indentAccess;
      }

      DelegateMethod methodName(String methodName) {
        this.methodName = Optional.of(methodName);
        return this;
      }

      CodeBlock invocation(TypeMirror parameterType, Supplier<CodeBlock> methodBody) {
        Equivalence.Wrapper<TypeMirror> key = MoreTypes.equivalence().wrap(parameterType);
        // This doesn't use putIfAbsent because the methodBody supplier could recursively create
        // new delegate methods. Map.putIfAbsent doesn't support reentrant calls.
        if (!delegateMethods.containsKey(key)) {
          delegateMethods.put(
              key,
              createMethod(
                  methodName.orElseGet(() -> newDelegateMethodName(parameterType)),
                  parameterType,
                  methodBody));
        }
        return CodeBlock.of(
            "$N($L, $L)", delegateMethods.get(key).name, propertyAccess, indentAccess);
      }

      private String newDelegateMethodName(TypeMirror type) {
        String prefix = "format" + nameForType(type);
        String methodName = prefix;
        for (int i = 2; !methodNames.add(methodName); i++) {
          methodName = prefix + i;
        }
        return methodName;
      }

      private MethodSpec createMethod(
          String methodName, TypeMirror type, Supplier<CodeBlock> methodBody) {
        return methodBuilder(methodName)
            .addModifiers(PRIVATE, STATIC)
            .returns(ClassName.get(String.class))
            .addParameter(TypeName.get(type), "value")
            .addParameter(TypeName.INT, "indentLevel")
            .beginControlFlow("if (value == null)")
            .addStatement("return $S", "null")
            .endControlFlow()
            .addCode(methodBody.get())
            .build();
      }
    }

    private CodeBlock reindent(CharSequence methodName) {
      return CodeBlock.builder()
          .addStatement(
              "return value.$1N().replace($2S, $2S + $3N(indentLevel))",
              methodName,
              "\n",
              INDENT_METHOD_NAME)
          .build();
    }

    private CodeBlock forEachLoopMethodBody(TypeMirror elementType) {
      return loopMethodBody(
          "[",
          "]",
          CodeBlock.of("for ($T element : value)", elementType),
          format(CodeBlock.of("element"), CodeBlock.of("indentLevel + 1"), elementType));
    }

    private CodeBlock forLoopMethodBody() {
      return loopMethodBody(
          "[",
          "]",
          CodeBlock.of("for (int i = 0; i < value.length(); i++)"),
          CodeBlock.of("value.get(i)"));
    }

    private CodeBlock mapMethodBody(TypeMirror keyType, TypeMirror valueType) {
      return forEachMapEntryMethodBody(keyType, valueType, "value");
    }

    private CodeBlock multimapMethodBody(TypeMirror keyType, TypeMirror valueType) {
      return forEachMapEntryMethodBody(keyType, valueType, "value.asMap()");
    }

    private CodeBlock forEachMapEntryMethodBody(
        TypeMirror keyType, TypeMirror valueType, String propertyAccess) {
      CodeBlock entryType = CodeBlock.of("$T<$T, $T>", Map.Entry.class, keyType, valueType);
      return loopMethodBody(
          "{",
          "}",
          CodeBlock.of("for ($L entry : $L.entrySet())", entryType, propertyAccess),
          format(CodeBlock.of("entry.getKey()"), CodeBlock.of("indentLevel + 1"), keyType),
          KEY_VALUE_SEPARATOR,
          format(CodeBlock.of("entry.getValue()"), CodeBlock.of("indentLevel + 1"), valueType));
    }

    private CodeBlock loopMethodBody(
        String openSymbol,
        String closeSymbol,
        CodeBlock loopDeclaration,
        CodeBlock... appendedValues) {
      ImmutableList<CodeBlock> allAppendedValues =
          ImmutableList.<CodeBlock>builder()
              .add(CodeBlock.of("$S", "\n"))
              .add(CodeBlock.of("$N(indentLevel + 1)", INDENT_METHOD_NAME))
              .add(appendedValues)
              .add(CodeBlock.of("$S", ","))
              .build();
      return CodeBlock.builder()
          .addStatement("$1T builder = new $1T().append($2S)", StringBuilder.class, openSymbol)
          .addStatement("boolean hasElements = false")
          .beginControlFlow("$L", loopDeclaration)
          .addStatement(
              "builder$L",
              allAppendedValues.stream()
                  .map(value -> CodeBlock.of(".append($L)", value))
                  .collect(CodeBlock.joining("")))
          .addStatement("hasElements = true")
          .endControlFlow()
          .beginControlFlow("if (hasElements)")
          .addStatement("builder.append($S).append($N(indentLevel))", "\n", INDENT_METHOD_NAME)
          .endControlFlow()
          .addStatement("return builder.append($S).toString()", closeSymbol)
          .build();
    }

    private CodeBlock optionalMethodBody(
        TypeMirror optionalType, PrettyPrintableKind printableKind) {
      return CodeBlock.builder()
          .addStatement(
              "return (value.isPresent() ? $L : $S)",
              format(CodeBlock.of("value.get()"), CodeBlock.of("indentLevel"), optionalType),
              printableKind.equals(PrettyPrintableKind.OPTIONAL) ? "<empty>" : "<absent>")
          .build();
    }

    private ImmutableList<TypeMirror> resolvedTypeParameters(
        TypeMirror propertyType, String interfaceName) {
      return elements.getTypeElement(interfaceName).getTypeParameters().stream()
          .map(p -> types.asMemberOf(MoreTypes.asDeclared(propertyType), p))
          .collect(toImmutableList());
    }

    private DeclaredType collectionOf(TypeMirror elementType) {
      return types.getDeclaredType(elements.getTypeElement("java.util.Collection"), elementType);
    }

    private DeclaredType mapOf(TypeMirror keyType, TypeMirror valueType) {
      return types.getDeclaredType(elements.getTypeElement("java.util.Map"), keyType, valueType);
    }

    private DeclaredType multimapOf(TypeMirror keyType, TypeMirror valueType) {
      return types.getDeclaredType(
          elements.getTypeElement("com.google.common.collect.Multimap"), keyType, valueType);
    }

    /** Returns a valid Java identifier for method or variable of type {@code type}. */
    private String nameForType(TypeMirror type) {
      return type.accept(
          new SimpleTypeVisitor8<String, Void>() {
            @Override
            public String visitDeclared(DeclaredType type, Void v) {
              String simpleName = simpleNameForType(type);
              if (type.getTypeArguments().isEmpty()) {
                return simpleName;
              }
              ImmutableList<String> typeArgumentNames =
                  type.getTypeArguments().stream()
                      .map(t -> simpleNameForType(t))
                      .collect(toImmutableList());
              if (isMapOrMultimap(type) && typeArgumentNames.size() == 2) {
                return String.format(
                    "%sOf%sTo%s", simpleName, typeArgumentNames.get(0), typeArgumentNames.get(1));
              }

              List<String> parts = new ArrayList<>();
              parts.add(simpleName);
              parts.add("Of");
              parts.addAll(typeArgumentNames.subList(0, typeArgumentNames.size() - 1));
              if (typeArgumentNames.size() > 1) {
                parts.add("And");
              }
              parts.add(getLast(typeArgumentNames));
              return String.join("", parts);
            }

            @Override
            protected String defaultAction(TypeMirror type, Void v) {
              return simpleNameForType(type);
            }
          },
          null);
    }

    boolean isMapOrMultimap(TypeMirror type) {
      TypeMirror mapType = elements.getTypeElement("java.util.Map").asType();
      if (types.isAssignable(type, types.erasure(mapType))) {
        return true;
      }
      TypeElement multimapElement = elements.getTypeElement("com.google.common.collect.Multimap");
      return multimapElement != null
          && types.isAssignable(type, types.erasure(multimapElement.asType()));
    }

    private String simpleNameForType(TypeMirror type) {
      return type.accept(
          new SimpleTypeVisitor8<String, Void>() {
            @Override
            public String visitPrimitive(PrimitiveType primitiveType, Void v) {
              return types.boxedClass(primitiveType).getSimpleName().toString();
            }

            @Override
            public String visitArray(ArrayType arrayType, Void v) {
              return arrayType.getComponentType().accept(this, null) + "Array";
            }

            @Override
            public String visitDeclared(DeclaredType declaredType, Void v) {
              return declaredType.asElement().getSimpleName().toString();
            }

            @Override
            protected String defaultAction(TypeMirror typeMirror, Void v) {
              throw new AssertionError(typeMirror);
            }
          },
          null);
    }
  }

  enum PrettyPrintableKind {
    HAS_TO_PRETTY_STRING_METHOD,
    REGULAR_OBJECT,
    PRIMITIVE,
    COLLECTION,
    ARRAY,
    IMMUTABLE_PRIMITIVE_ARRAY,
    OPTIONAL,
    GUAVA_OPTIONAL,
    MAP,
    MULTIMAP,
    ;

    private static final ImmutableMap<String, PrettyPrintableKind> KINDS_BY_EXACT_TYPE =
        ImmutableMap.of(
            "java.util.Optional", OPTIONAL,
            "com.google.common.base.Optional", GUAVA_OPTIONAL,
            "com.google.common.primitives.ImmutableIntArray", IMMUTABLE_PRIMITIVE_ARRAY,
            "com.google.common.primitives.ImmutableLongArray", IMMUTABLE_PRIMITIVE_ARRAY,
            "com.google.common.primitives.ImmutableDoubleArray", IMMUTABLE_PRIMITIVE_ARRAY);

    private static final ImmutableMap<String, PrettyPrintableKind> KINDS_BY_SUPERTYPE =
        ImmutableMap.of(
            "java.util.Collection", COLLECTION,
            "java.util.Map", MAP,
            "com.google.common.collect.Multimap", MULTIMAP);

    static class KindVisitor extends SimpleTypeVisitor8<PrettyPrintableKind, Void> {
      private final Elements elements;
      private final Types types;

      KindVisitor(Types types, Elements elements) {
        this.types = types;
        this.elements = elements;
      }

      @Override
      public PrettyPrintableKind visitPrimitive(PrimitiveType primitiveType, Void v) {
        return PRIMITIVE;
      }

      @Override
      public PrettyPrintableKind visitArray(ArrayType arrayType, Void v) {
        return ARRAY;
      }

      @Override
      public PrettyPrintableKind visitDeclared(DeclaredType declaredType, Void v) {
        TypeElement typeElement = asTypeElement(declaredType);
        if (toPrettyStringMethod(typeElement, types, elements).isPresent()) {
          return HAS_TO_PRETTY_STRING_METHOD;
        }
        PrettyPrintableKind byExactType =
            KINDS_BY_EXACT_TYPE.get(typeElement.getQualifiedName().toString());
        if (byExactType != null) {
          return byExactType;
        }

        for (Map.Entry<String, PrettyPrintableKind> entry : KINDS_BY_SUPERTYPE.entrySet()) {
          TypeElement supertypeElement = elements.getTypeElement(entry.getKey());
          if (supertypeElement != null
              && types.isAssignable(declaredType, types.erasure(supertypeElement.asType()))) {
            return entry.getValue();
          }
        }

        return REGULAR_OBJECT;
      }
    }
  }

  @Override
  public boolean applicable(Context context) {
    return toPrettyStringMethods(context).size() == 1;
  }

  @Override
  public ImmutableSet<ExecutableElement> consumeMethods(Context context) {
    return toPrettyStringMethods(context);
  }

  @Override
  public IncrementalExtensionType incrementalType(ProcessingEnvironment processingEnvironment) {
    return IncrementalExtensionType.ISOLATING;
  }
}
