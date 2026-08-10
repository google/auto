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

import com.google.common.collect.SortedSetMultimap
import com.google.common.collect.TreeMultimap
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Processes `@AutoService` annotations and generates the service provider configuration files
 * described in [java.util.ServiceLoader].
 */
class AutoServiceKspProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

  private val verify: Boolean = environment.options.getOrDefault("verify", "true") == "true"
  private val debug: Boolean = "debug" in environment.options.keys

  private val logger: KSPLogger = environment.logger

  private val providers: SortedSetMultimap<String, AutoServiceClass> = TreeMultimap.create()

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val services =
      resolver
        .getSymbolsWithAnnotation(AUTO_SERVICE)
        .filterIsInstance<KSClassDeclaration>() // should be all if code compiles
        .filter { it.getName() != null } // should also be all if code compiles
        .map { AutoServiceClass(it) }
        .toList()
    log("@AutoService-annotated classes: $services")
    for (service in services) {
      processService(service)
    }
    return listOf()
  }

  private fun processService(service: AutoServiceClass) {
    log("@AutoService-annotated class: $service", service)
    if (!service.verifyClass()) return

    for (serviceInterface in service.serviceInterfaces) {
      log("service interface: $serviceInterface", service)
      if (service.verifyImplementationOf(serviceInterface)) {
        providers.put(serviceInterface.binaryName, service)
      }
    }
  }

  /** Base type for representation of class or interface types. */
  private abstract class ClassType(type: KSType? = null, declaration: KSClassDeclaration? = null) {
    val type: KSType = type ?: declaration!!.asStarProjectedType()
    val declaration: KSClassDeclaration = declaration ?: type!!.declaration as KSClassDeclaration

    // Should be checked and filtered out before creating an instance of this type
    val name: String = this.declaration.getName()!!
    val binaryName: String = this.declaration.getBinaryName(name)

    val isGeneric: Boolean
      get() = type.arguments.isNotEmpty()

    override fun toString() = name
  }

  /** Simple representation of an `@AutoService`-annotated class. */
  private inner class AutoServiceClass(declaration: KSClassDeclaration) :
    ClassType(declaration = declaration), Comparable<AutoServiceClass> {
    // Since we found this class using resolver.getSymbolsWithAnnotation(AUTO_SERVICE), this
    // _should_ be safe.
    private val annotation: KSAnnotation =
      declaration.annotations.single { it.getName() == AUTO_SERVICE }

    private val suppressions: Set<String> =
      declaration.enclosingElements
        .flatMap { it.annotations }
        .filter { it.getName() in SUPPRESS_ANNOTATIONS }
        .flatMap { it.singleArgAsList<String>().asSequence() }
        .toSet()

    private val isConcreteClass: Boolean
      get() =
        declaration.classKind == ClassKind.CLASS && Modifier.ABSTRACT !in declaration.modifiers

    val serviceInterfaces: List<ServiceInterface> =
      annotation
        .singleArgAsList<KSType>()
        .filterNot {
          // We filter out error types since they cause issues (null names, etc.) and the compiler
          // should fail for them anyway. Arguably we should just stop processing when we see one,
          // but that doesn't seem to match how the APT AutoServiceProcessor handles them (it
          // effectively filters them out) and I haven't figured out how to make it actually behave
          // that way. By doing it this way, both APT and KSP processors filter out error types.
          // This also means that both fail with "no services interfaces provided for element" when
          // there's only a single class argument and it's an error.
          it.isError ||
            // name _probably_ shouldn't be null unless isError is true, but just to be sure...
            it.getName() == null
        }
        .map { ServiceInterface(it) }

    fun suppresses(key: String): Boolean = key in suppressions

    fun verifyClass(): Boolean =
      when {
        !verify || suppresses("AutoService") -> true
        !isConcreteClass -> {
          error("@AutoService can only be applied to a concrete class")
          false
        }
        serviceInterfaces.isEmpty() -> {
          // Arguably we should not error here when the annotation does have arguments but they're
          // all error types, but right now the tests are checking for that behavior. As long as
          // we aren't throwing an exception it's probably not a problem though.
          error("No service interfaces provided for element! arguments = ${annotation.arguments}")
          false
        }
        else -> true
      }

    fun verifyImplementationOf(serviceInterface: ServiceInterface): Boolean {
      if (!serviceInterface.isImplementedBy(this)) {
        error(
          "ServiceProviders must implement their service provider interface. " +
            "$this does not implement $serviceInterface"
        )
        return false
      }

      if (serviceInterface.isGeneric && !suppresses("rawtypes")) {
        warn(
          "Service provider $serviceInterface is generic, so it can't be named exactly by " +
            """@AutoService. If this is OK, add @Suppress("rawtypes")."""
        )
      }
      return true
    }

    private fun error(message: String) {
      logger.error(message, annotation)
    }

    private fun warn(message: String) {
      logger.warn(message, annotation)
    }

    override fun compareTo(other: AutoServiceClass) = binaryName.compareTo(other.binaryName)
  }

  /** Representation of a non-error interface type listed in an `@AutoService` annotation. */
  private class ServiceInterface(type: KSType) : ClassType(type) {
    fun isImplementedBy(impl: AutoServiceClass): Boolean =
      type.starProjection().isAssignableFrom(impl.type)
  }

  override fun finish() {
    val generator = environment.codeGenerator
    for ((serviceInterface, impls) in providers.asMap()) {
      val filePath = "META-INF/services/$serviceInterface"
      log("generating resource file: $filePath")

      // The files containing the @AutoService classes referenced in this META-INF/services file.
      val sourceFiles: Array<KSFile> =
        impls.mapNotNull { it.declaration.containingFile }.distinct().toTypedArray()
      val lines = impls.map { it.binaryName }
      generator
        .createNewFileByPath(
          dependencies = Dependencies(aggregating = false, sources = sourceFiles),
          path = filePath,
          extensionName = "",
        )
        .use { out ->
          out.bufferedWriter(UTF_8).use { writer ->
            for (line in lines) {
              writer.write(line)
              writer.write("\n")
            }
          }
        }
    }
  }

  private fun log(message: Any?, service: AutoServiceClass? = null) {
    if (debug) {
      logger.logging(message.toString(), service?.declaration)
    }
  }

  class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
      AutoServiceKspProcessor(environment)
  }

  companion object {
    private const val AUTO_SERVICE = "com.google.auto.service.AutoService"
    private val SUPPRESS_ANNOTATIONS = setOf("kotlin.Suppress", "java.lang.SuppressWarnings")

    private fun KSClassDeclaration.getName(): String? = qualifiedName?.asString()

    private fun KSType.getName(): String? = (declaration as KSClassDeclaration).getName()

    private fun KSAnnotation.getName(): String? = annotationType.resolve().getName()

    // Note: For java annotations, whether or not KSAnnotation#value returns type T or List<T> is
    // determined by whether or not '{}' brackets are used at the usage site rather than the actual
    // return type declared at the annotation definition. Thus, we need to check both cases here.
    private inline fun <reified T> KSAnnotation.singleArgAsList(): List<T> {
      @Suppress("UNCHECKED_CAST")
      return when (val value = arguments.single().value!!) {
        is List<*> -> value
        is T -> listOf(value)
        else -> {
          val name = T::class.java.simpleName
          throw IllegalStateException(
            "expected annotation value to be $name or List<$name> but was ${value::class}"
          )
        }
      }
        as List<T>
    }

    private fun KSClassDeclaration.getBinaryName(qualifiedName: String): String {
      val packageSeparatorIndex = packageName.asString().length + 1 // plus the '.'
      val nameAfterPackage = qualifiedName.substring(packageSeparatorIndex)
      return "${packageName.asString()}.${nameAfterPackage.replace('.', '$')}"
    }

    private val KSDeclaration.enclosingElements: Sequence<KSAnnotated>
      get() =
        listOfNotNull<KSAnnotated>(containingFile).asSequence() +
          generateSequence<KSDeclaration>(this) { it.parentDeclaration }
  }
}
