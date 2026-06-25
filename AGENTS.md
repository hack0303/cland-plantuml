# cland-plantuml — Contributor Quickstart Guide

**cland-plantuml** is a Java source code to PlantUML diagram generator. It scans Java source files, extracts type metadata (classes, interfaces, enums, records, annotations), and generates PlantUML class/package diagrams with optional PNG rendering.

## Repository Layout

| Path | Description |
|------|-------------|
| `app/src/main/java/org/cland/plantuml/` | Main source code |
| `app/src/test/groovy/org/cland/plantuml/` | Spock test specifications |
| `app/build.gradle` | Project build configuration |

### Source Files

| File | Purpose |
|------|---------|
| `ClandPlantuml.java` | CLI entry point (picocli), orchestrates scanning → generation → rendering |
| `JavaSourceScanner.java` | Scans Java source files, parses types, fields, methods, and package dependencies |
| `TypeModel.java` | Data model: `TypeModel` (name, package, kind, visibility) and `Member` (fields/methods) |
| `PumlGenerator.java` | Generates PlantUML `.puml` text — class diagrams and package dependency diagrams |
| `PngRenderer.java` | Renders `.puml` files to PNG using PlantUML's `SourceStringReader` |

## General Guidance

- **Java 25** is required (toolchain `JavaLanguageVersion.of(25)`, release flag `25`).
- **Test framework**: Spock 2.4 (Groovy 4.0.29) with JUnit Platform Launcher.
- **CLI argument parsing**: picocli 4.7.6 with `@Command` and `@Option` annotations.
- **Logging**: SLF4J 2.0.16 + Logback 1.5.21.
- **No JPMS** — this is a simple application module, no `module-info.java`.
- Package/visibility filters (`--only-public`, `--hide-java-lang`) are applied during diagram generation, not during scanning.

## Building and Testing

### Prerequisites
- JDK 25+ (temurin or equivalent)
- Gradle 9.5 (wrapper provided)

### Build the project
```bash
./gradlew clean build
```

### Run all tests
```bash
./gradlew check
```

### Run the application
```bash
./gradlew :app:run --args="-s src/main/java -o build/uml"

# With rendering to PNG
./gradlew :app:run --args="-s src/main/java -o build/uml --render"
```

### Build distribution archives
```bash
./gradlew assembleDist
```
Distribution archives are in `app/build/distributions`.

### Install distribution (unpacked)
```bash
./gradlew installDist
./app/build/install/cland-plantuml/bin/cland-plantuml
```

## CLI Usage

```
Usage: cland-plantuml [OPTIONS] [SOURCE_DIRS...]

Generate PlantUML class/package diagrams from Java source code.

Options:
  -o, --output=<outputDir>        Output directory for generated .puml and
                                    .png files (default: build/uml)
  -r, --render                    Render .puml diagrams to PNG
  -i, --include=<includes>        Package/class include patterns (default: **)
  -e, --exclude=<excludes>        Package/class exclude patterns
      --hide-java-lang            Hide java.lang classes (default: true)
      --only-public               Only show public members (default: true)
      --package-diagram           Generate package dependency diagram
                                    (default: true)
  -v, --verbose                   Verbose output
      --version                   Print version information and exit
  -h, --help                      Show this help message and exit
```

## Commit Messages and Pull Requests

- Follow the [Chris Beams](http://chris.beams.io/posts/git-commit/) style for commit messages.
- Every pull request should answer:
  - **What changed?**
  - **Why?**
  - **Breaking changes?**
  - **Related TODO items** (see `TODO-*.md` files in project root)
- Comments should be complete sentences and end with a period.

## Review Checklist

- [ ] `./gradlew check` passes (all tests succeed).
- [ ] Add new Spock tests for any new feature or bug fix.
- [ ] Update documentation (AGENTS.md, README.md) for user-facing changes.
- [ ] Verify CLI options work correctly (picocli help, default values).

## TODO / Plan Tracking

Active work items are tracked in `TODO-*.md` files in the project root.

## Additional Resources

- [README.md](./README.md) — Project overview
