/*
 * Copyright 2022 Google LLC
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

import static com.google.auto.common.MoreTypes.asArray;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static java.util.stream.Collectors.joining;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.V1_8;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

/**
 * Generates a class that invokes the constructor of another class.
 *
 * <p>The point here is that the constructor might be synthetic, in which case it can't be called
 * directly from Java source code. Say we want to call the constructor {@code ConstructMe(int,
 * String, long)} with parameters {@code 1, "2", 3L}. If the constructor is synthetic, then Java
 * source code can't just do {@code new ConstructMe(1, "2", 3L)}. So this class allows you to
 * generate a class file, say {@code Forwarder}, that is basically what you would get if you could
 * compile this:
 *
 * <pre>{@code
 * final class Forwarder {
 *   private Forwarder() {}
 *
 *   static ConstructMe of(int a, String b, long c) {
 *     return new ConstructMe(a, b, c);
 *   }
 * }
 * }</pre>
 *
 * <p>Because the class file is assembled directly, rather than being produced by the Java compiler,
 * it <i>can</i> call the synthetic constructor. Then regular Java source code can do {@code
 * Forwarder.of(1, "2", 3L)} to call the constructor.
 */
final class ForwardingClassGenerator {
  private final Types typeUtils;

  ForwardingClassGenerator(Types typeUtils) {
    this.typeUtils = typeUtils;
  }

