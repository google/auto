/*
 * Copyright 2012 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.testing.compile.CompilationRule;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TypeSimplifier}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class TypeSimplifierTest {
  @Rule public final CompilationRule compilationRule = new CompilationRule();

  private Types typeUtils;
  private Elements elementUtils;

  @Before
  public void setUp() {
    typeUtils = compilationRule.getTypes();
    elementUtils = compilationRule.getElements();
  }

  private static class Erasure<T> {
    int intNo;
    boolean booleanNo;
    int[] intArrayNo;
    String stringNo;
    String[] stringArrayNo;

    @SuppressWarnings("rawtypes")
    List rawListNo;

    List<?> listOfQueryNo;
    List<? extends Object> listOfQueryExtendsObjectNo;
    Map<?, ?> mapQueryToQueryNo;

    List<String> listOfStringYes;
    List<? extends String> listOfQueryExtendsStringYes;
    List<? super String> listOfQuerySuperStringYes;
    List<T> listOfTypeVarYes;
    List<? extends T> listOfQueryExtendsTypeVarYes;
    List<? super T> listOfQuerySuperTypeVarYes;
  }

  private abstract static class Wildcards {
    abstract <T extends V, U extends T, V> Map<? extends T, ? super U> one();

    abstract <T extends V, U extends T, V> Map<? extends T, ? super U> two();
  }

  /**
   * This test shows why we need to have TypeMirrorSet. The mirror of java.lang.Object obtained from
   * {@link Elements#getTypeElement Elements.getTypeElement("java.lang.Object")} does not compare
   * equal to the mirror of the return type of Object.clone(), even though that is also
   * java.lang.Object and {@link Types#isSameType} considers them the same.
   *
   * <p>There's no requirement that this test must pass and if it starts failing or doesn't work in
   * another test environment then we can delete it. The specification of {@link TypeMirror#equals}
   * explicitly says that it cannot be used for type equality, so even if this particular case stops
   * being a problem (which means this test would fail), we would need TypeMirrorSet for complete
   * correctness.
   */
  @Test
  public void testQuirkyTypeMirrors() {
    TypeMirror objectMirror = objectMirror();
    TypeMirror cloneReturnTypeMirror = cloneReturnTypeMirror();
    assertThat(objectMirror).isNotEqualTo(cloneReturnTypeMirror);
    assertThat(typeUtils.isSameType(objectMirror, cloneReturnTypeMirror)).isTrue();
  }

  @Test
  @SuppressWarnings("TypeEquals") // We want to test the equals method invocation on TypeMirror.
  public void testTypeMirrorSet() {
    // Test the TypeMirrorSet methods. Resist the temptation to rewrite these in terms of
    // Truth operations! For example, don't change assertThat(set.size()).isEqualTo(0) into
    // assertThat(set).isEmpty(), because then we wouldn't be testing size().
    TypeMirror objectMirror = objectMirror();
    TypeMirror otherObjectMirror = cloneReturnTypeMirror();
    Set<TypeMirror> set = new TypeMirrorSet();
    assertThat(set.size()).isEqualTo(0);
    assertThat(set.isEmpty()).isTrue();
    boolean added = set.add(objectMirror);
    assertThat(added).isTrue();
    assertThat(set.size()).isEqualTo(1);

    Set<TypeMirror> otherSet = typeMirrorSet(otherObjectMirror);
    assertThat(otherSet).isEqualTo(set);
    assertThat(set).isEqualTo(otherSet);
    assertThat(otherSet.hashCode()).isEqualTo(set.hashCode());

    assertThat(set.add(otherObjectMirror)).isFalse();
    assertThat(set.contains(otherObjectMirror)).isTrue();

    assertThat(set.contains(null)).isFalse();
    assertThat(set.contains((Object) "foo")).isFalse();
    assertThat(set.remove(null)).isFalse();
    assertThat(set.remove((Object) "foo")).isFalse();

    TypeElement list = typeElementOf(java.util.List.class);
    TypeMirror listOfObjectMirror = typeUtils.getDeclaredType(list, objectMirror);
    TypeMirror listOfOtherObjectMirror = typeUtils.getDeclaredType(list, otherObjectMirror);
    assertThat(listOfObjectMirror.equals(listOfOtherObjectMirror)).isFalse();
    assertThat(typeUtils.isSameType(listOfObjectMirror, listOfOtherObjectMirror)).isTrue();
    added = set.add(listOfObjectMirror);
    assertThat(added).isTrue();
    assertThat(set.size()).isEqualTo(2);
    assertThat(set.add(listOfOtherObjectMirror)).isFalse();
    assertThat(set.contains(listOfOtherObjectMirror)).isTrue();

    boolean removed = set.remove(listOfOtherObjectMirror);
    assertThat(removed).isTrue();
    assertThat(set.contains(listOfObjectMirror)).isFalse();

    set.removeAll(otherSet);
    assertThat(set.isEmpty()).isTrue();
  }

  @Test
  public void testTypeMirrorSetWildcardCapture() {
    // TODO(emcmanus): this test should really be in MoreTypesTest.
    // This test checks the assumption made by MoreTypes that you can find the
    // upper bounds of a TypeVariable tv like this:
    //   TypeParameterElement tpe = (TypeParameterElement) tv.asElement();
    //   List<? extends TypeMirror> bounds = tpe.getBounds();
    // There was some doubt as to whether this would be true in exotic cases involving
    // wildcard capture, but apparently it is.
    // The methods one and two here have identical signatures:
    //   abstract <T extends V, U extends T, V> Map<? extends T, ? super U> name();
    // Their return types should be considered equal by TypeMirrorSet. The capture of
    // each return type is different from the original return type, but the two captures
    // should compare equal to each other. We also add various other types like ? super U
    // to the set to ensure that the implied calls to the equals and hashCode visitors
    // don't cause a ClassCastException for TypeParameterElement.
    TypeElement wildcardsElement = typeElementOf(Wildcards.class);
    List<? extends ExecutableElement> methods =
        ElementFilter.methodsIn(wildcardsElement.getEnclosedElements());
    assertThat(methods).hasSize(2);
    ExecutableElement one = methods.get(0);
    ExecutableElement two = methods.get(1);
    assertThat(one.getSimpleName().toString()).isEqualTo("one");
    assertThat(two.getSimpleName().toString()).isEqualTo("two");
    TypeMirrorSet typeMirrorSet = new TypeMirrorSet();
    assertThat(typeMirrorSet.add(one.getReturnType())).isTrue();
    assertThat(typeMirrorSet.add(two.getReturnType())).isFalse();
    DeclaredType captureOne = (DeclaredType) typeUtils.capture(one.getReturnType());
    assertThat(typeMirrorSet.add(captureOne)).isTrue();
    DeclaredType captureTwo = (DeclaredType) typeUtils.capture(two.getReturnType());
    assertThat(typeMirrorSet.add(captureTwo)).isFalse();
    // Reminder: captureOne is Map<?#123 extends T, ?#456 super U>
    TypeVariable extendsT = (TypeVariable) captureOne.getTypeArguments().get(0);
    assertThat(typeMirrorSet.add(extendsT)).isTrue();
    assertThat(typeMirrorSet.add(extendsT.getLowerBound())).isTrue(); // NoType
    for (TypeMirror bound : ((TypeParameterElement) extendsT.asElement()).getBounds()) {
      assertThat(typeMirrorSet.add(bound)).isTrue();
    }
    TypeVariable superU = (TypeVariable) captureOne.getTypeArguments().get(1);
    assertThat(typeMirrorSet.add(superU)).isTrue();
    assertThat(typeMirrorSet.add(superU.getLowerBound())).isTrue();
  }

  @Test
  public void testPackageNameOfString() {
    assertThat(TypeSimplifier.packageNameOf(typeElementOf(java.lang.String.class)))
        .isEqualTo("java.lang");
  }

  @Test
  public void testPackageNameOfMapEntry() {
    assertThat(TypeSimplifier.packageNameOf(typeElementOf(java.util.Map.Entry.class)))
        .isEqualTo("java.util");
  }

  // Test TypeSimplifier.isCastingUnchecked. We do this by examining the fields of the Erasure
  // class. A field whose name ends with Yes has a type where
  // isCastingUnchecked should return true, and one whose name ends with No has a type where
  // isCastingUnchecked should return false.
  @Test
  public void testIsCastingUnchecked() {
    TypeElement erasureClass = typeElementOf(Erasure.class);
    List<VariableElement> fields = ElementFilter.fieldsIn(erasureClass.getEnclosedElements());
    assertThat(fields).isNotEmpty();
    for (VariableElement field : fields) {
      String fieldName = field.getSimpleName().toString();
      boolean expectUnchecked;
      if (fieldName.endsWith("Yes")) {
        expectUnchecked = true;
      } else if (fieldName.endsWith("No")) {
        expectUnchecked = false;
      } else {
        throw new AssertionError("Fields in Erasure class must end with Yes or No: " + fieldName);
      }
      TypeMirror fieldType = field.asType();
      boolean actualUnchecked = TypeSimplifier.isCastingUnchecked(fieldType);
      assertWithMessage("Unchecked-cast status for " + fieldType)
          .that(actualUnchecked)
          .isEqualTo(expectUnchecked);
    }
  }

  private static Set<TypeMirror> typeMirrorSet(TypeMirror... typeMirrors) {
    Set<TypeMirror> set = new TypeMirrorSet();
    for (TypeMirror typeMirror : typeMirrors) {
      assertThat(set.add(typeMirror)).isTrue();
    }
    return set;
  }

  private TypeMirror objectMirror() {
    return typeMirrorOf(Object.class);
  }

  private TypeMirror cloneReturnTypeMirror() {
    TypeElement object = typeElementOf(Object.class);
    ExecutableElement clone = null;
    for (Element element : object.getEnclosedElements()) {
      if (element.getSimpleName().contentEquals("clone")) {
        clone = (ExecutableElement) element;
        break;
      }
    }
    return clone.getReturnType();
  }

  private TypeElement typeElementOf(Class<?> c) {
    return elementUtils.getTypeElement(c.getCanonicalName());
  }

  private TypeMirror typeMirrorOf(Class<?> c) {
    return typeElementOf(c).asType();
  }
}
