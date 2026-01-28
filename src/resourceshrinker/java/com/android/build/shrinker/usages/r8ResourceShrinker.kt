/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("R8ResourceShrinker")

package com.android.build.shrinker.usages

import com.android.tools.r8.ProgramResource
import com.android.tools.r8.ProgramResourceProvider
import com.android.tools.r8.ResourceShrinker
import com.android.tools.r8.origin.PathOrigin
import com.android.tools.r8.references.MethodReference
import java.nio.file.Path
import java.util.function.Consumer

public fun runResourceShrinkerAnalysis(bytes: ByteArray, file: Path, callback: AnalysisCallback) {
  val resource = ProgramResource.fromBytes(PathOrigin(file), ProgramResource.Kind.DEX, bytes, null)
  val provider =
    object : ProgramResourceProvider {
      @Deprecated("Deprecated in Java")
      override fun getProgramResources(): Collection<ProgramResource?> = listOf(resource)

      override fun getProgramResources(consumer: Consumer<ProgramResource>) {
        consumer.accept(resource)
      }
    }

  val command = ResourceShrinker.Builder().addProgramResourceProvider(provider).build()
  ResourceShrinker.run(command, AnalysisAdapter(callback))
}

/** An adapter so R8 API classes do not leak into other modules. */
private class AnalysisAdapter(private val impl: AnalysisCallback) :
  ResourceShrinker.ReferenceChecker {
  override fun shouldProcess(internalName: String): Boolean = impl.shouldProcess(internalName)

  override fun referencedStaticField(internalName: String, fieldName: String) =
    impl.referencedStaticField(internalName, fieldName)

  override fun referencedInt(value: Int) = impl.referencedInt(value)

  override fun referencedString(value: String) = impl.referencedString(value)

  override fun referencedMethod(
    internalName: String,
    methodName: String,
    methodDescriptor: String,
  ) = impl.referencedMethod(internalName, methodName, methodDescriptor)

  override fun startMethodVisit(methodReference: MethodReference) =
    impl.startMethodVisit(methodReference)

  override fun endMethodVisit(methodReference: MethodReference) =
    impl.endMethodVisit(methodReference)
}

public interface AnalysisCallback {

  public fun shouldProcess(internalName: String): Boolean

  public fun referencedInt(value: Int)

  public fun referencedString(value: String)

  public fun referencedStaticField(internalName: String, fieldName: String)

  public fun referencedMethod(internalName: String, methodName: String, methodDescriptor: String)

  public fun startMethodVisit(methodReference: MethodReference)

  public fun endMethodVisit(methodReference: MethodReference)
}

public class MethodVisitingStatus(
  public var isVisiting: Boolean = false,
  public var methodName: String? = null,
)
