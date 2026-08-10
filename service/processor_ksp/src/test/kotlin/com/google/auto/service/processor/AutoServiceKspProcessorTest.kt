/*
 * Copyright 2025 Google LLC
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

package com.google.auto.service.processor

import androidx.room3.compiler.processing.ExperimentalProcessingApi
import androidx.room3.compiler.processing.util.CompilationResultSubject
import androidx.room3.compiler.processing.util.Source
import androidx.room3.compiler.processing.util.runProcessorTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

/** Tests for the [AutoServiceKspProcessor]. */
@ExperimentalProcessingApi
@RunWith(Parameterized::class)
class AutoServiceKspProcessorTest {

  @Parameter lateinit var sourceKind: SourceKind

  // These tests actually test with both KAPT (using AutoServiceProcessor) and KSP

  private fun compile(
    javaSource: Source,
    kotlinSource: Source,
    onCompilationResult: CompilationResultSubject.() -> Unit,
  ) {
    compile(listOf(javaSource), listOf(kotlinSource), onCompilationResult)
  }

  private fun compile(
    javaSources: List<Source>,
    kotlinSources: List<Source>,
    onCompilationResult: CompilationResultSubject.() -> Unit,
  ) {
    runProcessorTest(
      sources =
        when (sourceKind) {
          SourceKind.JAVA -> javaSources + services
          SourceKind.KOTLIN -> kotlinSources + services
        },
      javacProcessors = listOf(AutoServiceProcessor()),
      symbolProcessorProviders = listOf(AutoServiceKspProcessor.Provider()),
      onCompilationResult = onCompilationResult,
    )
  }

  @Test
  fun singleClass_singleServiceInterface() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;

        @AutoService(Service1.class)
        public final class Foo implements Service1 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import testservice.Service1

