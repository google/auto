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
package com.google.auto.value.processor;

import com.google.common.collect.ImmutableListMultimap;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An ultrasimplified Java parser for {@link EclipseHack} that examines classes to extract just
 * the abstract methods. The parsing is very superficial. It assumes that the source text is
 * syntactically correct, which it must be in the context of an annotation processor because the
 * compiler doesn't invoke the processor if there are syntax errors.
 *
 * <p>We recognize the text {@code ... class Foo ... { ... } } as a class called Foo, whose
 * definition extends to the matching right brace. Within a class definition, we recognize the text
 * {@code abstract ... bar ( ) } as an abstract method called bar. We also recognize {@code ...
 * interface Foo ... { ... } } so that we can discover {@code @AutoValue} classes that are nested
 * in an interface.
 *
 * <p>We construct a {@code Map<String, List<String>>} that represents the abstract methods found in
 * each class, in the order they were found. If com.example.Foo contains a nested class Bar, then
 * there will be an entry for "com.example.Foo.Bar" in this Map.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class AbstractMethodExtractor {
  AbstractMethodExtractor() { }

  // Here are the details of the matching. We track the current brace depth, and we artificially
  // consider that the whole file is at brace depth 1 inside a pseudo-class whose name is the
  // name of the package. When we see a class definition, we push the fully-qualified name of the
  // class on a stack so that we can associate abstract methods we find with the possibly-nested
  // class they belong to. A class definition must occur at brace depth one more than the class
  // containing it, which is equivalent to saying that the brace depth must be the same as the
  // class stack depth. This check excludes local class definitions within methods and
  // initializers. If we meet these constraints and we see the word "class" followed by an
  // identifier Foo, then we consider that we are entering the definition of class Foo. We determine
  // the fully-qualified name of Foo, which is container.Foo, where container is the current top of
  // the class stack (initially, the package name). We push this new fully-qualified name on the
  // class stack. We have not yet seen the left brace with the class definition so at this point the
  // class stack depth is one more than the brace depth. When we subsequently see a right brace that
  // takes us back to this situation then we know we have completed the definition of Foo and we can
  // pop it from the class stack.
  //
  // We check that the token after "class" is indeed an identifier to avoid confusion
  // with Foo.class. Even though the tokenizer does not distinguish between identifiers and
  // keywords, it is enough to exclude the single word "instanceof" because that is the only word
  // that can legally appear after Foo.class (though in a legal program the resultant expression
  // will always be true).
  //
  // Again, we are at the top level of a class when the brace depth is equal to the class stack
  // depth. If we then see the word "abstract" then that is the start either of an abstract class
  // definition or of an abstract method. We record that we have seen "abstract" and we cancel
  // that indication as soon as we see a left brace, to exclude the abstract class case, and also
  // the case of interfaces or @interfaces redundantly declared abstract. Now, when
  // we see an identifier that is preceded by an uncanceled "abstract" and followed by a left paren
  // then we have found an abstract method of the class on the top of the class stack. We record it
  // in the list of abstract methods of the class on the top of the class stack. We don't bother
  // checking that the method has no parameters, because an @AutoValue class will cause a compiler
  // error if there are abstract methods with parameters, since the @AutoValue processor doesn't
  // know how to implement them in the concrete subclass it generates.
  ImmutableListMultimap<String, String> abstractMethods(
      EclipseHackTokenizer tokenizer, String packageName) {
    ImmutableListMultimap.Builder<String, String> abstractMethods = ImmutableListMultimap.builder();
    Deque<String> classStack = new ArrayDeque<String>();
    classStack.addLast(packageName);
    int braceDepth = 1;
    boolean sawAbstract = false;
    String className = null;
    for (String previousToken = "", token = tokenizer.nextToken();
        token != null;
        previousToken = token, token = tokenizer.nextToken()) {
      boolean topLevel = (braceDepth == classStack.size());
      if (className != null) {
        if (Character.isJavaIdentifierStart(className.charAt(0))
            && !className.equals("instanceof")) {
          String container = classStack.getLast();
          // container might be empty in the case of a packageless class
          classStack.add(container.isEmpty() ? className : container + "." + className);
        }
        className = null;
      }
      if (token.equals("{")) {
        braceDepth++;
        sawAbstract = false;
      } else if (token.equals("}")) {
        braceDepth--;
        if (topLevel) {
          classStack.removeLast();
        }
      } else if (topLevel) {
        if (token.equals("class") || token.equals("interface")) {
          className = tokenizer.nextToken();
        } else if (token.equals("abstract")) {
          sawAbstract = true;
        } else if (token.equals("(")) {
          if (sawAbstract && Character.isJavaIdentifierStart(previousToken.charAt(0))) {
            abstractMethods.put(classStack.getLast(), previousToken);
          }
          sawAbstract = false;
        }
      }
    }
    return abstractMethods.build();
  }
}
