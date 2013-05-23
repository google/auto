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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Utility class providing some commonly used boilerplate between {@code InjectProcessor},
 * {@code ProvidesProcessor}, and {@FactoryProcessor}
 */
public final class ProcessorUtils {

  /** A simple representation of the key elements of an injected type. */
  static class InjectedClass {
    final TypeElement type;
    final List<Element> staticFields;
    final ExecutableElement constructor;
    final List<Element> fields;

    InjectedClass(TypeElement type, List<Element> staticFields, ExecutableElement constructor,
        List<Element> fields) {
      this.type = type;
      this.staticFields = staticFields;
      this.constructor = constructor;
      this.fields = fields;
    }

  }

  /**
   * Gather the set of classes that have members annotated by the given {@code annotation}.
   */
  static Set<String> getTypesWithAnnotatedMembers(RoundEnvironment env,
      Class<? extends Annotation> annotation) {
    return getTypesWithAnnotatedMembers(env, annotation, null);
  }

  /**
   * Gather the set of types that have members annotated by the given {@code annotation}
   * except those types with members annotated by the other provided annotation.
   */
  static Set<String> getTypesWithAnnotatedMembers(RoundEnvironment env,
      Class<? extends Annotation> annotation, Class<? extends Annotation> except) {
    Set<String> injectedTypeNames = new LinkedHashSet<String>();
    for (Element element : env.getElementsAnnotatedWith(annotation)) {
      Element typeElement = element.getEnclosingElement();
      if (!shouldInclude(typeElement, except)) continue;
      TypeMirror type = null;
      switch (typeElement.getKind()) {
        case CONSTRUCTOR:
          type = typeElement.getEnclosingElement().asType();
          break;
        case CLASS:
          type = typeElement.asType();
          break;
        default:
          throw new AssertionError("Unsupported element type.");
      }
      injectedTypeNames.add(CodeGen.rawTypeToString(type, '.'));
    }
    return injectedTypeNames;
  }

  private static boolean shouldInclude(Element type, Class<? extends Annotation> except) {
    if (except != null) {
      for (Element e : type.getEnclosedElements()) {
        if (e.getAnnotation(except) != null) return false;
      }
    }
    return true;
  }

  /**
   * Return true if all element types are currently available in this code
   * generation pass. Unavailable types will be of kind {@link TypeKind#ERROR}.
   */
  static boolean allTypesExist(Collection<? extends Element> elements) {
    for (Element element : elements) {
      if (element.asType().getKind() == TypeKind.ERROR) {
        return false;
      }
    }
    return true;
  }

  static boolean allDependenciesPresent(InjectedClass injectedClass) {
    ExecutableElement constructor = injectedClass.constructor;
    Collection<? extends Element> parameters = constructor != null
        ? constructor.getParameters()
        : new ArrayList<Element>();
    return allTypesExist(injectedClass.fields)
      && (allTypesExist(parameters))
      && allTypesExist(injectedClass.staticFields);
  }

  /**
   * @param typeName the name of a class with an @Inject-annotated member.
   */
  static InjectedClass getInjectedClass(
      ProcessingEnvironment env,
      String name,
      Class<? extends Annotation> ... annotations) {
    TypeElement type = env.getElementUtils().getTypeElement(name);
    boolean isAbstract = type.getModifiers().contains(Modifier.ABSTRACT);
    List<Element> staticFields = new ArrayList<Element>();
    ExecutableElement constructor = null;
    List<Element> fields = new ArrayList<Element>();
    for (Element member : type.getEnclosedElements()) {
      switch (member.getKind()) {
        case FIELD:
          if (!annotated(member, annotations)) continue;
          if (member.getModifiers().contains(Modifier.STATIC)) {
            error(env, "Cannot inject static fields on factory-managed value types.");
          } else {
            fields.add(member);
          }
          break;
        case CONSTRUCTOR:
          if (!annotated(member, annotations)) {
            boolean annotated = false;
            for (VariableElement ve : ((ExecutableElement) member).getParameters()) {
              if (annotated(ve, annotations)) {
                annotated = true;
                break;
              }
            }
            if (!annotated) continue;
          }

          if (constructor != null) {
            error(env, "Too many injectable constructors on %s.", type.getQualifiedName());
          } else if (isAbstract) {
            error(env, "Annotated constructors are not supported for abstract class %s.",
                type.getQualifiedName());
          }
          constructor = (ExecutableElement) member;
          break;
        default:
          if (!annotated(member, annotations)) continue;
          error(env, "Cannot inject %s", member);
          break;
      }
    }
    if (constructor == null && !isAbstract) {
      constructor = CodeGen.findNoArgsConstructor(type);
    }
    return new InjectedClass(type, staticFields, constructor, fields);
  }

  private static boolean annotated(Element member, Class<? extends Annotation>... annotations) {
    for (Class<? extends Annotation> annotation : annotations) {
      if (member.getAnnotation(annotation) != null) return true;
    }
    return false;
  }

  protected static void error(ProcessingEnvironment env, String format, Object... args) {
    env.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, args));
  }

  static Set<String> getReturnTypesForMethods(ProcessingEnvironment env, TypeElement type) {
    Set<String> returnTypes = new LinkedHashSet<String>();
    for (Element e : type.getEnclosedElements()) {
      switch (e.getKind()) {
        case METHOD:
          TypeMirror mirror = ((ExecutableElement) e).getReturnType();
          TypeElement returnType = (TypeElement) env.getTypeUtils().asElement(mirror);
          returnTypes.add(returnType.getQualifiedName().toString());
          break;
        case TYPE_PARAMETER:
        case INTERFACE:
        case CLASS:
          continue;
        default:
          throw new AssertionError("Should only be methods on interface " + e);
      }
    }
    return returnTypes;
  }

}
