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
package com.google.auto.value;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A class file parser that lists the no-arg abstract methods in a class.
 *
 * @author Éamonn McManus
 */
class AbstractMethodLister {
  private final InputStream inputStream;

  AbstractMethodLister(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  List<String> abstractNoArgMethods() {
    try {
      return abstractNoArgMethodsX();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<String> abstractNoArgMethodsX() throws IOException {
    ClassReader classReader = new ClassReader(inputStream);
    RecordingClassVisitor classVisitor = new RecordingClassVisitor();
    classReader.accept(classVisitor, 0);
    return classVisitor.abstractNoArgMethods;
  }

  private static class RecordingClassVisitor extends ClassVisitor {
    private final List<String> abstractNoArgMethods = new ArrayList<String>();

    RecordingClassVisitor() {
      super(Opcodes.ASM4);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      // The class-file method descriptor desc is a string that will contain "()" only if the
      // method has no arguments, and will end with "V" (actually "()V") only if the method
      // is void.
      if (Modifier.isAbstract(access) && desc.contains("()") && !desc.endsWith("V")) {
        abstractNoArgMethods.add(name);
      }
      return super.visitMethod(access, name, desc, signature, exceptions);
    }
  }
}