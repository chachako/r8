// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import DependenciesPlugin.Companion.computeRoot
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID
import kotlin.reflect.full.declaredMemberProperties
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

public class DependenciesPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    // Setup all test tasks to listen after system properties passed in by test.py.
    val testTask = target.tasks.findByName("test") as Test?
    testTask?.configure(isR8Lib = false, r8Jar = null, r8LibMappingFile = null)
  }

  public companion object {
    public fun computeRoot(file: File): File {
      var parent = file
      while (!parent.getName().equals("d8_r8")) {
        parent = parent.getParentFile()
      }
      return parent.getParentFile()
    }
  }
}

public enum class Jdk(public val folder: String, public val version: Int) {
  // Only include LTS and latest non-LTS GA.
  JDK_8("jdk8", 8),
  JDK_9("openjdk-9.0.4", 9), // JDK-9 not LTS, but still used.
  JDK_11("jdk-11", 11),
  JDK_17("jdk-17", 17),
  JDK_21("jdk-21", 21),
  JDK_25("jdk-25", 25);

  public fun isJdk8(): Boolean {
    return this == JDK_8
  }

  public fun getThirdPartyDependency(): ThirdPartyDependency {
    val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
    val subFolder: String
    if (os.isLinux) {
      subFolder = if (isJdk8()) "linux-x86" else "linux"
    } else if (os.isMacOsX) {
      subFolder = if (isJdk8()) "darwin-x86" else "osx"
    } else {
      assert(os.isWindows)
      if (isJdk8()) {
        throw RuntimeException("No Jdk8 on Windows")
      }
      subFolder = "windows"
    }
    return ThirdPartyDependency(
      name,
      Paths.get("third_party", "openjdk", folder, subFolder).toFile(),
      Paths.get("third_party", "openjdk", folder, "$subFolder.tar.gz.sha1").toFile(),
    )
  }
}

public fun Test.configure(isR8Lib: Boolean, r8Jar: File?, r8LibMappingFile: File? = null) {
  TestConfigurationHelper.setupTestTask(this, isR8Lib, r8Jar, r8LibMappingFile)
}

public fun Project.getRoot(): File {
  return computeRoot(this.projectDir)
}

// See https://datatracker.ietf.org/doc/html/rfc4122#section-4.3 for the algorithm.
public fun uuid5(namespace: UUID, name: String): UUID {
  val md = MessageDigest.getInstance("SHA-1")
  md.update(uuidToBytes(namespace))
  md.update(name.encodeToByteArray())
  val sha1Bytes = md.digest()
  // Set version 5 (upper 4 bits of octet 6).
  sha1Bytes[6] = (sha1Bytes[6].toInt() and 0x0f).toByte()
  sha1Bytes[6] = (sha1Bytes[6].toInt() or 0x50).toByte()
  // Set two upper bits of octet 8 to 10.
  sha1Bytes[8] = (sha1Bytes[8].toInt() and 0x3f).toByte()
  sha1Bytes[8] = (sha1Bytes[8].toInt() or 0x80).toByte()
  return uuidFromBytes(sha1Bytes)
}

private fun uuidFromBytes(data: ByteArray): UUID {
  assert(data.size >= 16)
  return UUID(toNetworkOrder(data, 0), toNetworkOrder(data, 8))
}

private fun uuidToBytes(uuid: UUID): ByteArray {
  val result = ByteArray(16)
  fromNetworkByteOrder(uuid.mostSignificantBits, result, 0)
  fromNetworkByteOrder(uuid.leastSignificantBits, result, 8)
  return result
}

private fun toNetworkOrder(data: ByteArray, dataIndex: Int): Long {
  var result: Long = 0
  for (i in 0..7) result = result shl 8 or (data[dataIndex + i].toInt() and 0xff).toLong()
  return result
}

private fun fromNetworkByteOrder(value: Long, dest: ByteArray, destIndex: Int) {
  for (i in 0..7) dest[i + destIndex] = (value shr (7 - i) * 8 and 0xffL).toByte()
}

/**
 * Builds a jar for each sub folder in a test source set.
 *
 * <p> As an example, src/test/examplesJava9 contains subfolders: backport, collectionof, ..., .
 * These are compiled to individual jars and placed in <repo-root>/build/test/examplesJava9/ as:
 * backport.jar, collectionof.jar, ..., .
 *
 * Calling this from a project will amend the task graph with the task named
 * getExamplesJarsTaskName(examplesName) such that it can be referenced from the test runners.
 */
