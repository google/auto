/*
 * Copyright (C) 2015 Google Inc.
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
package com.google.auto.value.extension;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * An AutoValueExtension allows for extra functionality to be created during the generation of an
 * AutoValue class.
 *
 * <p>Extensions are discovered at compile time using the {@link java.util.ServiceLoader} APIs,
 * allowing them to run without any additional annotations. To be found by {@code ServiceLoader}, an
 * extension class must be public with a public no-arg constructor, and its fully-qualified name
 * must appear in a file called {@code
 * META-INF/services/com.google.auto.value.extension.AutoValueExtension} in a jar that is on the
 * compiler's {@code -classpath} or {@code -processorpath}.
 *
 * <p>When the AutoValue processor runs for a class {@code Foo}, it will ask each Extension whether
 * it is {@linkplain #applicable applicable}. Suppose two Extensions reply that they are. Then
 * the processor will generate the AutoValue logic in a direct subclass of {@code Foo}, and it
 * will ask the first Extension to generate a subclass of that, and the second Extension to generate
 * a subclass of the subclass. So we might have this hierarchy:
 *
 * <pre>
 * &#64;AutoValue abstract class Foo {...}                          // the hand-written class
 * abstract class $$AutoValue_Foo extends Foo {...}             // generated by AutoValue processor
 * abstract class $AutoValue_Foo extends $$AutoValue_Foo {...}  // generated by first Extension
 * final class AutoValue_Foo extends $AutoValue_Foo {...}       // generated by second Extension
 * </pre>
 *
 * <p>(The exact naming scheme illustrated here is not fixed and should not be relied on.)
 *
 * <p>If an Extension needs its generated class to be the final class in the inheritance hierarchy,
 * its {@link #mustBeFinal(Context)} method returns true. Only one Extension can return true for a
 * given context. Only generated classes that will be the final class in the inheritance hierarchy
 * can be declared final. All others should be declared abstract.
 *
 * <p>The first generated class in the hierarchy will always be the one generated by the AutoValue
 * processor and the last one will always be the one generated by the Extension that {@code
 * mustBeFinal}, if any. Other than that, the order of the classes in the hierarchy is unspecified.
 * The * last class in the hierarchy is {@code AutoValue_Foo} and that is the one that the
 * {@code Foo} class will reference, for example with {@code new AutoValue_Foo(...)}.
 *
 * <p>Each Extension must also be sure to generate a constructor with arguments corresponding to all
 * properties in {@link com.google.auto.value.extension.AutoValueExtension.Context#properties()}, in
 * order, and to call the superclass constructor with the same arguments. This constructor must have
 * at least package visibility.
 *
 * <p>Because the class generated by the AutoValue processor is at the top of the generated
 * hierarchy, Extensions can override its methods, for example {@code hashCode()},
 * {@code toString()}, or the implementations of the various {@code bar()} property methods.
 */
public abstract class AutoValueExtension {

  /** The context of the generation cycle. */
  public interface Context {

    /**
     * Returns the processing environment of this generation cycle. This can be used, among other
     * things, to produce compilation warnings or errors, using {@link
     * ProcessingEnvironment#getMessager()}.
     */
    ProcessingEnvironment processingEnvironment();

    /** Returns the package name of the classes to be generated. */
    String packageName();

    /**
     * Returns the annotated class that this generation cycle is based on.
     *
     * <p>Given {@code @AutoValue public class Foo {...}}, this will be {@code Foo}.
     */
    TypeElement autoValueClass();

    /**
     * Returns the ordered collection of properties to be generated by AutoValue. Each key is a
     * property name, and the corresponding value is the getter method for that property. For
     * example, if property {@code bar} is defined by {@code abstract String getBar()} then this map
     * will have an entry mapping {@code "bar"} to the {@code ExecutableElement} for {@code
     * getBar()}.
     */
    Map<String, ExecutableElement> properties();

    /**
     * Returns the complete set of abstract methods defined in or inherited by the
     * {@code @AutoValue} class. This includes all methods that define properties (like {@code
     * abstract String getBar()}), any abstract {@code toBuilder()} method, and any other abstract
     * method even if it has been consumed by this or another Extension.
     */
    Set<ExecutableElement> abstractMethods();
  }

  /**
   * Determines whether this Extension applies to the given context.
   *
   * @param context The Context of the code generation for this class.
   * @return true if this Extension should be applied in the given context. If an Extension returns
   *     false for a given class, it will not be called again during the processing of that class.
   */
  public boolean applicable(Context context) {
    return false;
  }

