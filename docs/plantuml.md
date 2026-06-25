# PlantUML — Local Installation (Ubuntu)

PlantUML renders `.puml` diagrams to PNG/SVG. `cland-plantuml` generates `.puml` text; PlantUML + Graphviz visualize it.

## Quick Install

```bash
# 1. Graphviz (layout engine) — required for all rendering
sudo apt-get install -y graphviz

# 2. Verify
dot -V
# dot - graphviz version 2.43.0 (0)
```

## Install PlantUML

### Option A: From Gradle Cache (recommended for this project)

`cland-plantuml` already depends on `plantuml-gplv2` — the JAR is in the Gradle cache:

```bash
# Find the cached jar
find ~/.gradle -name "plantuml-gplv2*.jar" 2>/dev/null

# Copy to tools directory
mkdir -p ~/tools
cp $(find ~/.gradle -name "plantuml-gplv2*.jar" 2>/dev/null | head -1) ~/tools/plantuml.jar

# Create launcher script
cat > ~/tools/plantuml << 'EOF'
#!/usr/bin/env bash
exec java -jar "$(dirname "$0")/plantuml.jar" "$@"
EOF
chmod +x ~/tools/plantuml

# Verify
~/tools/plantuml -version | head -3
```

### Option B: Via apt (Ubuntu 24.04+)

```bash
sudo apt-get install -y plantuml
plantuml -version
```

### Option C: Download latest JAR manually

```bash
mkdir -p ~/tools
wget https://github.com/plantuml/plantuml/releases/latest/download/plantuml-gplv2-1.2025.0.jar \
  -O ~/tools/plantuml.jar

# Launcher script (same as Option A)
cat > ~/tools/plantuml << 'EOF'
#!/usr/bin/env bash
exec java -jar "$(dirname "$0")/plantuml.jar" "$@"
EOF
chmod +x ~/tools/plantuml
```

### Add to PATH

```bash
echo 'export PATH="$HOME/tools:$PATH"' >> ~/.bashrc
source ~/.bashrc
plantuml -version
```

## Usage

### Render a single .puml to PNG

```bash
plantuml /tmp/alice-uml-final/class-diagram.puml
# Output: /tmp/alice-uml-final/class-diagram.png
```

### Batch render directory

```bash
plantuml /tmp/alice-uml-final/*.puml
```

### Output formats

```bash
plantuml -tsvg diagram.puml        # SVG
plantuml -darkmode diagram.puml    # Dark mode
plantuml -tpdf diagram.puml        # PDF (needs extra deps)
```

## Integration with cland-plantuml

`cland-plantuml` has a built-in renderer — use `--render` flag:

```bash
cland-plantuml /path/to/src --render
```

Prerequisites:

| Dependency | Install | Required for |
|-----------|---------|-------------|
| Graphviz `dot` | `sudo apt-get install -y graphviz` | All rendering |
| PlantUML jar | Bundled in `cland-plantuml` | Built-in `--render` |
| Java 11+ | `sudo apt-get install -y default-jre` | Running the JAR |

### Verify rendering works

```bash
cland-plantuml /path/to/src -o /tmp/test --render -v
ls -lh /tmp/test/*.png
```

If you see `Cannot run program "dot"` — Graphviz is missing.

## VS Code Extension (alternative)

For quick preview during development:

1. `sudo apt-get install -y graphviz`
2. Install VS Code extension: **PlantUML** by jebbs
3. Open any `.puml` file → `Alt+D` to preview

## Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `Cannot run program "dot"` | Graphviz not installed | `sudo apt-get install -y graphviz` |
| `java: command not found` | No JRE | `sudo apt-get install -y default-jre` |
| `OutOfMemoryError` | Diagram too large | `java -jar -Xmx512m plantuml.jar file.puml` |
| PlantUML renders blank image | Syntax error in `.puml` | Check stderr output for error line |

## References

- [PlantUML Website](https://plantuml.com/)
- [PlantUML Class Diagram Guide](https://plantuml.com/class-diagram)
- [Graphviz Download](https://graphviz.org/download/)