public fun Project.buildExampleJars(name: String): Task {
  val jarTasks: MutableList<Task> = mutableListOf()
  val testSourceSet =
    extensions
      .getByType(JavaPluginExtension::class.java)
      .sourceSets
      // The TEST_SOURCE_SET_NAME is the source set defined by writing
      // java { sourcesets.test { ... }}
      .getByName(SourceSet.TEST_SOURCE_SET_NAME)
  val destinationDir = getRoot().resolveAll("build", "test", name)
  val generateDir = getRoot().resolveAll("build", "generated", name)
  val classesOutput = destinationDir.resolve("classes")
  testSourceSet.java.destinationDirectory.set(classesOutput)
  testSourceSet.resources.destinationDirectory.set(destinationDir)
  testSourceSet.java.sourceDirectories.files.forEach { srcDir ->
    srcDir.listFiles(File::isDirectory)?.forEach { exampleDir ->
      arrayOf("compileTestJava", "debuginfo-all", "debuginfo-none").forEach { taskName ->
        if (!project.getTasksByName(taskName, false).isEmpty()) {
          var generationTask: Task? = null
          val compileOutput = getOutputName(classesOutput.toString(), taskName)
          if (exampleDir.resolve("TestGenerator.java").isFile) {
            val generatedOutput =
              Paths.get(getOutputName(generateDir.toString(), taskName), exampleDir.name).toString()
            generationTask =
              tasks
                .register<JavaExec>("generate-$name-${exampleDir.name}-$taskName") {
                  dependsOn(taskName)
                  mainClass.set("${exampleDir.name}.TestGenerator")
                  classpath = files(compileOutput, testSourceSet.compileClasspath)
                  args(compileOutput, generatedOutput)
                  outputs.dirs(generatedOutput)
                }
                .get()
          }
          val jarTask =
            tasks
              .register<Jar>("jar-$name-${exampleDir.name}-$taskName") {
                dependsOn(taskName)
                archiveFileName.set("${getOutputName(exampleDir.name, taskName)}.jar")
                destinationDirectory.set(destinationDir)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                if (generationTask != null) {
                  // If a generation task exists, we first take the generated output and add to the
                  // current jar. Running with DuplicatesStrategy.EXCLUDE ensure that we do not
                  // overwrite with the non-generated file.
                  dependsOn(generationTask)
                  from(generationTask.outputs.files.singleFile.parentFile) {
                    include("${exampleDir.name}/**/*.class")
                    exclude("**/TestGenerator*")
                  }
                }
                from(compileOutput) {
                  include("${exampleDir.name}/**/*.class")
                  exclude("**/TestGenerator*")
                  exclude("${exampleDir.name}/twr/**")
                }
                // Copy additional resources into the jar.
                from(exampleDir) {
                  exclude("**/*.java")
                  exclude("**/keep-rules*.txt")
                  into(exampleDir.name)
                }
              }
              .get()
          jarTasks.add(jarTask)
        }
      }
    }
  }
  return tasks.register(getExampleJarsTaskName(name)) { dependsOn(jarTasks.toTypedArray()) }.get()
}

private fun getOutputName(dest: String, taskName: String): String {
  if (taskName == "compileTestJava") {
    return dest
  }
  return "${dest}_${taskName.replace('-', '_')}"
}

public fun getExampleJarsTaskName(name: String): String {
  return "build-example-jars-$name"
}

public fun Project.resolve(
  thirdPartyDependency: ThirdPartyDependency,
  vararg paths: String,
): ConfigurableFileCollection {
  return files(project.getRoot().resolve(thirdPartyDependency.path).resolveAll(*paths))
}

/**
 * When using composite builds, referencing tasks in other projects do not give a Task but a
 * TaskReference. To get outputs from other tasks we need to have a proper task and gradle do not
 * provide a way of getting a Task from a TaskReference. We use a trick where we create a synthetic
 * task that depends on the task of interest, allowing us to look at the graph and obtain the actual
 * reference. Remove this code if gradle starts supporting this natively.
 */
public fun Project.projectTask(project: String, taskName: String): Task {
  val name = "$project-reference-$taskName"
  val task =
    tasks.register(name) { dependsOn(gradle.includedBuild(project).task(":$taskName")) }.get()
  return task.taskDependencies.getDependencies(tasks.getByName(name)).iterator().next()
}

public fun File.resolveAll(vararg xs: String): File {
  var that = this
  for (x in xs) {
    that = that.resolve(x)
  }
  return that
}

public fun Project.getJavaHome(jdk: Jdk): File {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  var osFolder = "linux"
  if (os.isWindows) {
    osFolder = "windows"
  }
  if (os.isMacOsX) {
    osFolder = "osx/Contents/Home"
  }
  return getRoot().resolveAll("third_party", "openjdk", jdk.folder, osFolder)
}

public fun Project.getCompilerPath(jdk: Jdk): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  val binary = if (os.isWindows) "javac.exe" else "javac"
  return getJavaHome(jdk).resolveAll("bin", binary).toString()
}

private fun Project.getJavaPath(jdk: Jdk): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  val binary = if (os.isWindows) "java.exe" else "java"
  return getJavaHome(jdk).resolveAll("bin", binary).toString()
}

public fun Project.getJavaLauncher(jdk: Jdk): JavaLauncher {
  return object : JavaLauncher {
    override fun getMetadata(): JavaInstallationMetadata {
      return object : JavaInstallationMetadata {
        override fun getLanguageVersion(): JavaLanguageVersion {
          return DefaultJavaLanguageVersion.of(jdk.version)
        }

        override fun getJavaRuntimeVersion(): String {
          return jdk.name
        }

        override fun getJvmVersion(): String {
          return jdk.name
        }

        override fun getVendor(): String {
          return "vendor"
        }

        override fun getInstallationPath(): Directory {
          return project.layout.projectDirectory.dir(getJavaHome(jdk).toString())
        }

        override fun isCurrentJvm(): Boolean {
          return false
        }
      }
    }

    override fun getExecutablePath(): RegularFile {
      return project.layout.projectDirectory.file(getJavaPath(jdk))
    }
  }
}

private fun getClasspath(vararg paths: File): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  assert(!paths.isEmpty())
  val separator = if (os.isWindows) ";" else ":"
  return paths.joinToString(separator = separator) { it.toString() }
}

public fun Project.baseCompilerCommandLine(
  jar: File,
  deps: File,
  compiler: String,
  args: List<String> = listOf(),
): List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    getJavaPath(Jdk.JDK_17),
    "-Xmx8g",
    "-ea",
    "-cp",
    getClasspath(jar, deps),
    "com.android.tools.r8.SwissArmyKnife",
    compiler,
  ) + args
}

public fun Project.baseCompilerCommandLine(
  jvmArgs: List<String> = listOf(),
  jar: File,
  compiler: String,
  args: List<String> = listOf(),
): List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(getJavaPath(Jdk.JDK_17), "-Xmx8g", "-ea") +
    jvmArgs +
    listOf("-cp", "$jar", "com.android.tools.r8.SwissArmyKnife", compiler) +
    args
}

