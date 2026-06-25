# cland-plantuml

**cland-plantuml** scans Java source files and generates [PlantUML](https://plantuml.com/) class diagrams and package dependency diagrams, with optional PNG rendering.

## Project Layout

```
cland-plantuml
|
|-- app
|   |-- src
|   |   |-- main
|   |   |   |-- java
|   |   |   |   `-- org/cland/plantuml/
|   |   |   |       |-- ClandPlantuml.java   (CLI entry point, picocli)
|   |   |   |       |-- JavaSourceScanner.java (source file scanner & parser)
|   |   |   |       |-- PumlGenerator.java     (PlantUML text generation)
|   |   |   |       |-- PngRenderer.java       (PNG rendering)
|   |   |   |       `-- TypeModel.java         (data model)
|   |   |   `-- resources
|   |   `-- test
|   |       |-- groovy
|   |       |   `-- org/cland/plantuml/
|   |       |       `-- ClandPlantumlSpec.groovy
|   |       `-- resources
|   `-- build.gradle
|
|-- .gitignore
|-- LICENSE
|-- AGENTS.md
|-- CONTRIBUTING.md
|-- README.md
|-- gradlew
|-- gradlew.bat
`-- settings.gradle
```

## Prerequisites

- JDK 25+ (temurin or equivalent)
- Gradle 9.5 (wrapper provided)

## Build

```bash
./gradlew clean build
```

## Code Formatting

Code formatting is enforced by [Spotless](https://github.com/diffplug/spotless) with **Google Java Format** (AOSP variant). Formatting runs automatically during `build`, but you can also run it explicitly:

```bash
# Apply formatting
./gradlew spotlessApply

# Check formatting (without making changes)
./gradlew spotlessCheck
```

## Run Tests

```bash
./gradlew check
```

Gradle HTML report is located at `app/build/reports/tests/`.

## Run the Application

### Basic usage
```bash
# Scan a directory, generate .puml files
./gradlew :app:run --args="-s src/main/java -o build/uml"
```

### With PNG rendering
```bash
./gradlew :app:run --args="-s src/main/java -o build/uml --render"
```

### Filter by package
```bash
./gradlew :app:run --args="-s src/main/java -o build/uml -i 'org.cland.**'"
```

### Show all members (including private/protected)
```bash
./gradlew :app:run --args="-s src/main/java -o build/uml --only-public=false"
```

## Building Distribution Archives

```bash
./gradlew assembleDist
# Archives: app/build/distributions/cland-plantuml-*.zip / .tar

./gradlew installDist
# Installed: app/build/install/cland-plantuml/bin/cland-plantuml
```

## CLI Options

| Option | Description | Default |
|--------|-------------|---------|
| `SOURCE_DIRS` | Source directories to scan (positional) | `.` |
| `-o, --output` | Output directory for `.puml`/`.png` files | `build/uml` |
| `-r, --render` | Render `.puml` diagrams to PNG | `false` |
| `-i, --include` | Package/class include patterns (comma-separated) | `**` |
| `-e, --exclude` | Package/class exclude patterns (comma-separated) | — |
| `--hide-java-lang` | Hide `java.lang` classes from diagrams | `true` |
| `--only-public` | Only show public members | `true` |
| `--package-diagram` | Generate package dependency diagram | `true` |
| `-v, --verbose` | Verbose output | `false` |
| `--version` | Print version information | — |
| `-h, --help` | Show help message | — |

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Gradle | 9.5 |
| picocli | 4.7.6 |
| PlantUML | 1.2025.0 |
| SLF4J | 2.0.16 |
| Logback | 1.5.21 |
| Spock | 2.4-M7 (Groovy 4.0.29) |
| Spotless | 6.25.0 (Google Java Format 1.28.0) |

## License

[MIT](./LICENSE)
