[![Discourse Topics][discourse-shield]][discourse-url]
![Issues][issues-shield]
![Contributor Shield][contributor-shield]

[discourse-shield]: https://img.shields.io/discourse/topics?label=Discuss%20This%20Tool&server=https%3A%2F%2Fdeveloper.sailpoint.com%2Fdiscuss
[discourse-url]: https://developer.sailpoint.com/discuss/tag/rules
[issues-shield]:https://img.shields.io/github/issues/sailpoint-oss/rule-development-kit?label=Issues
[contributor-shield]:https://img.shields.io/github/contributors/sailpoint-oss/rule-development-kit?label=Contributors


[product-screenshot]: ./assets/images/intellij.png

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/othneildrew/Best-README-Template">
    <img src="https://avatars.githubusercontent.com/u/63106368?s=200&v=4" alt="Logo" width="80" height="80">
  </a>

  <h3 align="center">Rule Development Kit</h3>

  <p align="center">
    A project setup for the development and testing of SailPoint rules.
    <br />
    <a href="https://developer.sailpoint.com/idn/tools/rule-development-kit"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <!-- <a href="https://github.com/sailpoint/repo-template">View Demo</a>
    ·
    <a href="https://github.com/sailpoint-oss/repo-template/issues">Report Bug</a>
    ·
    <a href="https://github.com/sailpoint-oss/repo-template/issueschoose">Request Feature</a> -->
  </p>
</div>

## Requirements

- **JDK 21.** The build sets `maven.compiler.release` to 21, so older JDKs cannot compile it.
- **Maven 3.9+.**

### ⚠️ Make sure Maven is actually using JDK 21

Maven uses `JAVA_HOME`, *not* whichever `java` is first on your `PATH`. If `JAVA_HOME` is
unset, Maven falls back to its own bundled JDK — and a Homebrew-installed Maven pulls in
Homebrew's `openjdk` formula, which may not be 21.

Check what Maven is really using before you build:

```bash
mvn -v      # look at the "Java version:" line, not `java -version`
```

If it does not say 21, set it explicitly:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
# export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk     # Linux, adjust to your install
```

If Maven runs on an older JDK, compilation fails with `error: release version 21 not supported`.

Mockito (5.24.0) and byte-buddy (1.18.14) are pinned to versions that support JDK 21.
Surefire loads Mockito as a `-javaagent` instead of letting it self-attach, which JDK 21
warns about and future JDKs will block.

CI (`.github/workflows/build.yml`) runs `mvn -B -ntp verify` on Temurin 21 for every pull request.

## Running the tests

```bash
mvn test
```

You should get `Tests run: 42, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

## Updating the SailPoint class stubs

The `sailpoint.*` classes your rules compile against (`IdnRuleUtil`, `Identity`, `Link`,
`ProvisioningPlan`, …) are **not** source files in this repo. They come from a prebuilt
`sailpoint:rule-java-docs` jar produced by the
[rule-javadoc](https://github.com/sailpoint-oss/rule-javadoc) repo.

That jar is vendored here as a committed local Maven repository:

```
lib/sailpoint/rule-java-docs/<version>/
    rule-java-docs-<version>.jar        # the compiled stubs
    rule-java-docs-<version>.pom
    *.md5 / *.sha1                      # checksums Maven requires
lib/sailpoint/rule-java-docs/maven-metadata.xml
```

`pom.xml` points at it with `<url>file://${project.basedir}/lib</url>` and depends on a
specific version. So when the rule API changes, you rebuild the jar in `rule-javadoc` and
deploy it into `lib/` here.

### Steps

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
RDK=$(pwd)                      # run this from the root of this repo
```

**1. Bump the version in `rule-javadoc/pom.xml`.**

Check what version this repo currently consumes (`grep -A2 rule-java-docs pom.xml`) and pick
the next one. Note that `rule-javadoc`'s own `pom.xml` has historically drifted out of sync
with the version actually shipped here, so verify rather than assume.

**2. Build the jar.**

```bash
cd /path/to/rule-javadoc
mvn package                     # produces target/rule-java-docs-<VERSION>.jar
```

**3. Deploy it into this repo's `lib/`.**

```bash
mvn deploy:deploy-file \
  -Durl=file://$RDK/lib \
  -DrepositoryId=data-local \
  -Dfile=target/rule-java-docs-<VERSION>.jar \
  -DpomFile=pom.xml
```

Use `deploy:deploy-file`, **not** `install:install-file` — only `deploy-file` writes the
`.md5`/`.sha1` checksums and updates `maven-metadata.xml` to match the existing layout.

**4. Point this repo at the new version.** Edit the dependency in `pom.xml`:

```xml
<dependency>
    <groupId>sailpoint</groupId>
    <artifactId>rule-java-docs</artifactId>
    <version><!-- new version --></version>
</dependency>
```

**5. Verify.**

```bash
cd $RDK
mvn test        # expect 42/42 passing
```

**6. Commit** the new `lib/sailpoint/rule-java-docs/<version>/` directory, the updated
`lib/sailpoint/rule-java-docs/maven-metadata.xml`, and `pom.xml`. The old version directory
can be deleted once you are confident in the new one.

### Checking what you built

```bash
# maven-metadata.xml should list the new version and set it as <release>
cat lib/sailpoint/rule-java-docs/maven-metadata.xml

# the jar should contain Java 8 bytecode (major version 52)
javap -verbose -cp lib/sailpoint/rule-java-docs/<VERSION>/rule-java-docs-<VERSION>.jar \
  sailpoint.server.IdnRuleUtil | grep major
```

Java 8 bytecode is expected and correct. `rule-javadoc` sets
`maven.compiler.target=8`, so the jar targets Java 8 **regardless of which JDK you build
with**, and JDK 21 reads it without issue. You do not need a JDK 8 to produce it.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| `error: release version 21 not supported` | Maven is running on a JDK older than 21. Check `mvn -v` and set `JAVA_HOME`. |
| `Could not resolve dependencies for ... rule-java-docs:jar:<version>` | The version in `pom.xml` does not match a directory under `lib/sailpoint/rule-java-docs/`. |
| Dependency resolves to the old stubs after an update | Stale local cache. Run `rm -rf ~/.m2/repository/sailpoint/rule-java-docs` and rebuild. |
| `source value 8 is obsolete` warnings when building `rule-javadoc` | Expected, harmless. It targets Java 8 on purpose. |