public fun Project.baseCompilerCommandLine(
  jar: File,
  compiler: String,
  args: List<String> = listOf(),
): List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    getJavaPath(Jdk.JDK_17),
    "-Xmx8g",
    "-ea",
    "-cp",
    "$jar",
    "com.android.tools.r8.SwissArmyKnife",
    compiler,
  ) + args
}

public fun Project.createR8LibCommandLine(
  r8Compiler: File,
  input: File,
  output: File,
  pgConf: List<File>,
  excludingDepsVariant: Boolean,
  debugVariant: Boolean,
  lib: List<File> = listOf(),
  classpath: List<File> = listOf(),
  pgInputMap: File? = null,
  replaceFromJar: File? = null,
  versionJar: File? = null,
  enableKeepAnnotations: Boolean = true,
): List<String> {
  return buildList {
    add("python3")
    add("${getRoot().resolve("tools").resolve("create_r8lib.py")}")
    add("--r8compiler")
    add("$r8Compiler")
    add("--r8jar")
    add("$input")
    add("--output")
    add("$output")
    pgConf.forEach {
      add("--pg-conf")
      add("$it")
    }
    lib.forEach {
      add("--lib")
      add("$it")
    }
    classpath.forEach {
      add("--classpath")
      add("$it")
    }
    if (excludingDepsVariant) {
      add("--excldeps-variant")
    }
    if (debugVariant) {
      add("--debug-variant")
    }
    if (pgInputMap != null) {
      add("--pg-map")
      add("$pgInputMap")
    }
    if (replaceFromJar != null) {
      add("--replace-from-jar")
      add("$replaceFromJar")
    }
    if (versionJar != null) {
      add("--r8-version-jar")
      add("$versionJar")
    }
    if (!enableKeepAnnotations) {
      add("--disable-keep-annotations")
    }
    if (!enableKeepAnnotations) {
      add("--exclude-api-database")
    }
  }
}

public object JvmCompatibility {
  public val sourceCompatibility: JavaVersion = JavaVersion.VERSION_11
  public val targetCompatibility: JavaVersion = JavaVersion.VERSION_11
  public const val release: Int = 11
}

private object Versions {
  public const val androidxCollectionVersion = "1.6.0-SNAPSHOT"
  public const val androidxTracingVersion = "2.0.0-SNAPSHOT"
  public const val asmVersion = "9.9"
  public const val errorproneVersion = "2.18.0"
  public const val fastUtilVersion = "7.2.1"
  public const val gsonVersion = "2.10.1"
  public const val guavaVersion = "32.1.2-jre"
  public const val javassist = "3.29.2-GA"
  public const val junitVersion = "4.13-beta-2"
  public const val kotlinVersion = "1.9.20"
  public const val kotlinMetadataVersion = "2.3.10"
  public const val mockito = "2.10.0"
  public const val smaliVersion = "3.0.3"
  public const val protobufVersion = "4.33.5"
  public const val zipflingerVersion = "9.0.0"
}

public object Deps {
  public val androidxCollection: String by lazy {
    "androidx.collection:collection:${Versions.androidxCollectionVersion}"
  }
  public val androidxTracingDriver: String by lazy {
    "androidx.tracing:tracing:${Versions.androidxTracingVersion}"
  }
  public val androidxTracingDriverWire: String by lazy {
    "androidx.tracing:tracing-wire:${Versions.androidxTracingVersion}"
  }
  public val asm: String by lazy { "org.ow2.asm:asm:${Versions.asmVersion}" }
  public val asmUtil: String by lazy { "org.ow2.asm:asm-util:${Versions.asmVersion}" }
  public val asmCommons: String by lazy { "org.ow2.asm:asm-commons:${Versions.asmVersion}" }
  public val errorprone: String by lazy {
    "com.google.errorprone:error_prone_core:${Versions.errorproneVersion}"
  }
  public val fastUtil: String by lazy { "it.unimi.dsi:fastutil:${Versions.fastUtilVersion}" }
  public val gson: String by lazy { "com.google.code.gson:gson:${Versions.gsonVersion}" }
  public val guava: String by lazy { "com.google.guava:guava:${Versions.guavaVersion}" }
  public val javassist: String by lazy { "org.javassist:javassist:${Versions.javassist}" }
  public val junit: String by lazy { "junit:junit:${Versions.junitVersion}" }
  public val kotlinMetadata: String by lazy {
    "org.jetbrains.kotlin:kotlin-metadata-jvm:${Versions.kotlinMetadataVersion}"
  }
  public val kotlinStdLib: String by lazy {
    "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinVersion}"
  }
  public val kotlinReflect: String by lazy {
    "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinVersion}"
  }
  public val mockito: String by lazy { "org.mockito:mockito-core:${Versions.mockito}" }
  public val smali: String by lazy { "com.android.tools.smali:smali:${Versions.smaliVersion}" }
  public val smaliUtil: String by lazy {
    "com.android.tools.smali:smali-util:${Versions.smaliVersion}"
  }
  public val protobuf: String by lazy {
    "com.google.protobuf:protobuf-java:${Versions.protobufVersion}"
  }
  public val zipflinger: String by lazy { "com.android:zipflinger:${Versions.zipflingerVersion}" }
}

