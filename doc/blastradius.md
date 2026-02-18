# R8 Blast Radius

The "blast radius" of a keep rule refers to the negative impact that the rule has on the final application.

R8 provides a tool to analyze and visualize this blast radius. This helps developers:

* **Identify expensive rules**: Find keep rules that are preventing R8 from
optimizing large parts of the application.
* **Debug dependencies**: See which libraries are introducing broad keep rules.
* **Remove redundancy**: Discover rules that are not needed because they are
covered by other rules.

> **Note**: This feature is currently **experimental** and requires R8 version
> 9.2.1-dev or higher. When visualizing the data, it is important to use the
> same version of R8 that was used to generate the `.pb` file, as the protocol
> buffer schema may change. The r8.jar corresponding to a given version can be
> downloaded from https://storage.googleapis.com/r8-releases. For example,
> R8 9.2.1-dev can be downloaded from
> https://storage.googleapis.com/r8-releases/raw/9.2.1-dev/r8.jar.

## Generating the Blast Radius Data

R8 outputs the blast radius information as a Protocol Buffer (`.pb`) file, which
uses the schema defined in `src/blastradius/proto/blastradius.proto`.

### Gradle

To generate the blast radius file from a Gradle build, pass the
`com.android.tools.r8.dumpblastradiustodirectory` system property.

See [Replacing R8 in AGP](../README.md#replacing-r8-in-agp) for instructions on
how to update the R8 version in your build.

```bash
./gradlew assembleRelease \
    --no-daemon \
    -Dcom.android.tools.r8.dumpblastradiustodirectory=<output_directory>
```

**Example**:
```bash
./gradlew assembleRelease \
    --no-daemon \
    -Dcom.android.tools.r8.dumpblastradiustodirectory=/tmp/blastradius
```

### Android Platform

For builds within the Android Platform, set the `R8_DUMP_BLAST_RADIUS` environment variable to `true`.

```bash
R8_DUMP_BLAST_RADIUS=true m
```

This will generate an `r8blastradius.pb` file for each R8 build target within
`out/soong/.intermediates`.

## Visualizing the Data

The raw `.pb` file can be converted into an interactive HTML report using the
`BlastRadiusHtmlReportGenerator`.

### Converting a single file

Use the following command to convert a specific `.pb` file to HTML:

```bash
java -cp r8.jar com.android.tools.r8.blastradius.BlastRadiusHtmlReportGenerator \
  <path_to_input_pb> <path_to_output_html>
```

**Example**:
```bash
java -cp r8.jar com.android.tools.r8.blastradius.BlastRadiusHtmlReportGenerator \
  /tmp/blastradius/blastradius.pb /tmp/blastradius/report.html
```

### Converting multiple files (Android Platform)

If you have multiple blast radius files (e.g., from an Android Platform build),
you can generate a summary report for the directory:

```bash
java -cp prebuilts/r8/r8.jar com.android.tools.r8.blastradius.BlastRadiusHtmlReportGenerator \
  <path_to_input_directory> <path_to_output_directory>
```

**Example**:
```bash
java -cp prebuilts/r8/r8.jar com.android.tools.r8.blastradius.BlastRadiusHtmlReportGenerator \
  out/soong/.intermediates /tmp/blastradiusreport
```

This will scan the input directory for `.pb` files and generate an HTML report
in the output directory.
