# Design: Migrating from Regex Scanning to AST-Based Parsing

## 0. Core Design Constraint: Scanning Arbitrary Projects

**`cland-plantuml` is a general-purpose scanner.** It takes any set of source directories as input (via the `SOURCE_DIRS` positional parameter) and generates PlantUML diagrams from whatever Java code it finds — it does **not** scan its own source code.

```bash
# Scan a completely different project
./gradlew :app:run --args="-s /path/to/some-other-project/src -o ./diagrams"

# Scan multiple unrelated projects together
./gradlew :app:run --args="-s /path/to/project-a/src /path/to/project-b/src -o ./diagrams"
```

Because of this, the parser must be:
- **Standalone** — cannot rely on the scanned project's build system, classpath, or module structure.
- **Resilient** — must parse incomplete source trees (not all dependencies may be present).
- **Fast** — should handle large projects without a full compilation pass.

This is precisely why the current regex approach was chosen initially, but also why **JavaParser** (which is standalone and file-based) is the right AST choice — it does not require a compilation context.

## 1. Motivation

The current `JavaSourceScanner` uses regex pattern matching to extract type metadata from Java source files. While functional, this approach has several inherent limitations that an AST-based approach solves:

| Limitation | Impact |
|------------|--------|
| **No inheritance info** | Cannot detect `extends`, `implements`, or `permits` clauses. Diagrams show no relationships. |
| **No generic type resolution** | Raw type strings are captured; parameterised types like `List<String>` are partially parsed or missed. |
| **False positives in methods** | Regex can match keywords or string literals as method/field declarations. |
| **No annotation metadata** | Annotations like `@Deprecated`, `@JsonProperty` are stripped during cleaning. |
| **No inner/nested class support** | Inner classes inside a body are not reliably detected or scoped. |
| **No record component detection** | Record components (canonical constructor params) are not identified. |
| **Fragile brace matching** | `findMatchingBrace` is hand-rolled and can fail on edge cases (nested strings, lambdas, annotations with braces). |
| **No enum constant extraction** | Enum values are not captured. |
| **No Java version awareness** | Works on raw text — will not leverage Java language evolution (sealed classes, pattern matching context). |

Using a proper Java AST parser eliminates all of these issues and opens the door to richer diagram generation.

## 2. Options for Java AST Parsing

### Option A: Java Compiler API (`javax.tools.JavaCompiler`) + `com.sun.source.tree`

- ✅ Zero additional dependencies (JDK built-in)
- ✅ Full, spec-compliant parsing
- ❌ Internal JDK API (`com.sun.source.*`) — may vary across JDK versions
- ❌ More verbose setup (requires compiling files through the tool API)
- ❌ No standalone source-level analysis without full compilation context

### Option B: Eclipse JDT Core

- ✅ Mature, production-grade
- ✅ Standalone — does not require a full compiler setup
- ❌ Heavy dependency (~5 MB)
- ❌ API is complex and geared toward IDE integration

### Option C: JavaParser (`com.github.javaparser:javaparser-core`)

- ✅ **Recommended**. Popular, actively maintained, standalone.
- ✅ Clean, fluent API with symbol resolution support.
- ✅ Handles all Java versions up to Java 21+ (lombok support via `javaparser-symbol-solver`).
- ✅ `CompilationUnit` model maps 1:1 to source files — visit types, methods, fields, annotations, generics.
- ✅ Supports `Visitor` pattern for selective traversal.
- ✅ Lightweight core (~2 MB) with optional symbol solver extension.
- ✅ Apache 2.0 license (compatible with this project).

| Feature | JavaParser | JDT | Compiler API |
|---------|-----------|-----|--------------|
| Pure dependency size | ~2 MB | ~5 MB | 0 (JDK) |
| API clarity | ★★★★★ | ★★★ | ★★★ |
| Standalone (no compilation) | ✅ | ✅ | ❌ |
| Symbol resolution | Optional extension | ✅ | ✅ |
| Java 21+ support | ✅ | ✅ | ✅ |

**Decision: Use JavaParser `javaparser-core`.**