        @AutoService(Service1::class)
        class Foo : Service1
        """,
      )
    compile(javaSource, kotlinSource) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo("foo.Foo\n")
    }
  }

  @Test
  fun singleClass_singleServiceInterface_explicitValueArg() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;

        @AutoService(value = {Service1.class})
        public final class Foo implements Service1 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1

          @AutoService(value = [Service1::class])
          class Foo : Service1
          """,
      )
    compile(javaSource, kotlinSource) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo("foo.Foo\n")
    }
  }

  @Test
  fun singleClass_singleServiceInterface_nested() {
    val javaSource =
      Source.java(
        "foo/Outer",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;

        public final class Outer {
          @AutoService(value = {Service1.class})
          public static final class Foo implements Service1 {}
        }
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Outer.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1

          class Outer {
            @AutoService(value = [Service1::class])
            class Foo : Service1
          }
          """,
      )
    compile(javaSource, kotlinSource) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo("foo.Outer\$Foo\n")
    }
  }

  @Test
  fun singleClass_twoServiceInterfaces_varargs() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;
        import testservice.Service2;

        @AutoService({Service1.class, Service2.class})
        public final class Foo implements Service1, Service2 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1
          import testservice.Service2

          @AutoService(Service1::class, Service2::class)
          class Foo : Service1, Service2
          """,
      )
    compile(javaSource, kotlinSource) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo("foo.Foo\n")
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service2")
        .isEqualTo("foo.Foo\n")
    }
  }

  @Test
  fun singleClass_twoServiceInterfaces_explicitValueArg() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;
        import testservice.Service2;

        @AutoService(value = {Service1.class, Service2.class})
        public final class Foo implements Service1, Service2 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1
          import testservice.Service2

          @AutoService(value = [Service1::class, Service2::class])
          class Foo : Service1, Service2
          """,
      )
    compile(javaSource, kotlinSource) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo("foo.Foo\n")
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service2")
        .isEqualTo("foo.Foo\n")
    }
  }

  @Test
  fun singleClass_twoServiceInterfaces_explicitValueArg_arrayOf() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;
        import testservice.Service2;

        @AutoService(value = {Service1.class, Service2.class})
        public final class Foo implements Service1, Service2 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1
          import testservice.Service2

          @AutoService(value = arrayOf(Service1::class, Service2::class))
          class Foo : Service1, Service2
          """,
      )
    compile(javaSource, kotlinSource) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo("foo.Foo\n")
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service2")
        .isEqualTo("foo.Foo\n")
    }
  }

  @Test
  fun twoClasses_twoServiceInterfaces() {
    val javaSources =
      listOf(
        Source.java(
          "foo/Foo",
          """
            package foo;

            import com.google.auto.service.AutoService;
            import testservice.Service1;
            import testservice.Service2;

            @AutoService(value = {Service1.class, Service2.class})
            public final class Foo implements Service1, Service2 {}
            """,
        ),
        Source.java(
          "foo/Bar",
          """
            package foo;

            import com.google.auto.service.AutoService;
            import testservice.Service1;

            @AutoService(Service1.class)
            public final class Bar implements Service1 {}
            """,
        ),
      )
    val kotlinSources =
      listOf(
        Source.kotlin(
          "foo/Foo.kt",
          """
            package foo

            import com.google.auto.service.AutoService
            import testservice.Service1
            import testservice.Service2

            @AutoService(value = [Service1::class, Service2::class])
            class Foo : Service1, Service2
            """,
        ),
        Source.kotlin(
          "foo/Bar.kt",
          """
            package foo

            import com.google.auto.service.AutoService
            import testservice.Service1

            @AutoService(Service1::class)
            class Bar : Service1
            """,
        ),
      )
    compile(javaSources, kotlinSources) {
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service1")
        .isEqualTo(
          """
          |foo.Bar
          |foo.Foo
          |"""
            .trimMargin()
        )
      generatedTextResourceFileWithPath("META-INF/services/testservice.Service2")
        .isEqualTo("foo.Foo\n")
    }
  }

  @Test
  fun error_doesNotImplementServiceInterface() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;
        import testservice.Service2;

        @AutoService(Service1.class)
        public final class Foo implements Service2 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1
          import testservice.Service2

          @AutoService(Service1::class)
          class Foo : Service2
          """,
      )
    compile(javaSource, kotlinSource) {
      hasErrorContaining("ServiceProviders must implement their service provider interface.")
    }
  }

  @Test
  fun error_abstractClass() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;

        @AutoService(Service1.class)
        public abstract class Foo implements Service1 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1

          @AutoService(Service1::class)
          abstract class Foo : Service1
          """,
      )
    compile(javaSource, kotlinSource) {
      hasErrorContaining("@AutoService can only be applied to a concrete class")
        .onSource(
          when (sourceKind) {
            SourceKind.JAVA -> javaSource
            SourceKind.KOTLIN -> kotlinSource
          }
        )
        .onLineContaining("@AutoService")
    }
  }

  @Test
  fun error_interface() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;

        @AutoService(Service1.class)
        public interface Foo extends Service1 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1

          @AutoService(Service1::class)
          interface Foo : Service1
          """,
      )
    compile(javaSource, kotlinSource) {
      hasErrorContaining("@AutoService can only be applied to a concrete class")
        .onSource(
          when (sourceKind) {
            SourceKind.JAVA -> javaSource
            SourceKind.KOTLIN -> kotlinSource
          }
        )
        .onLineContaining("@AutoService")
    }
  }

  // TODO(cgdecker): Make AutoServiceProcessor (the KAPT one) pass this test; KSP already does
  /*@Test
  fun error_enum() {
    val foo =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import testservice.Service1

        @AutoService(Service1::class)
        enum class Foo : Service1 {
          BAR,
        }
        """,
      )
    compile(foo) {
      hasErrorContaining("@AutoService can only be applied to a concrete class")
    }
  }*/

  // TODO(cgdecker): AutoService should probably fail for inner classes but does not currently
  /*@Test
  fun error_innerClass() {
    val foo =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import testservice.Service1

        class Outer {
          @AutoService(value = [Service1::class])
          inner class Foo : Service1
        }
        """,
      )
    compile(foo) {
      hasErrorContaining("inner")
    }
  }*/

  @Test
  fun error_noServiceInterface() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.Service1;

        @AutoService({})
        public class Foo implements Service1 {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
          package foo

          import com.google.auto.service.AutoService
          import testservice.Service1

          @AutoService
          class Foo : Service1
          """,
      )
    compile(javaSource, kotlinSource) {
      hasErrorContaining("No service interfaces provided for element!")
    }
  }

  @Test
  fun warning_genericServiceInterface() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.GenericService;

        @AutoService(GenericService.class)
        public class Foo implements GenericService<String> {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import testservice.GenericService

        @AutoService(GenericService::class)
        class Foo : GenericService<String>
        """,
      )
    compile(javaSource, kotlinSource) {
      hasWarningContaining(
        "Service provider testservice.GenericService is generic, so it can't be named exactly by " +
          "@AutoService."
      )
    }
  }

  @Test
  fun warning_genericServiceInterface_suppressed() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.GenericService;

        @SuppressWarnings("rawtypes")
        @AutoService(GenericService.class)
        public final class Foo implements GenericService<String> {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import testservice.GenericService

        // Ideally we should be able to use @Suppress here rather than @SuppressWarnings, but the
        // Java AutoServiceProcessor doesn't support that currently so the KAPT version of this
        // compilation outputs the warning anyway.
        @SuppressWarnings("rawtypes")
        @AutoService(GenericService::class)
        class Foo : GenericService<String>
        """,
      )
    compile(javaSource, kotlinSource) { hasWarningCount(0) }
  }

  @Test
  fun error_noServiceInterfaces_singleErrorType() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import doesnotexist.DoesNotExist;

        @AutoService(DoesNotExist.class)
        public final class Foo implements DoesNotExist {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import doesnotexist.DoesNotExist

        @AutoService(DoesNotExist::class)
        class Foo : DoesNotExist
        """,
      )
    compile(javaSource, kotlinSource) { hasErrorContaining("No service interfaces") }
  }

  @Test
  fun error_fromCompiler_serviceInterfacesIncludesMissingType() {
    val javaSource =
      Source.java(
        "foo/Foo",
        """
        package foo;

        import com.google.auto.service.AutoService;
        import testservice.DoesNotExist;
        import testservice.Service1;

        @AutoService({Service1.class, DoesNotExist.class})
        public final class Foo implements Service1, DoesNotExist {}
        """,
      )
    val kotlinSource =
      Source.kotlin(
        "foo/Foo.kt",
        """
        package foo

        import com.google.auto.service.AutoService
        import testservice.DoesNotExist
        import testservice.Service1

        @AutoService(Service1::class, DoesNotExist::class)
        class Foo : Service1, DoesNotExist
        """,
      )
    compile(javaSource, kotlinSource) {
      // It's a little awkward to try to check that what we're getting here is just compiler errors,
      // especially since the form of the error differs depending on whether it's Java source or
      // Kotlin source. Ideally we'd check that there are _not_ any errors reported by our
      // processors. Note that if one of our processors actually throws an exception rather than
      // just reporting an error, that exception will actually propagate out of the compile() call
      // and fail the test.
      hasErrorContaining("DoesNotExist")
    }
  }

  companion object {
    private val service1 =
      Source.kotlin(
        "testservice/Service1.kt",
        """
        package testservice

        interface Service1
        """,
      )
    private val service2 =
      Source.kotlin(
        "testservice/Service2.kt",
        """
        package testservice

        interface Service2
        """,
      )
    private val genericService =
      Source.kotlin(
        "testservice/GenericService.kt",
        """
        package testservice

        interface GenericService<T>
        """,
      )

    private val services = listOf(service1, service2, genericService)

    enum class SourceKind {
      JAVA,
      KOTLIN,
    }

    @JvmStatic
    @Parameters(name = "{0}")
    fun sourceKinds() = listOf(arrayOf(SourceKind.JAVA), arrayOf(SourceKind.KOTLIN))
  }
}