  /**
   * Denotes that the class generated by this Extension must be the final class in the inheritance
   * hierarchy. Only one Extension may be the final class, so this should be used sparingly.
   *
   * @param context the Context of the code generation for this class.
   */
  public boolean mustBeFinal(Context context) {
    return false;
  }

  /**
   * Returns a possibly empty set of property names that this Extension intends to implement. This
   * will prevent AutoValue from generating an implementation, and remove the supplied properties
   * from builders, constructors, {@code toString}, {@code equals}, and {@code hashCode}. The
   * default set returned by this method is empty.
   *
   * <p>Each returned string must be one of the property names in {@link Context#properties()}.
   *
   * <p>Returning a property name from this method is equivalent to returning the property's getter
   * method from {@link #consumeMethods}.
   *
   * <p>For example, Android's {@code Parcelable} interface includes a <a
   * href="http://developer.android.com/reference/android/os/Parcelable.html#describeContents()">method</a>
   * {@code int describeContents()}. Since this is an abstract method with no parameters, by default
   * AutoValue will consider that it defines an {@code int} property called {@code
   * describeContents}. If an {@code @AutoValue} class implements {@code Parcelable} and does not
   * provide an implementation of this method, by default its implementation will include {@code
   * describeContents} in builders, constructors, and so on. But an {@code AutoValueExtension} that
   * understands {@code Parcelable} can instead provide a useful implementation and return a set
   * containing {@code "describeContents"}. Then {@code describeContents} will be omitted from
   * builders and the rest.
   *
   * @param context the Context of the code generation for this class.
   */
  public Set<String> consumeProperties(Context context) {
    return Collections.emptySet();
  }

  /**
   * Returns a possible empty set of abstract methods that this Extension intends to implement. This
   * will prevent AutoValue from generating an implementation, in cases where it would have, and it
   * will also avoid warnings about abstract methods that AutoValue doesn't expect. The default set
   * returned by this method is empty.
   *
   * <p>Each returned method must be one of the abstract methods in {@link
   * Context#abstractMethods()}.
   *
   * <p>For example, Android's {@code Parcelable} interface includes a <a
   * href="http://developer.android.com/reference/android/os/Parcelable.html#writeToParcel(android.os.Parcel,
   * int)">method</a> {@code void writeToParcel(Parcel, int)}. Normally AutoValue would not know
   * what to do with that abstract method. But an {@code AutoValueExtension} that understands {@code
   * Parcelable} can provide a useful implementation and return the {@code writeToParcel} method
   * here. That will prevent a warning about the method from AutoValue.
   *
   * @param context the Context of the code generation for this class.
   */
  public Set<ExecutableElement> consumeMethods(Context context) {
    return Collections.emptySet();
  }

  /**
   * Returns the generated source code of the class named {@code className} to extend {@code
   * classToExtend}, or {@code null} if this extension does not generate a class in the hierarchy.
   * If there is a generated class, it should be final if {@code isFinal} is true; otherwise it
   * should be abstract. The returned string should be a complete Java class definition of the class
   * {@code className} in the package {@link Context#packageName() context.packageName()}.
   *
   * <p>The returned string will typically look like this:
   *
   * <pre>{@code
   * package <package>;
   * ...
   * <finalOrAbstract> class <className> extends <classToExtend> {...}
   * }</pre>
   *
   * <p>Here, {@code <package>} is {@link Context#packageName()}; {@code <finalOrAbstract>} is the
   * keyword {@code final} if {@code isFinal} is true or {@code abstract} otherwise; and {@code
   * <className>} and {@code <classToExtend>} are the values of this method's parameters of the same
   * name.
   *
   * @param context The {@link Context} of the code generation for this class.
   * @param className The simple name of the resulting class. The returned code will be written to a
   *     file named accordingly.
   * @param classToExtend The simple name of the direct parent of the generated class. This could be
   *     the AutoValue generated class, or a class generated as the result of another Extension.
   * @param isFinal True if this class is the last class in the chain, meaning it should be marked
   *     as final. Otherwise it should be marked as abstract.
   * @return The source code of the generated class, or {@code null} if this extension does not
   *     generate a class in the hierarchy.
   */
  public abstract String generateClass(
      Context context, String className, String classToExtend, boolean isFinal);
}