## 3. Architecture

### 3.1 High-Level Flow (after migration)

```
┌─────────────────┐     ┌──────────────────────┐     ┌────────────────┐
│  Source Files    │────▶│  AstScanner           │────▶│  TypeModel     │
│  (.java)         │     │  (JavaParser Visitor) │     │  (enriched)    │
└─────────────────┘     └──────────────────────┘     └────────────────┘
                                                                │
                                                                ▼
                                                       ┌────────────────┐
                                                       │  PumlGenerator  │
                                                       │  (relationships │
                                                       │   + generics)   │
                                                       └────────────────┘
```

### 3.2 New/Modified Components

| Component | Change |
|-----------|--------|
| `JavaSourceScanner` | **Rewritten** to use JavaParser's `CompilationUnit` and `Visitor` APIs. The public contract (`ScanResult`) stays the same. |
| `TypeModel` | **Extended** to support: `extends`, `implements`, `permits`, `typeParameters` (generics), `annotations`, `recordComponents`, `enumConstants`, `innerTypes`. |
| `PumlGenerator` | **Enhanced** to render relationships (`<\|--`, `..\|>`, `--\|>`, `..`). |
| `build.gradle` | Add `com.github.javaparser:javaparser-core` dependency. |
| `ClandPlantuml` | **No change** — CLI interface remains identical. |

### 3.3 TypeModel Enrichment

```java
public class TypeModel {
    public final String name;
    public final String packageName;
    public final String kind; // class, interface, enum, record, annotation
    public final String visibility;
    public final List<Member> fields = new ArrayList<>();
    public final List<Member> methods = new ArrayList<>();

    // === NEW FIELDS ===
    public final String superClass;                    // e.g. "java.util.ArrayList"
    public final List<String> interfaces = new ArrayList<>(); // e.g. ["java.util.List", "java.io.Serializable"]
    public final List<String> permits = new ArrayList<>();    // sealed class permits
    public final List<String> typeParameters = new ArrayList<>(); // e.g. ["T", "K extends Number"]
    public final List<String> annotations = new ArrayList<>();
    public final List<String> recordComponents = new ArrayList<>(); // record component names
    public final List<String> enumConstants = new ArrayList<>();
    public final List<TypeModel> innerTypes = new ArrayList<>();
    public final List<String> dependencies = new ArrayList<>(); // resolved type references (for relationships)
}
```

### 3.4 Relationship Detection

With AST information, the `PumlGenerator` can emit PlantUML relationship arrows:

| Relationship | PlantUML | Detection |
|-------------|----------|-----------|
| **Extension** | `class A extends B` → `B <\|-- A` | `superClass` field |
| **Implementation** | `class A implements B` → `B <\|.. A` | `interfaces` list |
| **Sealed permits** | `sealed class A permits B, C` → `A <\|-- B`, `A <\|-- C` | `permits` list |
| **Field type dependency** | `class A { B b; }` → `A --> B` | Resolved field types |
| **Method return/param dependency** | `class A { B foo(C c); }` → `A --> B`, `A --> C` | Resolved method types |
| **Annotation** | `@Deprecated` → `<<Deprecated>>` stereotype | Annotation list |

## 4. Implementation Plan

### Phase 1: Dependency & Setup

1. Add to `build.gradle`:
   ```groovy
   implementation 'com.github.javaparser:javaparser-core:3.26.2'
   ```
2. Run `./gradlew build` to verify dependency resolves.

### Phase 2: AST Scanner (rewrite `JavaSourceScanner`)

1. Rewrite `JavaSourceScanner` to use JavaParser:
   - Use `StaticJavaParser.parse(file)` to obtain a `CompilationUnit` for **each source file encountered** during the file tree walk.
   - This is entirely standalone — JavaParser does not need the scanned project's classpath, modules, or dependencies.
   - Visit `TypeDeclaration` nodes (class, interface, enum, record).
   - For each type, extract:
     - Name, package, modifiers (visibility, abstract, final, static, sealed)
     - Extended type, implemented types, permitted types
     - Type parameters
     - Annotations
     - Fields (with resolved types)
     - Methods (with return type, parameter types)
     - Record components
     - Enum constants
     - Inner types (recursive)
   - Resolve fully qualified names using the file's own import declarations (no symbol solver needed for basic relationship arrows).

