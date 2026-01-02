# Gemini Code Exploration Guide: R8 Project

## Project Overview

This project contains the source code for D8 and R8, two critical tools for Android development.

*   **D8:** A dexer that converts Java bytecode to DEX format.
*   **R8:** A shrinker and minifier that optimizes Java bytecode and converts it to DEX format.

The project is written in Java and Kotlin and uses Gradle for building. It is a multi-module project with several components, including `main`, `library_desugar`, and `test`.

## Building and Running

### Building

The primary build script is `tools/gradle.py`. To build the project, run:

```bash
tools/gradle.py r8
```

This will produce a JAR file at `build/libs/r8.jar`.

### Running D8

D8 is used to convert Java class files to the DEX format.

**Debug build:**

```bash
java -cp build/libs/r8.jar com.android.tools.r8.D8 \
       --min-api <min-api> \
       --output out.zip \
       --lib <android.jar> \
       input.jar
```

**Release build:**

```bash
java -cp build/libs/r8.jar com.android.tools.r8.D8 \
       --release \
       --min-api <min-api> \
       --output out.zip \
       --lib <android.jar> \
       input.jar
```

### Running R8

R8 is used to shrink, optimize and minify Java class files.

```bash
java -cp build/libs/r8.jar com.android.tools.r8.R8 \
       --release \
       --min-api <min-api> \
       --output out.zip \
       --pg-conf proguard-rules.pro \
       --lib <android.jar> \
       input.jar
```

### Testing

Tests are run using the `tools/test.py` script:

```bash
tools/test.py --no-internal
```

By default, this runs tests using r8lib.jar, which is a bootstrapped R8. It is possible to speed up local testing by running tests with a non-bootstrapped R8:

```bash
tools/test.py --no-internal --no-r8lib
```

It is possible to run a single test by passing the name of the test, e.g.,

```bash
tools/test.py --no-internal *ProguardConfigurationParserTest*
```

## Development Conventions

### Code Formatting

The project enforces a strict code style.

*   **Java:** `google-java-format` is used for formatting Java files.
*   **Kotlin:** `ktfmt` is used for formatting Kotlin files.

The `PRESUBMIT.py` script checks that the code has been correctly formatted and contains the exact commands to format the code.

It is also possible to format the code using the `tools/fmt-diff.py` script:

```bash
tools/fmt-diff.py [--python]
```

### Copyright Headers

All new files must contain a copyright header. The format is checked by the presubmit script.

### Testing

There are several conventions for writing tests, including:

*   Do not add `.disassemble()` to tests.
*   Do not add `.allowStdoutMessages()` or `.allowStderrMessages()` to tests.

### Contributions

Contributions to the project require signing a Contributor License Agreement (CLA). See `CONTRIBUTING.md` for more details.