  /**
   * Assembles a class with a static method {@code of} that calls the constructor of another class
   * with the same parameters.
   *
   * <p>It would be simpler if we could just pass in an {@code ExecutableElement} representing the
   * constructor, but if it is synthetic then it won't be visible to the {@code javax.lang.model}
   * APIs. So we have to pass the constructed type and the constructor parameter types separately.
   *
   * @param forwardingClassName the fully-qualified name of the class to generate
   * @param classToConstruct the type whose constructor will be invoked ({@code ConstructMe} in the
   *     example above)
   * @param constructorParameters the types of the constructor parameters, which will also be the
   *     types of the generated {@code of} method
   * @return a byte array making up the new class file
   */
  byte[] makeConstructorForwarder(
      String forwardingClassName,
      DeclaredType classToConstruct,
      ImmutableList<TypeMirror> constructorParameters) {

    ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS);
    classWriter.visit(
        V1_8,
        ACC_FINAL | ACC_SUPER,
        internalName(forwardingClassName),
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource(forwardingClassName, null);

    // Generate the `of` method.
    String parameterSignature =
        constructorParameters.stream()
            .map(typeUtils::erasure)
            .map(ForwardingClassGenerator::signatureEncoding)
            .collect(joining(""));
    String internalClassToConstruct = internalName(asTypeElement(classToConstruct));
    String ofMethodSignature = "(" + parameterSignature + ")L" + internalClassToConstruct + ";";
    MethodVisitor ofMethodVisitor =
        classWriter.visitMethod(
            ACC_STATIC,
            "of",
            ofMethodSignature,
            genericMethodSignature(asTypeElement(classToConstruct), constructorParameters),
            null);
    ofMethodVisitor.visitCode();

    // The remaining instructions are basically what ASMifier generates for a class like the
    // `Forwarder` class in the example above.
    ofMethodVisitor.visitTypeInsn(NEW, internalClassToConstruct);
    ofMethodVisitor.visitInsn(DUP);

    int local = 0;
    for (TypeMirror type : constructorParameters) {
      ofMethodVisitor.visitVarInsn(loadInstruction(type), local);
      local += localSize(type);
    }
    String constructorToCallSignature = "(" + parameterSignature + ")V";
    ofMethodVisitor.visitMethodInsn(
        INVOKESPECIAL,
        internalClassToConstruct,
        "<init>",
        constructorToCallSignature,
        /* isInterface= */ false);

    ofMethodVisitor.visitInsn(ARETURN);
    ofMethodVisitor.visitMaxs(0, 0);
    ofMethodVisitor.visitEnd();
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  /**
   * If the given type is generic, returns a string representing the generic signature of the
   * constructor, which will also be the generic signature of the generated {@code of} method.
   * Otherwise returns null.
   *
   * <p>This is surprisingly complicated. It would be easier, if a bit less clean, just to return
   * the raw type in the {@code of} method and suppress {@code unchecked} warnings at the call site.
   */
  private @Nullable String genericMethodSignature(
      TypeElement type, ImmutableList<TypeMirror> constructorParameters) {
    if (type.getTypeParameters().isEmpty()) {
      return null;
    }
    SignatureWriter writer = new SignatureWriter();
    for (TypeParameterElement param : type.getTypeParameters()) {
      writer.visitFormalTypeParameter(param.getSimpleName().toString());
      for (TypeMirror bound : param.getBounds()) {
        if (typeUtils.asElement(bound).getKind().isClass()) {
          bound.accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, writer.visitClassBound());
        } else {
          bound.accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, writer.visitInterfaceBound());
        }
      }
    }
    for (TypeMirror param : constructorParameters) {
      param.accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, writer.visitParameterType());
    }
    type.asType().accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, writer.visitReturnType());
    return writer.toString();
  }

  private static class TypeMirrorToSignatureVisitor
      extends SimpleTypeVisitor8<Void, SignatureVisitor> {
    @Override
    public Void visitPrimitive(PrimitiveType t, SignatureVisitor v) {
      v.visitBaseType(signatureEncoding(t).charAt(0));
      return null;
    }

    @Override
    public Void visitArray(ArrayType t, SignatureVisitor v) {
      SignatureVisitor componentVisitor = v.visitArrayType();
      t.getComponentType().accept(this, componentVisitor);
      return null;
    }

    @Override
    public Void visitDeclared(DeclaredType t, SignatureVisitor v) {
      TypeElement element = asTypeElement(t);
      v.visitClassType(internalName(element));
      for (TypeMirror arg : t.getTypeArguments()) {
        arg.accept(TypeArgumentVisitor.INSTANCE, v);
      }
      v.visitEnd();
      return null;
    }

    @Override
    public Void visitTypeVariable(TypeVariable t, SignatureVisitor v) {
      v.visitTypeVariable(t.asElement().getSimpleName().toString());
      return null;
    }

    @Override
    protected Void defaultAction(TypeMirror t, SignatureVisitor v) {
      throw new IllegalArgumentException("Unexpected type " + t);
    }
  }

  private static class TypeArgumentVisitor extends SimpleTypeVisitor8<Void, SignatureVisitor> {
    static final TypeArgumentVisitor INSTANCE = new TypeArgumentVisitor();

    @Override
    public Void visitWildcard(WildcardType t, SignatureVisitor v) {
      if (t.getExtendsBound() != null) {
        t.getExtendsBound().accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, v.visitTypeArgument('+'));
      } else if (t.getSuperBound() != null) {
        t.getSuperBound().accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, v.visitTypeArgument('-'));
      } else {
        v.visitTypeArgument();
      }
      return null;
    }

    @Override
    protected Void defaultAction(TypeMirror e, SignatureVisitor v) {
      e.accept(TYPE_MIRROR_TO_SIGNATURE_VISITOR, v.visitTypeArgument('='));
      return null;
    }
  }

  private static final TypeMirrorToSignatureVisitor TYPE_MIRROR_TO_SIGNATURE_VISITOR =
      new TypeMirrorToSignatureVisitor();

  /** The bytecode instruction that copies a parameter of the given type onto the JVM stack. */
  private int loadInstruction(TypeMirror type) {
    switch (typeUtils.erasure(type).getKind()) {
      case DECLARED:
      case ARRAY:
        return ALOAD;
      case LONG:
        return LLOAD;
      case FLOAT:
        return FLOAD;
      case DOUBLE:
        return DLOAD;
      case BYTE:
      case SHORT:
      case CHAR:
      case INT:
      case BOOLEAN:
        // These are all represented as int local variables.
        return ILOAD;
      default:
        // We have erased the TypeMirror so we shouldn't be seeing type variables or whatever.
        throw new IllegalArgumentException("Unexpected type " + type);
    }
  }

  /**
   * The size in the local variable array of a value of the given type. A quirk of the JVM means
   * that long and double variables each take up two consecutive slots in the local variable array.
   * (The first n local variables are the parameters, so we need to know their sizes when iterating
   * over them.)
   */
  private static int localSize(TypeMirror type) {
    switch (type.getKind()) {
      case LONG:
      case DOUBLE:
        return 2;
      default:
        return 1;
    }
  }

  private static String internalName(String className) {
    return className.replace('.', '/');
  }

  /**
   * Given a class like {@code foo.bar.Outer.Inner}, produces a string like {@code
   * "foo/bar/Outer$Inner"}, which is the way the class is referenced in the JVM.
   */
  private static String internalName(TypeElement typeElement) {
    if (typeElement.getNestingKind().equals(NestingKind.MEMBER)) {
      TypeElement enclosing = MoreElements.asType(typeElement.getEnclosingElement());
      return internalName(enclosing) + "$" + typeElement.getSimpleName();
    }
    return internalName(typeElement.getQualifiedName().toString());
  }

  private static String signatureEncoding(TypeMirror type) {
    switch (type.getKind()) {
      case ARRAY:
        return "[" + signatureEncoding(asArray(type).getComponentType());
      case BYTE:
        return "B";
      case SHORT:
        return "S";
      case INT:
        return "I";
      case LONG:
        return "J";
      case FLOAT:
        return "F";
      case DOUBLE:
        return "D";
      case CHAR:
        return "C";
      case BOOLEAN:
        return "Z";
      case DECLARED:
        return "L" + internalName(asTypeElement(type)) + ";";
      default:
        throw new AssertionError("Bad signature type " + type);
    }
  }
}
