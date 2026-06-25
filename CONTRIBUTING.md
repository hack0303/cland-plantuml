# Contributing to cland-plantuml

This doc is intended for contributors to **cland-plantuml** (hopefully that's you!)

## Development Environment

- **Java 25+** is required to run Gradle, compile the project, and run all tests.
- **Gradle 9.5** (wrapper provided via `./gradlew`).
- **Git** with proper commit message conventions.

## Build

```bash
./gradlew clean build
```

This will compile the application and run all tests.

## Testing

This project uses **Spock 2.4** (Groovy 4.0.29) with **JUnit Platform** as the test engine.

### Run all tests
```bash
./gradlew check
```

### Run all tests (verbose)
```bash
./gradlew test
```

### Run a single test class or wildcard
```bash
./gradlew :app:test --tests "org.cland.plantuml.ClandPlantumlSpec"
./gradlew :app:test --tests "org.cland.plantuml.*"
```

Spock test reports (HTML) are generated to `app/build/reports/tests/`.

## Running the Application

### Via Gradle
```bash
# Basic usage — scan a directory and generate .puml files
./gradlew :app:run --args="-s src/main/java -o build/uml"

# With PNG rendering
./gradlew :app:run --args="-s src/main/java -o build/uml --render"

# Scan multiple directories
./gradlew :app:run --args="src/main/java src/test/java -o build/uml"

# Filter by package
./gradlew :app:run --args="-s src/main/java -o build/uml -i 'org.cland.**'"

# Exclude test packages
./gradlew :app:run --args="-s . -o build/uml -e '*.test.*'"

# Show all members (including private/protected)
./gradlew :app:run --args="-s src/main/java -o build/uml --only-public=false"
```

### From installed distribution
```bash
./gradlew installDist
./app/build/install/cland-plantuml/bin/cland-plantuml -s src/main/java -o build/uml
```

## Commit Messages

We follow the [Chris Beams](http://chris.beams.io/posts/git-commit/) guide to writing git commit messages:

- Separate subject from body with a blank line
- Limit subject line to 50 characters
- Capitalize the subject line
- Do not end the subject line with a period
- Use the imperative mood in the subject line
- Wrap the body at 72 characters
- Use the body to explain *what* and *why* (not *how*)

### Commit message structure:
```
<scope>: <short description>

More detailed explanation wrapping at 72 characters.

Closes #123
```

### Recommended prefixes:
| Prefix | Scope |
|--------|-------|
| `scanner` | JavaSourceScanner — file scanning and parsing |
| `generator` | PumlGenerator — diagram text generation |
| `renderer` | PngRenderer — PNG rendering |
| `cli` | ClandPlantuml — CLI entry point and picocli options |
| `model` | TypeModel — data model changes |
| `test` | Test specifications and test infrastructure |
| `build` | Gradle/build config, dependencies |
| `docs` | Documentation only |

## Pull Request Checklist

Before submitting a PR, ensure:

- [ ] `./gradlew check` passes (all tests succeed)
- [ ] New Spock (or Groovy) tests are added for any new feature or bug fix
- [ ] Documentation (AGENTS.md, README.md) is updated for user-facing changes
- [ ] CLI help output is correct (`./gradlew :app:run --args="--help"`)
- [ ] No breaking changes to CLI options without discussion

## Pull Request Description

Every pull request should answer:

- **What changed?** — Summary of the changes
- **Why?** — Motivation and context
- **Breaking changes?** — Yes/No, with migration notes if applicable
- **Related issues/TODOs** — Links to relevant issue numbers

## Code Style

- Use **Java 25** language features where appropriate (records, sealed classes, pattern matching, text blocks, etc.)
- Use SLF4J for all logging — `LoggerFactory.getLogger(getClass())`
- Keep classes focused and single-responsibility
- Parse regex patterns as `static final` constants for performance
- Use `Path` API (NIO.2) for all file I/O — avoid `java.io.File`

## Getting Help

- See the [AGENTS.md](./AGENTS.md) for a quickstart overview
- See [README.md](./README.md) for project overview
