/*
 * Copyright 2020 Google LLC
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

import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.AutoValueExtension.IncrementalExtensionType;
import com.google.auto.value.extension.memoized.processor.MemoizeExtension;
import com.google.auto.value.extension.serializable.processor.SerializableAutoValueExtension;
import com.google.auto.value.extension.toprettystring.processor.ToPrettyStringExtension;
import com.google.common.collect.ImmutableList;
import javax.annotation.processing.ProcessingEnvironment;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests of Gradle incrementality in the presence of extensions. See
 * <a href="https://docs.gradle.org/5.0/userguide/java_plugin.html#sec:incremental_annotation_processing">
 * incremental annotation processing</a> in the Gradle user guide.
 */
@RunWith(JUnit4.class)
public class IncrementalExtensionTest {
  @Test
  public void builtInExtensionsAreIsolating() {
    ImmutableList<AutoValueExtension> builtInExtensions =
        AutoValueProcessor.extensionsFromLoader(AutoValueProcessor.class.getClassLoader());
    // These are the current built-in extensions. We will update this test if we add more.
    // The (Object) cast is because otherwise the inferred type is Class<?>, and we can't match
    // that <?> with the class literals here. Even if we cast them to Class<?> it will be a
    // different <?>.
    assertThat(builtInExtensions)
        .comparingElementsUsing(transforming(e -> (Object) e.getClass(), "is class"))
        .containsExactly(
            MemoizeExtension.class,
            SerializableAutoValueExtension.class,
            ToPrettyStringExtension.class);

    AutoValueProcessor processor = new AutoValueProcessor(builtInExtensions);
    assertThat(processor.getSupportedOptions())
        .contains(IncrementalAnnotationProcessorType.ISOLATING.getProcessorOption());
  }

  @Test
  public void customExtensionsAreNotIsolatingByDefault() {
    AutoValueExtension nonIsolatingExtension = new NonIsolatingExtension();
    assertThat(nonIsolatingExtension.incrementalType((ProcessingEnvironment) null))
        .isEqualTo(IncrementalExtensionType.UNKNOWN);
    ImmutableList<AutoValueExtension> extensions =
        ImmutableList.<AutoValueExtension>builder()
            .addAll(
                AutoValueProcessor.extensionsFromLoader(AutoValueProcessor.class.getClassLoader()))
            .add(nonIsolatingExtension)
            .build();

    AutoValueProcessor processor = new AutoValueProcessor(extensions);
    assertThat(processor.getSupportedOptions())
        .doesNotContain(IncrementalAnnotationProcessorType.ISOLATING.getProcessorOption());
  }

  @Test
  public void customExtensionsCanBeIsolating() {
    AutoValueExtension isolatingExtension = new IsolatingExtension();
    assertThat(isolatingExtension.incrementalType((ProcessingEnvironment) null))
        .isEqualTo(IncrementalExtensionType.ISOLATING);
    ImmutableList<AutoValueExtension> extensions =
        ImmutableList.<AutoValueExtension>builder()
            .addAll(
                AutoValueProcessor.extensionsFromLoader(AutoValueProcessor.class.getClassLoader()))
            .add(isolatingExtension)
            .build();

    AutoValueProcessor processor = new AutoValueProcessor(extensions);
    assertThat(processor.getSupportedOptions())
        .contains(IncrementalAnnotationProcessorType.ISOLATING.getProcessorOption());
  }

  // Extensions are "UNKNOWN" by default.
  private static class NonIsolatingExtension extends AutoValueExtension {
    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      return null;
    }
  }

  // Extensions are "ISOLATING" if they say they are.
  private static class IsolatingExtension extends AutoValueExtension {
    @Override
    public IncrementalExtensionType incrementalType(ProcessingEnvironment processingEnvironment) {
      return IncrementalExtensionType.ISOLATING;
    }

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      return null;
    }
  }
}
