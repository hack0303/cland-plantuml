# cland-plantuml — CLI Usage Guide

Scan Java source files (any project) and generate PlantUML class/package diagrams with optional PNG rendering.

## Quick Start

```bash
# Scan a project and generate .puml files
cland-plantuml /path/to/your-project/src -o ./diagrams

# With PNG rendering
cland-plantuml /path/to/your-project/src -o ./diagrams --render

# Using Gradle (development)
./gradlew :app:run --args="/path/to/your-project/src -o build/uml"
```

## Basic Usage

```bash
cland-plantuml [OPTIONS] [SOURCE_DIRS...]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `SOURCE_DIRS...` | One or more source directories to scan. Scans all `*.java` files recursively. Default: current directory (`.`) |

### Examples

```bash
# Single project
cland-plantuml /home/user/my-project/src

# Multiple source directories (merged into one diagram)
cland-plantuml /home/user/lib-a/src /home/user/lib-b/src

# Current directory (default)
cland-plantuml
```

## Options

### Output Control

| Option | Description | Default |
|--------|-------------|---------|
| `-o`, `--output=<dir>` | Output directory for `.puml` and `.png` files | `build/uml` |
| `-r`, `--render` | Render `.puml` diagrams to PNG (requires Graphviz `dot`) | off |

### Filtering

| Option | Description | Default |
|--------|-------------|---------|
| `-i`, `--include=<patterns>` | Package/class include glob patterns (comma-separated) | `**` |
| `-e`, `--exclude=<patterns>` | Package/class exclude glob patterns (comma-separated) | (none) |
| `--[no-]hide-java-lang` | Hide `java.lang` types from diagram | `true` |
| `--[no-]only-public` | Only show public fields and methods | `true` |

### Diagram Types

| Option | Description | Default |
|--------|-------------|---------|
| `--[no-]package-diagram` | Generate package dependency diagram | `true` |
| `--hide-relationships` | Hide extends/implements/dependency arrows | shown by default |
| `--sequence` | Generate sequence diagram from method call traces | off |
| `--seq-focus <pkg>` | Focus sequence diagram on specific package prefix (repeatable, comma-separated) | all packages |

### Information

| Option | Description |
|--------|-------------|
| `-v`, `--verbose` | Verbose output with timing info |
| `-V`, `--version` | Print version and exit |
| `-h`, `--help` | Show help message and exit |

## Diagram Features

### Class Diagram (`class-diagram.puml`)

The class diagram shows:

- **Classes**, **interfaces**, **enums**, **records**, **annotations** with proper PlantUML stereotypes (`<<record>>`, `<<annotation>>`)
- **Fields** and **methods** with visibility symbols (`+` public, `-` private, `#` protected, `~` package)
- **Interface methods** detected and shown
- **Implements clauses** — e.g. `class MyImpl implements MyInterface`
- **Relationship arrows** (shown by default):
  - `B <|-- A` — extends
  - `B <|.. A` — implements
  - `A --> B` — field/method type dependency
- **Inner/nested classes** detected separately
- **Enum constants** listed
- **Record components** shown as fields
- **Type parameters** (generics) in class headers

### Package Diagram (`package-diagram.puml`)

The package diagram shows:

- **Package boxes** for all packages containing scanned types
- **Dependency arrows** — package → package based on import statements

### Sequence Diagram (`sequence-diagram.puml`)

The sequence diagram shows static method call flow between types:

- **Root entry points** identified as methods that call others but are never called by any known type (`main()` gets priority)
- **Call arrows** with source line numbers — `caller -> callee: method(args)`
- **Self-calls** (same-type internal method calls) shown as looping arrows
- **Nested group blocks** for call chains — e.g. `invoke() → wrapResult() → builder()`
- **Auto-numbered steps** via PlantUML's `autonumber`
- **Focus filtering** with `--seq-focus` to zoom into specific packages
- **Arguments** shown inline (truncated at 80 chars)