public object ThirdPartyDeps {
  public val aapt2: ThirdPartyDependency =
    ThirdPartyDependency(
      "aapt2",
      Paths.get("third_party", "aapt2").toFile(),
      Paths.get("third_party", "aapt2.tar.gz.sha1").toFile(),
    )
  public val agsa: ThirdPartyDependency =
    ThirdPartyDependency(
      "agsa",
      Paths.get("third_party", "closedsource-apps", "agsa", "20250412-v16.14.47").toFile(),
      Paths.get("third_party", "closedsource-apps", "agsa", "20250412-v16.14.47.tar.gz.sha1")
        .toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val androidJars: List<ThirdPartyDependency> = getThirdPartyAndroidJars()
  public val androidVMs: List<ThirdPartyDependency> = getThirdPartyAndroidVms()
  public val apiDatabase: ThirdPartyDependency =
    ThirdPartyDependency(
      "apiDatabase",
      Paths.get("third_party", "api_database", "api_database").toFile(),
      Paths.get("third_party", "api_database", "api_database.tar.gz.sha1").toFile(),
    )
  public val artTests: ThirdPartyDependency =
    ThirdPartyDependency(
      "art-tests",
      Paths.get("tests", "2017-10-04", "art").toFile(),
      Paths.get("tests", "2017-10-04", "art.tar.gz.sha1").toFile(),
    )
  public val artTestsLegacy: ThirdPartyDependency =
    ThirdPartyDependency(
      "art-tests-legacy",
      Paths.get("tests", "2016-12-19", "art").toFile(),
      Paths.get("tests", "2016-12-19", "art.tar.gz.sha1").toFile(),
    )
  public val clank: ThirdPartyDependency =
    ThirdPartyDependency(
      "clank",
      Paths.get("third_party", "chrome", "clank_google3_prebuilt").toFile(),
      Paths.get("third_party", "chrome", "clank_google3_prebuilt.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val chrome: ThirdPartyDependency =
    ThirdPartyDependency(
      "chrome",
      Paths.get("third_party", "chrome", "chrome_200430").toFile(),
      Paths.get("third_party", "chrome", "chrome_200430.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val chromeBenchmark: ThirdPartyDependency =
    ThirdPartyDependency(
      "chrome-benchmark",
      Paths.get("third_party", "opensource-apps", "chrome").toFile(),
      Paths.get("third_party", "opensource-apps", "chrome.tar.gz.sha1").toFile(),
    )
  public val compilerApi: ThirdPartyDependency =
    ThirdPartyDependency(
      "compiler-api",
      Paths.get("third_party", "binary_compatibility_tests", "compiler_api_tests").toFile(),
      Paths.get("third_party", "binary_compatibility_tests", "compiler_api_tests.tar.gz.sha1")
        .toFile(),
    )
  public val composeExamplesChangedBitwiseValuePropagation: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-examples-changed-bitwise-value-propagation",
      Paths.get(
          "third_party",
          "opensource-apps",
          "compose-examples",
          "changed-bitwise-value-propagation",
        )
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "compose-examples",
          "changed-bitwise-value-propagation.tar.gz.sha1",
        )
        .toFile(),
    )
  public val composeSamplesCrane: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-crane",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "crane").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "crane.tar.gz.sha1")
        .toFile(),
    )
  public val composeSamplesJetCaster: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-jetcaster",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetcaster")
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetcaster.tar.gz.sha1",
        )
        .toFile(),
    )
  public val composeSamplesJetChat: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-jetchat",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetchat").toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetchat.tar.gz.sha1",
        )
        .toFile(),
    )
  public val composeSamplesJetLagged: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-jetlagged",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetlagged")
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetlagged.tar.gz.sha1",
        )
        .toFile(),
    )
  public val composeSamplesJetNews: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-jetnews",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetnews").toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetnews.tar.gz.sha1",
        )
        .toFile(),
    )
  public val composeSamplesJetSnack: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-jetsnack",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "jetsnack")
        .toFile(),
      Paths.get(
          "third_party",
          "opensource-apps",
          "android",
          "compose-samples",
          "jetsnack.tar.gz.sha1",
        )
        .toFile(),
    )
  public val composeSamplesOwl: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-owl",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "owl").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "owl.tar.gz.sha1")
        .toFile(),
    )
  public val composeSamplesReply: ThirdPartyDependency =
    ThirdPartyDependency(
      "compose-samples-reply",
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "reply").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "compose-samples", "reply.tar.gz.sha1")
        .toFile(),
    )
  public val coreLambdaStubs: ThirdPartyDependency =
    ThirdPartyDependency(
      "coreLambdaStubs",
      Paths.get("third_party", "core-lambda-stubs").toFile(),
      Paths.get("third_party", "core-lambda-stubs.tar.gz.sha1").toFile(),
    )
  public val customConversion: ThirdPartyDependency =
    ThirdPartyDependency(
      "customConversion",
      Paths.get("third_party", "openjdk", "custom_conversion").toFile(),
      Paths.get("third_party", "openjdk", "custom_conversion.tar.gz.sha1").toFile(),
    )
  public val dagger: ThirdPartyDependency =
    ThirdPartyDependency(
      "dagger",
      Paths.get("third_party", "dagger", "2.41").toFile(),
      Paths.get("third_party", "dagger", "2.41.tar.gz.sha1").toFile(),
    )
  public val dartSdk: ThirdPartyDependency =
    ThirdPartyDependency(
      "dart-sdk",
      Paths.get("third_party", "dart-sdk").toFile(),
      Paths.get("third_party", "dart-sdk.tar.gz.sha1").toFile(),
    )
  public val ddmLib: ThirdPartyDependency =
    ThirdPartyDependency(
      "ddmlib",
      Paths.get("third_party", "ddmlib").toFile(),
      Paths.get("third_party", "ddmlib.tar.gz.sha1").toFile(),
    )
  public val examples: ThirdPartyDependency =
    ThirdPartyDependency(
      "examples",
      Paths.get("third_party", "examples").toFile(),
      Paths.get("third_party", "examples.tar.gz.sha1").toFile(),
    )
  public val examplesAndroidN: ThirdPartyDependency =
    ThirdPartyDependency(
      "examplesAndroidN",
      Paths.get("third_party", "examplesAndroidN").toFile(),
      Paths.get("third_party", "examplesAndroidN.tar.gz.sha1").toFile(),
    )
  public val examplesAndroidO: ThirdPartyDependency =
    ThirdPartyDependency(
      "examplesAndroidO",
      Paths.get("third_party", "examplesAndroidO").toFile(),
      Paths.get("third_party", "examplesAndroidO.tar.gz.sha1").toFile(),
    )
  public val examplesAndroidOGenerated: ThirdPartyDependency =
    ThirdPartyDependency(
      "examplesAndroidOGenerated",
      Paths.get("third_party", "examplesAndroidOGenerated").toFile(),
      Paths.get("third_party", "examplesAndroidOGenerated.tar.gz.sha1").toFile(),
    )
  public val examplesAndroidOLegacy: ThirdPartyDependency =
    ThirdPartyDependency(
      "examplesAndroidOLegacy",
      Paths.get("third_party", "examplesAndroidOLegacy").toFile(),
      Paths.get("third_party", "examplesAndroidOLegacy.tar.gz.sha1").toFile(),
    )
  public val examplesAndroidP: ThirdPartyDependency =
    ThirdPartyDependency(
      "examplesAndroidP",
      Paths.get("third_party", "examplesAndroidP").toFile(),
      Paths.get("third_party", "examplesAndroidP.tar.gz.sha1").toFile(),
    )
  public val examplesAndroidPGenerated: ThirdPartyDependency =
    ThirdPartyDependency(
      "examplesAndroidPGenerated",
      Paths.get("third_party", "examplesAndroidPGenerated").toFile(),
      Paths.get("third_party", "examplesAndroidPGenerated.tar.gz.sha1").toFile(),
    )
  public val desugarJdkLibs: ThirdPartyDependency =
    ThirdPartyDependency(
      "desugar-jdk-libs",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs").toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs.tar.gz.sha1").toFile(),
    )
  public val desugarJdkLibsLegacy: ThirdPartyDependency =
    ThirdPartyDependency(
      "desugar-jdk-libs-legacy",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_legacy").toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_legacy.tar.gz.sha1").toFile(),
    )
  public val desugarLibraryReleases: List<ThirdPartyDependency> =
    getThirdPartyDesugarLibraryReleases()
  // TODO(b/289363570): This could probably be removed.
  public val framework: ThirdPartyDependency =
    ThirdPartyDependency(
      "framework",
      Paths.get("third_party", "framework").toFile(),
      Paths.get("third_party", "framework.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val googleJavaFormat_1_24: ThirdPartyDependency =
    ThirdPartyDependency(
      "google-java-format-1.24",
      Paths.get("third_party", "google", "google-java-format", "1.24.0").toFile(),
      Paths.get("third_party", "google", "google-java-format", "1.24.0.tar.gz.sha1").toFile(),
    )
  public val googleKotlinFormat_0_54: ThirdPartyDependency =
    ThirdPartyDependency(
      "google-kotlin-format-0.54",
      Paths.get("third_party", "google", "google-kotlin-format", "0.54").toFile(),
      Paths.get("third_party", "google", "google-kotlin-format", "0.54.tar.gz.sha1").toFile(),
    )
  public val googleYapf_20231013: ThirdPartyDependency =
    ThirdPartyDependency(
      "google-yapf-20231013",
      Paths.get("third_party", "google", "yapf", "20231013").toFile(),
      Paths.get("third_party", "google", "yapf", "20231013.tar.gz.sha1").toFile(),
    )
  public val gson: ThirdPartyDependency =
    ThirdPartyDependency(
      "gson",
      Paths.get("third_party", "gson", "gson-2.10.1").toFile(),
      Paths.get("third_party", "gson", "gson-2.10.1.tar.gz.sha1").toFile(),
    )
  public val guavaJre: ThirdPartyDependency =
    ThirdPartyDependency(
      "guava-jre",
      Paths.get("third_party", "guava", "guava-32.1.2-jre").toFile(),
      Paths.get("third_party", "guava", "guava-32.1.2-jre.tar.gz.sha1").toFile(),
    )
  public val desugarJdkLibs11: ThirdPartyDependency =
    ThirdPartyDependency(
      "desugar-jdk-libs-11",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_11").toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_11.tar.gz.sha1").toFile(),
    )
  public val gmscoreVersions: List<ThirdPartyDependency> = getGmsCoreVersions()
  public val internalIssues: List<ThirdPartyDependency> = getInternalIssues()
  public val jacoco: ThirdPartyDependency =
    ThirdPartyDependency(
      "jacoco",
      Paths.get("third_party", "jacoco", "0.8.6").toFile(),
      Paths.get("third_party", "jacoco", "0.8.6.tar.gz.sha1").toFile(),
    )
  public val jasmin: ThirdPartyDependency =
    ThirdPartyDependency(
      "jasmin",
      Paths.get("third_party", "jasmin").toFile(),
      Paths.get("third_party", "jasmin.tar.gz.sha1").toFile(),
    )
  public val jsr223: ThirdPartyDependency =
    ThirdPartyDependency(
      "jsr223",
      Paths.get("third_party", "jsr223-api-1.0").toFile(),
      Paths.get("third_party", "jsr223-api-1.0.tar.gz.sha1").toFile(),
    )
  public val java8Runtime: ThirdPartyDependency =
    ThirdPartyDependency(
      "openjdk-rt-1.8",
      Paths.get("third_party", "openjdk", "openjdk-rt-1.8").toFile(),
      Paths.get("third_party", "openjdk", "openjdk-rt-1.8.tar.gz.sha1").toFile(),
    )
  public val jdks: List<ThirdPartyDependency> = getJdks()
  public val jdk11Test: ThirdPartyDependency =
    ThirdPartyDependency(
      "jdk-11-test",
      Paths.get("third_party", "openjdk", "jdk-11-test").toFile(),
      Paths.get("third_party", "openjdk", "jdk-11-test.tar.gz.sha1").toFile(),
    )
  public val jdk21Float16Test: ThirdPartyDependency =
    ThirdPartyDependency(
      "float16-test",
      Paths.get("third_party", "openjdk", "float16-test").toFile(),
      Paths.get("third_party", "openjdk", "float16-test.tar.gz.sha1").toFile(),
    )
  public val junit: ThirdPartyDependency =
    ThirdPartyDependency(
      "junit",
      Paths.get("third_party", "junit").toFile(),
      Paths.get("third_party", "junit.tar.gz.sha1").toFile(),
    )
  public val jdwpTests: ThirdPartyDependency =
    ThirdPartyDependency(
      "jdwp-tests",
      Paths.get("third_party", "jdwp-tests").toFile(),
      Paths.get("third_party", "jdwp-tests.tar.gz.sha1").toFile(),
    )
  public val kotlinCompilers: List<ThirdPartyDependency> = getThirdPartyKotlinCompilers()
  public val kotlinR8TestResources: ThirdPartyDependency =
    ThirdPartyDependency(
      "kotlinR8TestResources",
      Paths.get("third_party", "kotlinR8TestResources").toFile(),
      Paths.get("third_party", "kotlinR8TestResources.tar.gz.sha1").toFile(),
    )
  public val kotlinxCoroutines: ThirdPartyDependency =
    ThirdPartyDependency(
      "kotlinx-coroutines-1.3.6",
      Paths.get("third_party", "kotlinx-coroutines-1.3.6").toFile(),
      Paths.get("third_party", "kotlinx-coroutines-1.3.6.tar.gz.sha1").toFile(),
    )
  public val multidex: ThirdPartyDependency =
    ThirdPartyDependency(
      "multidex",
      Paths.get("third_party", "multidex").toFile(),
      Paths.get("third_party", "multidex.tar.gz.sha1").toFile(),
    )
  public val nest: ThirdPartyDependency =
    ThirdPartyDependency(
      "nest",
      Paths.get("third_party", "nest", "nest_20180926_7c6cfb").toFile(),
      Paths.get("third_party", "nest", "nest_20180926_7c6cfb.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val nowinandroid: ThirdPartyDependency =
    ThirdPartyDependency(
      "nowinandroid",
      Paths.get("third_party", "opensource-apps", "android", "nowinandroid").toFile(),
      Paths.get("third_party", "opensource-apps", "android", "nowinandroid.tar.gz.sha1").toFile(),
    )
  public val processKeepRulesBinaryCompatibility: ThirdPartyDependency =
    ThirdPartyDependency(
      "retrace-binary-compatibility",
      Paths.get("third_party", "processkeeprules", "binary_compatibility").toFile(),
      Paths.get("third_party", "processkeeprules", "binary_compatibility.tar.gz.sha1").toFile(),
    )
  public val proguards: List<ThirdPartyDependency> = getThirdPartyProguards()
  public val proguardsettings: ThirdPartyDependency =
    ThirdPartyDependency(
      "proguardsettings",
      Paths.get("third_party", "proguardsettings").toFile(),
      Paths.get("third_party", "proguardsettings.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val protoc: ThirdPartyDependency =
    ThirdPartyDependency(
      "protoc",
      Paths.get("third_party", "protoc").toFile(),
      Paths.get("third_party", "protoc.tar.gz.sha1").toFile(),
    )
  public val protoRuntimeEdition2023: ThirdPartyDependency =
    ThirdPartyDependency(
      "protoRuntimeEdition2023",
      Paths.get("third_party", "proto", "runtime", "edition2023").toFile(),
      Paths.get("third_party", "proto", "runtime", "edition2023.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val protoRuntimeLegacy: ThirdPartyDependency =
    ThirdPartyDependency(
      "protoRuntimeLegacy",
      Paths.get("third_party", "proto", "runtime", "legacy").toFile(),
      Paths.get("third_party", "proto", "runtime", "legacy.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val protoTestEdition2023: ThirdPartyDependency =
    ThirdPartyDependency(
      "protoTestEdition2023",
      Paths.get("third_party", "proto", "test", "edition2023").toFile(),
      Paths.get("third_party", "proto", "test", "edition2023.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val protoTestProto2: ThirdPartyDependency =
    ThirdPartyDependency(
      "protoTestProto2",
      Paths.get("third_party", "proto", "test", "proto2").toFile(),
      Paths.get("third_party", "proto", "test", "proto2.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val protoTestProto3: ThirdPartyDependency =
    ThirdPartyDependency(
      "protoTestProto3",
      Paths.get("third_party", "proto", "test", "proto3").toFile(),
      Paths.get("third_party", "proto", "test", "proto3.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val r8: ThirdPartyDependency =
    ThirdPartyDependency(
      "r8",
      Paths.get("third_party", "r8").toFile(),
      Paths.get("third_party", "r8.tar.gz.sha1").toFile(),
    )
  public val r8Mappings: ThirdPartyDependency =
    ThirdPartyDependency(
      "r8-mappings",
      Paths.get("third_party", "r8mappings").toFile(),
      Paths.get("third_party", "r8mappings.tar.gz.sha1").toFile(),
    )
  public val r8v2_0_74: ThirdPartyDependency =
    ThirdPartyDependency(
      "r8-v2-0-74",
      Paths.get("third_party", "r8-releases", "2.0.74").toFile(),
      Paths.get("third_party", "r8-releases", "2.0.74.tar.gz.sha1").toFile(),
    )
  public val r8v3_2_54: ThirdPartyDependency =
    ThirdPartyDependency(
      "r8-v3-2-54",
      Paths.get("third_party", "r8-releases", "3.2.54").toFile(),
      Paths.get("third_party", "r8-releases", "3.2.54.tar.gz.sha1").toFile(),
    )
  public val r8v8_0_46: ThirdPartyDependency =
    ThirdPartyDependency(
      "r8-v8-0-46",
      Paths.get("third_party", "r8-releases", "8.0.46").toFile(),
      Paths.get("third_party", "r8-releases", "8.0.46.tar.gz.sha1").toFile(),
    )
  public val retraceBenchmark: ThirdPartyDependency =
    ThirdPartyDependency(
      "retrace-benchmark",
      Paths.get("third_party", "retrace_benchmark").toFile(),
      Paths.get("third_party", "retrace_benchmark.tar.gz.sha1").toFile(),
    )
  public val retraceBinaryCompatibility: ThirdPartyDependency =
    ThirdPartyDependency(
      "retrace-binary-compatibility",
      Paths.get("third_party", "retrace", "binary_compatibility").toFile(),
      Paths.get("third_party", "retrace", "binary_compatibility.tar.gz.sha1").toFile(),
    )
  public val retracePartitionFormats: ThirdPartyDependency =
    ThirdPartyDependency(
      "retrace-partition-formats",
      Paths.get("third_party", "retrace", "partition_formats").toFile(),
      Paths.get("third_party", "retrace", "partition_formats.tar.gz.sha1").toFile(),
    )
  public val retraceInternal: ThirdPartyDependency =
    ThirdPartyDependency(
      "retrace-internal",
      Paths.get("third_party", "retrace_internal").toFile(),
      Paths.get("third_party", "retrace_internal.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val rhino: ThirdPartyDependency =
    ThirdPartyDependency(
      "rhino",
      Paths.get("third_party", "rhino-1.7.10").toFile(),
      Paths.get("third_party", "rhino-1.7.10.tar.gz.sha1").toFile(),
    )
  public val rhinoAndroid: ThirdPartyDependency =
    ThirdPartyDependency(
      "rhino-android",
      Paths.get("third_party", "rhino-android-1.1.1").toFile(),
      Paths.get("third_party", "rhino-android-1.1.1.tar.gz.sha1").toFile(),
    )
  public val smali: ThirdPartyDependency =
    ThirdPartyDependency(
      "smali",
      Paths.get("third_party", "smali").toFile(),
      Paths.get("third_party", "smali.tar.gz.sha1").toFile(),
    )
  public val systemUI: ThirdPartyDependency =
    ThirdPartyDependency(
      "systemUI",
      Paths.get("third_party", "closedsource-apps", "systemui").toFile(),
      Paths.get("third_party", "closedsource-apps", "systemui.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  public val tivi: ThirdPartyDependency =
    ThirdPartyDependency(
      "tivi",
      Paths.get("third_party", "opensource-apps", "tivi").toFile(),
      Paths.get("third_party", "opensource-apps", "tivi.tar.gz.sha1").toFile(),
    )
  public val youtube1719: ThirdPartyDependency =
    ThirdPartyDependency(
      "youtube-17.19",
      Paths.get("third_party", "youtube", "youtube.android_17.19").toFile(),
      Paths.get("third_party", "youtube", "youtube.android_17.19.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
}

private fun getThirdPartyAndroidJars(): List<ThirdPartyDependency> {
  return listOf(
      "libcore_latest",
      "lib-main",
      "lib-v14",
      "lib-v15",
      "lib-v19",
      "lib-v21",
      "lib-v22",
      "lib-v23",
      "lib-v24",
      "lib-v25",
      "lib-v26",
      "lib-v27",
      "lib-v28",
      "lib-v29",
      "lib-v30",
      "lib-v31",
      "lib-v32",
      "lib-v33",
      "lib-v34",
      "lib-v35",
      "lib-v36",
      "lib-v36.1",
    )
    .map(::getThirdPartyAndroidJar)
}

private fun getThirdPartyAndroidJar(version: String): ThirdPartyDependency {
  return ThirdPartyDependency(
    version,
    Paths.get("third_party", "android_jar", version).toFile(),
    Paths.get("third_party", "android_jar", "$version.tar.gz.sha1").toFile(),
  )
}

private fun getThirdPartyAndroidVms(): List<ThirdPartyDependency> {
  return listOf(
      listOf("host", "art-master"),
      listOf("host", "art-16.0.0"),
      listOf("host", "art-15.0.0-beta2"),
      listOf("host", "art-14.0.0-beta3"),
      listOf("host", "art-13.0.0"),
      listOf("host", "art-12.0.0-beta4"),
      listOf("art-10.0.0"),
      listOf("art-5.1.1"),
      listOf("art-6.0.1"),
      listOf("art-7.0.0"),
      listOf("art-8.1.0"),
      listOf("art-9.0.0"),
      listOf("art"),
      listOf("dalvik-4.0.4"),
      listOf("dalvik"),
    )
    .map(::getThirdPartyAndroidVm)
}

private fun getThirdPartyAndroidVm(version: List<String>): ThirdPartyDependency {
  return ThirdPartyDependency(
    version.last(),
    Paths.get("tools", "linux", *version.slice(0..version.size - 2).toTypedArray(), version.last())
      .toFile(),
    Paths.get(
        "tools",
        "linux",
        *version.slice(0..version.size - 2).toTypedArray(),
        "${version.last()}.tar.gz.sha1",
      )
      .toFile(),
  )
}

private fun getJdks(): List<ThirdPartyDependency> {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  if (os.isLinux || os.isMacOsX) {
    return Jdk.values().map { it.getThirdPartyDependency() }
  } else {
    return Jdk.values().filter { !it.isJdk8() }.map { it.getThirdPartyDependency() }
  }
}

private fun getThirdPartyProguards(): List<ThirdPartyDependency> {
  return listOf("proguard-7.0.0", "proguard-7.7.0").map {
    ThirdPartyDependency(
      it,
      Paths.get("third_party", "proguard", it).toFile(),
      Paths.get("third_party", "proguard", "${it}.tar.gz.sha1").toFile(),
    )
  }
}

private fun getThirdPartyKotlinCompilers(): List<ThirdPartyDependency> {
  return listOf(
      "kotlin-compiler-1.3.72",
      "kotlin-compiler-1.4.20",
      "kotlin-compiler-1.5.0",
      "kotlin-compiler-1.6.0",
      "kotlin-compiler-1.7.0",
      "kotlin-compiler-1.8.0",
      "kotlin-compiler-1.9.21",
      "kotlin-compiler-2.0.20",
      "kotlin-compiler-2.1.10",
      "kotlin-compiler-2.2.0",
      "kotlin-compiler-2.3.10",
      "kotlin-compiler-dev",
    )
    .map {
      ThirdPartyDependency(
        it,
        Paths.get("third_party", "kotlin", it).toFile(),
        Paths.get("third_party", "kotlin", "${it}.tar.gz.sha1").toFile(),
      )
    }
}

private fun getThirdPartyDesugarLibraryReleases(): List<ThirdPartyDependency> {
  return listOf("1.0.9", "1.0.10", "1.1.0", "1.1.1", "1.1.5", "2.0.3").map {
    ThirdPartyDependency(
      "desugar-library-release-$it",
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_releases", it).toFile(),
      Paths.get("third_party", "openjdk", "desugar_jdk_libs_releases", "${it}.tar.gz.sha1").toFile(),
    )
  }
}

private fun getInternalIssues(): List<ThirdPartyDependency> {
  return listOf("issue-127524985").map {
    ThirdPartyDependency(
      "internal-$it",
      Paths.get("third_party", "internal", it).toFile(),
      Paths.get("third_party", "internal", "${it}.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  }
}

private fun getGmsCoreVersions(): List<ThirdPartyDependency> {
  return listOf("gmscore_v10", "latest").map {
    ThirdPartyDependency(
      "gmscore-version-$it",
      Paths.get("third_party", "gmscore", it).toFile(),
      Paths.get("third_party", "gmscore", "${it}.tar.gz.sha1").toFile(),
      testOnly = true,
      type = DependencyType.X20,
    )
  }
}

private fun allDependencies(): List<ThirdPartyDependency> {
  val allDeps = mutableListOf<ThirdPartyDependency>()
  ThirdPartyDeps::class.declaredMemberProperties.forEach {
    val value = it.get(ThirdPartyDeps)
    if (value is List<*>) {
      allDeps.addAll(value as List<ThirdPartyDependency>)
    } else {
      allDeps.add(value as ThirdPartyDependency)
    }
  }
  return allDeps
}

public fun allPublicDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> !x.testOnly && x.type == DependencyType.GOOGLE_STORAGE }
}

public fun allPublicTestDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> x.testOnly && x.type == DependencyType.GOOGLE_STORAGE }
}

public fun allInternalDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> !x.testOnly && x.type == DependencyType.X20 }
}

public fun allInternalTestDependencies(): List<ThirdPartyDependency> {
  return allDependencies().filter { x -> x.testOnly && x.type == DependencyType.X20 }
}

public fun extractClassesPaths(prefix: String, vararg paths: String): String {
  val result: MutableList<String> = ArrayList()
  paths.forEach { it ->
    result.addAll(
      it.split(File.pathSeparator).filter { it.contains("${prefix}build${File.separator}classes") }
    )
  }
  return result.joinToString(File.pathSeparator)
}

public fun Project.configureErrorProneForJavaCompile() {
  val treatWarningsAsErrors = !project.hasProperty("disable_warnings_as_errors")
  val enableErrorProne = !project.hasProperty("disable_errorprone")
  tasks.withType<JavaCompile>().configureEach {
    options.errorprone.isEnabled.set(enableErrorProne)
    if (enableErrorProne) {
      // Non-default / Experimental checks - explicitly enforced.
      enableCheck(this, "RemoveUnusedImports", treatWarningsAsErrors)
      enableCheck(this, "InconsistentOverloads", treatWarningsAsErrors)
      enableCheck(this, "MissingDefault", treatWarningsAsErrors)
      enableCheck(this, "MultipleTopLevelClasses", treatWarningsAsErrors)
      enableCheck(this, "NarrowingCompoundAssignment", treatWarningsAsErrors)

      // Warnings that cause unwanted edits (e.g., inability to write informative asserts).
      options.errorprone.disable("AlreadyChecked")

      // JavaDoc related warnings. Would be nice to resolve but of no real consequence.
      options.errorprone.disable("InvalidLink")
      options.errorprone.disable("InvalidBlockTag")
      options.errorprone.disable("InvalidInlineTag")
      options.errorprone.disable("EmptyBlockTag")
      options.errorprone.disable("MissingSummary")
      options.errorprone.disable("UnrecognisedJavadocTag")
      options.errorprone.disable("AlmostJavadoc")

      // Moving away from identity and canonical items is not planned.
      options.errorprone.disable("IdentityHashMapUsage")
    }

    // Make all warnings errors. Warnings that we have chosen not to fix (or suppress) are disabled
    // outright below.
    if (treatWarningsAsErrors) {
      options.compilerArgs.add("-Werror")
    }
  }
}

private fun enableCheck(task: JavaCompile, warning: String, treatWarningsAsErrors: Boolean) {
  if (treatWarningsAsErrors) {
    task.options.errorprone.error(warning)
  } else {
    task.options.errorprone.warn(warning)
  }
}
