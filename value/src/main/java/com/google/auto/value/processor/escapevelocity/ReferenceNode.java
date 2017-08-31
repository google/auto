/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.google.auto.value.processor.escapevelocity;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A node in the parse tree that is a reference. A reference is anything beginning with {@code $},
 * such as {@code $x} or {@code $x[$i].foo($j)}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class ReferenceNode extends ExpressionNode {
  ReferenceNode(int lineNumber) {
    super(lineNumber);
  }

  /**
   * A node in the parse tree that is a plain reference such as {@code $x}. This node may appear
   * inside a more complex reference like {@code $x.foo}.
   */
  static class PlainReferenceNode extends ReferenceNode {
    final String id;

    PlainReferenceNode(int lineNumber, String id) {
      super(lineNumber);
      this.id = id;
    }

    @Override Object evaluate(EvaluationContext context) {
      if (context.varIsDefined(id)) {
        return context.getVar(id);
      } else {
        throw new EvaluationException("Undefined reference $" + id);
      }
    }

    @Override
    boolean isDefinedAndTrue(EvaluationContext context) {
      if (context.varIsDefined(id)) {
        return isTrue(context);
      } else {
        return false;
      }
    }
  }

  /**
   * A node in the parse tree that is a reference to a property of another reference, like
   * {@code $x.foo} or {@code $x[$i].foo}.
   */
  static class MemberReferenceNode extends ReferenceNode {
    final ReferenceNode lhs;
    final String id;

    MemberReferenceNode(ReferenceNode lhs, String id) {
      super(lhs.lineNumber);
      this.lhs = lhs;
      this.id = id;
    }

    private static final String[] PREFIXES = {"get", "is"};
    private static final boolean[] CHANGE_CASE = {false, true};

    @Override Object evaluate(EvaluationContext context) {
      Object lhsValue = lhs.evaluate(context);
      if (lhsValue == null) {
        throw new EvaluationException("Cannot get member " + id + " of null value");
      }
      // Velocity specifies that, given a reference .foo, it will first look for getfoo() and then
      // for getFoo(), and likewise given .Foo it will look for getFoo() and then getfoo().
      for (String prefix : PREFIXES) {
        for (boolean changeCase : CHANGE_CASE) {
          String baseId = changeCase ? changeInitialCase(id) : id;
          String methodName = prefix + baseId;
          Method method;
          try {
            method = lhsValue.getClass().getMethod(methodName);
            if (!prefix.equals("is") || method.getReturnType().equals(boolean.class)) {
              // Don't consider methods that happen to be called isFoo() but don't return boolean.
              return invokeMethod(method, lhsValue, ImmutableList.of());
            }
          } catch (NoSuchMethodException e) {
            // Continue with next possibility
          }
        }
      }
      throw new EvaluationException(
          "Member " + id + " does not correspond to a public getter of " + lhsValue
              + ", a " + lhsValue.getClass().getName());
    }

    private static String changeInitialCase(String id) {
      int initial = id.codePointAt(0);
      String rest = id.substring(Character.charCount(initial));
      if (Character.isUpperCase(initial)) {
        initial = Character.toLowerCase(initial);
      } else if (Character.isLowerCase(initial)) {
        initial = Character.toUpperCase(initial);
      }
      return new StringBuilder().appendCodePoint(initial).append(rest).toString();
    }
  }

  /**
   * A node in the parse tree that is an indexing of a reference, like {@code $x[0]} or
   * {@code $x.foo[$i]}. Indexing is array indexing or calling the {@code get} method of a list
   * or a map.
   */
  static class IndexReferenceNode extends ReferenceNode {
    final ReferenceNode lhs;
    final ExpressionNode index;

    IndexReferenceNode(ReferenceNode lhs, ExpressionNode index) {
      super(lhs.lineNumber);
      this.lhs = lhs;
      this.index = index;
    }

    @Override Object evaluate(EvaluationContext context) {
      Object lhsValue = lhs.evaluate(context);
      if (lhsValue == null) {
        throw new EvaluationException("Cannot index null value");
      }
      if (lhsValue instanceof List<?>) {
        Object indexValue = index.evaluate(context);
        if (!(indexValue instanceof Integer)) {
          throw new EvaluationException("List index is not an integer: " + indexValue);
        }
        List<?> lhsList = (List<?>) lhsValue;
        int i = (Integer) indexValue;
        if (i < 0 || i >= lhsList.size()) {
          throw new EvaluationException(
              "List index " + i + " is not valid for list of size " + lhsList.size());
        }
        return lhsList.get(i);
      } else if (lhsValue instanceof Map<?, ?>) {
        Object indexValue = index.evaluate(context);
        Map<?, ?> lhsMap = (Map<?, ?>) lhsValue;
        return lhsMap.get(indexValue);
      } else {
        // In general, $x[$y] is equivalent to $x.get($y). We've covered the most common cases
        // above, but for other cases like Multimap we resort to evaluating the equivalent form.
        MethodReferenceNode node = new MethodReferenceNode(lhs, "get", ImmutableList.of(index));
        return node.evaluate(context);
      }
    }
  }

  /**
   * A node in the parse tree representing a method reference, like {@code $list.size()}.
   */
  static class MethodReferenceNode extends ReferenceNode {
    final ReferenceNode lhs;
    final String id;
    final List<ExpressionNode> args;

    MethodReferenceNode(ReferenceNode lhs, String id, List<ExpressionNode> args) {
      super(lhs.lineNumber);
      this.lhs = lhs;
      this.id = id;
      this.args = args;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Evaluating a method expression such as {@code $x.foo($y)} involves looking at the actual
     * types of {@code $x} and {@code $y}. The type of {@code $x} must have a public method
     * {@code foo} with a parameter type that is compatible with {@code $y}.
     *
     * <p>Currently we don't allow there to be more than one matching method. That is a difference
     * from Velocity, which blithely allows you to invoke {@link List#remove(int)} even though it
     * can't really know that you didn't mean to invoke {@link List#remove(Object)} with an Object
     * that just happens to be an Integer.
     *
     * <p>The method to be invoked must be visible in a public class or interface that is either the
     * class of {@code $x} itself or one of its supertypes. Allowing supertypes is important because
     * you may want to invoke a public method like {@link List#size()} on a list whose class is not
     * public, such as the list returned by {@link java.util.Collections#singletonList}.
     */
    @Override Object evaluate(EvaluationContext context) {
      Object lhsValue = lhs.evaluate(context);
      if (lhsValue == null) {
        throw evaluationException("Cannot invoke method " + id + " on null value");
      }
      List<Object> argValues = new ArrayList<Object>();
      for (ExpressionNode arg : args) {
        argValues.add(arg.evaluate(context));
      }
      List<Method> methodsWithName = Lists.newArrayList();
      for (Method method : lhsValue.getClass().getMethods()) {
        if (method.getName().equals(id) && !method.isSynthetic()) {
          methodsWithName.add(method);
        }
      }
      if (methodsWithName.isEmpty()) {
        throw evaluationException("No method " + id + " in " + lhsValue.getClass().getName());
      }
      List<Method> compatibleMethods = Lists.newArrayList();
      for (Method method : methodsWithName) {
        // TODO(emcmanus): support varargs, if it's useful
        if (compatibleArgs(method.getParameterTypes(), argValues)) {
          compatibleMethods.add(method);
        }
      }
      switch (compatibleMethods.size()) {
        case 0:
          throw evaluationException(
              "Parameters for method " + id + " have wrong types: " + argValues);
        case 1:
          return invokeMethod(Iterables.getOnlyElement(compatibleMethods), lhsValue, argValues);
        default:
          throw evaluationException(
              "Ambiguous method invocation, could be one of:"
              + Joiner.on('\n').join(compatibleMethods));
      }
    }

    /**
     * Determines if the given argument list is compatible with the given parameter types. This
     * includes an {@code Integer} argument being compatible with a parameter of type {@code int} or
     * {@code long}, for example.
     */
    static boolean compatibleArgs(Class<?>[] paramTypes, List<Object> argValues) {
      if (paramTypes.length != argValues.size()) {
        return false;
      }
      for (int i = 0; i < paramTypes.length; i++) {
        Class<?> paramType = paramTypes[i];
        Object argValue = argValues.get(i);
        if (paramType.isPrimitive()) {
          return primitiveIsCompatible(paramType, argValue);
        } else if (!paramType.isInstance(argValue)) {
          return false;
        }
      }
      return true;
    }

    private static boolean primitiveIsCompatible(Class<?> primitive, Object value) {
      if (value == null || !Primitives.isWrapperType(value.getClass())) {
        return false;
      }
      return primitiveTypeIsAssignmentCompatible(primitive, Primitives.unwrap(value.getClass()));
    }

    private static final ImmutableList<Class<?>> NUMERICAL_PRIMITIVES = ImmutableList.<Class<?>>of(
        byte.class, short.class, int.class, long.class, float.class, double.class);
    private static final int INDEX_OF_INT = NUMERICAL_PRIMITIVES.indexOf(int.class);

    /**
     * Returns true if {@code from} can be assigned to {@code to} according to
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.1.2">Widening
     * Primitive Conversion</a>.
     */
    static boolean primitiveTypeIsAssignmentCompatible(Class<?> to, Class<?> from) {
      // To restate the JLS rules, f can be assigned to t if:
      // - they are the same; or
      // - f is char and t is a numeric type at least as wide as int; or
      // - f comes before t in the order byte, short, int, long, float, double.
      if (to == from) {
        return true;
      }
      int toI = NUMERICAL_PRIMITIVES.indexOf(to);
      if (toI < 0) {
        return false;
      }
      if (from == char.class) {
        return toI >= INDEX_OF_INT;
      }
      int fromI = NUMERICAL_PRIMITIVES.indexOf(from);
      if (fromI < 0) {
        return false;
      }
      return toI >= fromI;
    }
  }

  /**
   * Invoke the given method on the given target with the given arguments. The method is expected
   * to be public, but the class it is in might not be. In that case we will search up the
   * hierarchy for an ancestor that is public and has the same method, and use that to invoke the
   * method. Otherwise we would get an {@link IllegalAccessException}. More than one ancestor might
   * define the method, but it doesn't matter which one we invoke since ultimately the code that
   * will run will be the same.
   */
  Object invokeMethod(Method method, Object target, List<Object> argValues) {
    if (!classIsPublic(target.getClass())) {
      method = visibleMethod(method, target.getClass());
      if (method == null) {
        throw evaluationException(
            "Method is not visible in class " + target.getClass().getName() + ": " + method);
      }
    }
    try {
      return method.invoke(target, argValues.toArray());
    } catch (InvocationTargetException e) {
      throw evaluationException(e.getCause());
    } catch (Exception e) {
      throw evaluationException(e);
    }
  }

  private static String packageNameOf(Class<?> c) {
    String name = c.getName();
    int lastDot = name.lastIndexOf('.');
    if (lastDot > 0) {
      return name.substring(0, lastDot);
    } else {
      return "";
    }
  }

  private static final String THIS_PACKAGE = packageNameOf(Node.class) + ".";

  /**
   * Returns a Method with the same name and parameter types as the given one, but that is in a
   * public class or interface. This might be the given method, or it might be a method in a
   * superclass or superinterface.
   *
   * @return a public method in a public class or interface, or null if none was found.
   */
  static Method visibleMethod(Method method, Class<?> in) {
    if (in == null) {
      return null;
    }
    Method methodInClass;
    try {
      methodInClass = in.getMethod(method.getName(), method.getParameterTypes());
    } catch (NoSuchMethodException e) {
      return null;
    }
    if (classIsPublic(in) || in.getName().startsWith(THIS_PACKAGE)) {
      // The second disjunct is a hack to allow us to use the methods of $foreach without having
      // to make the ForEachVar class public. We can invoke those methods from here since they
      // are in the same package.
      return methodInClass;
    }
    Method methodSuper = visibleMethod(method, in.getSuperclass());
    if (methodSuper != null) {
      return methodSuper;
    }
    for (Class<?> intf : in.getInterfaces()) {
      Method methodIntf = visibleMethod(method, intf);
      if (methodIntf != null) {
        return methodIntf;
      }
    }
    return null;
  }

  /**
   * Returns whether the given class is public as seen from this class. Prior to Java 9, a class
   * was either public or not public. But with the introduction of modules in Java 9, a class can
   * be marked public and yet not be visible, if it is not exported from the module it appears in.
   * So, on Java 9, we perform an additional check on class {@code c}, which is effectively
   * {@code c.getModule().isExported(c.getPackageName())}. We use reflection so that the code can
   * compile on earlier Java versions.
   */
  private static boolean classIsPublic(Class<?> c) {
    if (!Modifier.isPublic(c.getModifiers())) {
      return false;
    }
    if (CLASS_GET_MODULE_METHOD != null) {
      return classIsExported(c);
    }
    return true;
  }

  private static boolean classIsExported(Class<?> c) {
    try {
      String pkg = packageNameOf(c);
      Object module = CLASS_GET_MODULE_METHOD.invoke(c);
      return (Boolean) MODULE_IS_EXPORTED_METHOD.invoke(module, pkg);
    } catch (Exception e) {
      return false;
    }
  }

  private static final Method CLASS_GET_MODULE_METHOD;
  private static final Method MODULE_IS_EXPORTED_METHOD;

  static {
    Method classGetModuleMethod;
    Method moduleIsExportedMethod;
    try {
      classGetModuleMethod = Class.class.getMethod("getModule");
      Class<?> moduleClass = classGetModuleMethod.getReturnType();
      moduleIsExportedMethod = moduleClass.getMethod("isExported", String.class);
    } catch (Exception e) {
      classGetModuleMethod = null;
      moduleIsExportedMethod = null;
    }
    CLASS_GET_MODULE_METHOD = classGetModuleMethod;
    MODULE_IS_EXPORTED_METHOD = moduleIsExportedMethod;
  }
}
