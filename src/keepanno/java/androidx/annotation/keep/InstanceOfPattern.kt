/*
 * Copyright 2025 The Android Open Source Project
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

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

// ***********************************************************************************
// MAINTAINED AND TESTED IN THE R8 REPO. PLEASE MAKE CHANGES THERE AND REPLICATE.
// ***********************************************************************************

package androidx.annotation.keep

import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * A pattern structure for matching instances of classes and interfaces.
 *
 * <p>
 * If no properties are set, the default pattern matches any instance.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class InstanceOfPattern(

    /**
     * True if the pattern should include the directly matched classes.
     *
     * <p>
     * If false, the pattern is exclusive and only matches classes that are strict subclasses of the
     * pattern.
     */
    val isInclusive: Boolean = true,

    /** Instances of classes matching the class-name pattern. */
    val classNamePattern: ClassNamePattern = ClassNamePattern(unqualifiedName = ""),
)