2. Package dependencies are still extracted from the import statements (same logic).

3. The public interface `ScanResult` is preserved for backward compatibility.

4. **Concrete example of scanning another project**:
   ```java
   // Under the hood, the rewritten scanner does:
   for (Path file : collectJavaFiles()) {
       CompilationUnit cu = StaticJavaParser.parse(file);
       cu.findAll(ClassOrInterfaceDeclaration.class).forEach(type -> {
           // extract name, package, extends, implements, fields, methods...
           // all fully-qualified via imports list
       });
   }
   ```

### Phase 3: Update TypeModel

1. Add fields listed in §3.3.
2. Update existing tests to reflect new fields.
3. Add a builder or constructor overloads.

### Phase 4: Enhance PumlGenerator

1. Add relationship rendering logic.
2. Update class diagram generation to:
   - Emit `extends` arrows.
   - Emit `implements` arrows.
   - Emit field/method dependency arrows (`-->`).
   - Show generics in type signatures.
   - Show annotations as stereotypes.
3. Add a `--relationships` / `--no-relationships` CLI flag (default: `true`).

### Phase 5: Testing

1. Write Spock tests for `AstScanner` covering:
   - Standard class, interface, enum, record, annotation
   - Generics (`class Box<T extends Number>`)
   - Sealed classes and permits
   - Inner/nested classes
   - Enum with fields and methods
   - Record with components
   - Annotations on types/fields/methods
2. Write Spock tests for relationship rendering in `PumlGenerator`.
3. Run full test suite and verify no regressions.

## 5. CLI Backward Compatibility

The existing CLI flags and their semantics remain unchanged:

| Flag | Unchanged? | Notes |
|------|-----------|-------|
| `-s`, `--source-dirs` | ✅ | |
| `-o`, `--output` | ✅ | |
| `-r`, `--render` | ✅ | |
| `-i`, `--include` | ✅ | Still applied at the diagram level |
| `-e`, `--exclude` | ✅ | |
| `--hide-java-lang` | ✅ | |
| `--only-public` | ✅ | |
| `--package-diagram` | ✅ | |
| `-v`, `--verbose` | ✅ | |

New optional flags:

| Flag | Default | Description |
|------|---------|-------------|
| `--relationships` | `true` | Show relationships (extends, implements, field deps) |
| `--show-dependencies` | `false` | Show field/method type dependency arrows |
| `--show-annotations` | `true` | Show annotations as stereotypes |

## 6. Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| **JavaParser version mismatch** | Pin to a known-stable version and test against Java 25 features. |
| **Performance overhead** | JavaParser is fast for single-file parsing. For large codebases, consider parallel parsing with `Files.walkFileTree` + parallel stream. |
| **Symbol resolution complexity** | Start with simple name resolution (no symbol solver). Add `javaparser-symbol-solver` as an optional phase if fully qualified names are needed for relationship arrows. |
| **Build size increase** | `javaparser-core` is ~2 MB — acceptable for this project. No other heavy dependencies introduced. |
| **Existing test breakage** | Keep the old `JavaSourceScanner` as a fallback during transition. Dual-run tests to compare outputs. |

## 7. Future Work

- **Symbol solver integration**: Resolve fully qualified names across compilation units for accurate relationship diagrams.
- **Lombok support**: Detect Lombok annotations (`@Getter`, `@Setter`, `@Data`, etc.) and generate synthetic members.
- **Spring Boot support**: Detect `@RestController`, `@Service`, `@Repository` and group types into layered diagrams.
- **Javadoc extraction**: Include class/method descriptions as PlantUML notes.
- **Incremental scanning**: Cache parsed ASTs and only re-parse changed files.
- **Export to Mermaid**: Generate alternate output format (for GitHub-flavoured Markdown).