> **Note:** This is a **static** call graph, not a runtime trace. Calls through interface dispatch are unresolved. Return flow is not shown.

## CLI Examples

### Scan a third-party project

```bash
# Scan Apache Commons Lang source
cland-plantuml ~/projects/commons-lang/src/main/java \
  -o ~/diagrams/commons-lang \
  --render
```

### Focus on specific packages

```bash
# Only include types in com.example.service and sub-packages
cland-plantuml ./src -o ./uml -i "com.example.service.**"

# Exclude test and internal packages
cland-plantuml ./src -o ./uml \
  -e "**.internal.**, **.test.**"
```

### Control diagram content

```bash
# Show all members (not just public)
cland-plantuml ./src --no-only-public

# Hide relationship arrows (clean diagram)
cland-plantuml ./src --hide-relationships

# Show java.lang types
cland-plantuml ./src --no-hide-java-lang

# Skip the package diagram
cland-plantuml ./src --no-package-diagram

# Full control
cland-plantuml ./src \
  -o ./diagrams \
  --hide-relationships \
  --no-package-diagram \
  --only-public \
  --hide-java-lang \
  -v
```

### Sequence diagram

```bash
# Generate sequence diagram from method call traces
cland-plantuml ./src --sequence

# Focus on a specific package (hide unrelated flows)
cland-plantuml ./src --sequence --seq-focus "com.myapp.service"

# Focus on multiple packages
cland-plantuml ./src --sequence --seq-focus "com.myapp.service,com.myapp.controller"

# Full: sequence + focus + render
cland-plantuml /path/to/src \
  --sequence \
  --seq-focus "com.myapp.engine" \
  --render \
  -v
```

### Scan from a Gradle dev environment

```bash
# Development mode
./gradlew :app:run --args="/path/to/project/src -o build/uml -v"

# With rendering
./gradlew :app:run --args="/path/to/project/src -o build/uml --render"
```

## Output Files

| File | Contents | Generated by |
|------|----------|-------------|
| `class-diagram.puml` | PlantUML class diagram text | `PumlGenerator` |
| `class-diagram.ast` | Parsed type model dump (debug/view) | `JavaSourceScanner` (AST) |
| `class-diagram.png` | Rendered class diagram (if `--render`) | PlantUML + Graphviz dot |
| `package-diagram.puml` | PlantUML package dependency diagram text | `PumlGenerator` |
| `package-diagram.png` | Rendered package diagram (if `--render`) | PlantUML + Graphviz dot |
| `sequence-diagram.puml` | PlantUML sequence diagram text (if `--sequence`) | `PumlGenerator` |
| `sequence-diagram.png` | Rendered sequence diagram (if `--sequence --render`) | PlantUML + Graphviz dot |

### Data Pipeline

```
Java source files
       │
       ▼  JavaParser AST
JavaSourceScanner
       │
       ▼  TypeModel objects
   .ast file (debug dump)
       │
       ▼  PumlGenerator
   .puml file (PlantUML syntax)
       │
       ▼  PlantUML + Graphviz dot
   .png file (final visual)
```

The `.ast` file shows the internal parsed model (types, fields, methods, relationships) before diagram generation. Use it to verify the scanner correctly understood your source code.

### View PNG

```bash
# Open rendered diagram in default image viewer
xdg-open /tmp/alice-uml-final/class-diagram.png
xdg-open /tmp/alice-uml-final/package-diagram.png
```

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | No Java files found matching the include patterns |

## Requirements for Scanned Projects

The tool can scan **any** Java source project — it does not need:

- ❌ A build system (Gradle, Maven, etc.)
- ❌ A compiled classpath or dependencies
- ❌ Module descriptors (`module-info.java` — skipped automatically)
- ❌ The project to be compilable

It parses each `.java` file independently using JavaParser AST. Incomplete source trees are handled gracefully — unresolved type references are kept as simple names.

## File Encoding

All source files are assumed to be UTF-8 encoded. Generated `.puml` files are UTF-8.
