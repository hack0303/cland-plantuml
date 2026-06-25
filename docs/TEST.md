# Test Workflow

End-to-end test: scan a real Java project, generate `.puml`, render to `.png`.

## Quick Test

```bash
# 1. Build
./gradlew clean :app:installDist

# 2. Scan target project → generate .puml + render .png
./app/build/install/cland-plantuml/bin/cland-plantuml \
  /mnt/develop/work/agentic/alice-agent/alice-bootstrap/src/main/java \
  /mnt/develop/work/agentic/alice-agent/alice-bootstrap/src/hole/java \
  -o /tmp/alice-uml-test \
  --render \
  -v
```

## Verify Output

```bash
# 3. Check generated files
ls -la /tmp/alice-uml-test/
```

Expected:

| File | Description |
|------|-------------|
| `class-diagram.puml` | PlantUML class diagram source |
| `class-diagram.png` | Rendered class diagram |
| `package-diagram.puml` | PlantUML package diagram source |
| `package-diagram.png` | Rendered package diagram |

## Inspect .puml Content

```bash
# 4. Check class diagram text
cat /tmp/alice-uml-test/class-diagram.puml
```

Check for:

- [ ] All types found (classes, interfaces, enums, records)
- [ ] Fields and methods shown with visibility symbols (`+`, `-`, `#`, `~`)
- [ ] `implements` clauses on class declarations
- [ ] Interface methods detected
- [ ] **No false positives** from inner classes
- [ ] Relationship arrows: `-->` (dependency), `<|--` (extends), `<|..` (implements)
- [ ] No arrows to unknown types (only types present in diagram)

## View PNG

```bash
# 5. Open rendered images
xdg-open /tmp/alice-uml-test/class-diagram.png
xdg-open /tmp/alice-uml-test/package-diagram.png
```

## CLI Flag Tests

```bash
# Hide relationships (no arrows)
./app/build/install/cland-plantuml/bin/cland-plantuml \
  /path/to/src --hide-relationships -o /tmp/test-norel

# Only show public members
./app/build/install/cland-plantuml/bin/cland-plantuml \
  /path/to/src --only-public -o /tmp/test-public

# Skip package diagram
./app/build/install/cland-plantuml/bin/cland-plantuml \
  /path/to/src --no-package-diagram -o /tmp/test-nopkg
```

## Test Data

| Project | Path | Types | Notes |
|---------|------|-------|-------|
| alice-bootstrap | `/mnt/develop/work/agentic/alice-agent/alice-bootstrap` | 4 | Has inner class, SPI interface, implements relationship |
| *(add more)* | | | |

## Requirements

- `dot` (Graphviz): `sudo apt-get install -y graphviz`
- Java 25+
