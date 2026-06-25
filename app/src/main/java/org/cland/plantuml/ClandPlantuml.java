/*
 * cland-plantuml — Java source code to PlantUML diagram generator
 *
 * Scans Java source files, generates PlantUML class/package diagrams,
 * and optionally renders them to PNG images.
 *
 * Usage:
 *   java -jar cland-plantuml.jar --source-dir src/main/java --output build/uml
 *   java -jar cland-plantuml.jar -s src/main/java -o build/uml --render
 */
package org.cland.plantuml;

import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "cland-plantuml",
    version = "0.1.0",
    description = "Generate PlantUML class/package diagrams from Java source code",
    mixinStandardHelpOptions = true
)
public class ClandPlantuml implements Callable<Integer> {

    @Parameters(
        index = "0..*",
        paramLabel = "SOURCE_DIRS",
        description = "Source directories to scan (default: current directory)"
    )
    private Path[] sourceDirs = {Paths.get(".")};

    @Option(
        names = {"-o", "--output"},
        description = "Output directory for generated .puml and .png files (default: build/uml)"
    )
    private Path outputDir = Paths.get("build/uml");

    @Option(
        names = {"-r", "--render"},
        description = "Render .puml diagrams to PNG (requires PlantUML engine)"
    )
    private boolean render = false;

    @Option(
        names = {"-i", "--include"},
        description = "Package/class include patterns (default: '**')",
        split = ","
    )
    private List<String> includes = List.of("**");

    @Option(
        names = {"-e", "--exclude"},
        description = "Package/class exclude patterns",
        split = ","
    )
    private List<String> excludes = List.of();

    @Option(
        names = {"--hide-java-lang"},
        description = "Hide java.lang classes from diagram (default: true)",
        defaultValue = "true",
        negatable = true
    )
    private boolean hideJavaLang = true;

    @Option(
        names = {"--only-public"},
        description = "Only show public members (default: true)",
        defaultValue = "true",
        negatable = true
    )
    private boolean onlyPublic = true;

    @Option(
        names = {"--package-diagram"},
        description = "Generate package dependency diagram (default: true)",
        defaultValue = "true",
        negatable = true
    )
    private boolean packageDiagram = true;

    @Option(
        names = {"-v", "--verbose"},
        description = "Verbose output"
    )
    private boolean verbose = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ClandPlantuml()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws IOException {
        long start = System.currentTimeMillis();

        // Create output directory
        Files.createDirectories(outputDir);

        // Scan source files
        List<Path> dirs = Arrays.asList(sourceDirs);
        JavaSourceScanner scanner = new JavaSourceScanner(includes, excludes, dirs);

        if (verbose) {
            System.out.println("Scanning " + dirs.size() + " source directories...");
        }

        JavaSourceScanner.ScanResult result = scanner.scan();

        if (verbose) {
            System.out.println("Found " + result.types.size() + " types in "
                + dirs.size() + " directories");
        }

        if (result.types.isEmpty()) {
            System.err.println("Warning: No Java source files found matching the include patterns.");
            System.err.println("  Source dirs: " + dirs);
            System.err.println("  Includes:    " + includes);
            return 1;
        }

        // Generate class diagram
        PumlGenerator generator = new PumlGenerator(hideJavaLang, onlyPublic);
        String classDiagram = generator.generateClassDiagram(result);
        Path classPuml = outputDir.resolve("class-diagram.puml");
        Files.writeString(classPuml, classDiagram);
        System.out.println("Generated: " + classPuml + " (" + result.types.size() + " types)");

        // Generate package diagram
        if (packageDiagram) {
            String pkgDiagram = generator.generatePackageDiagram(result);
            Path pkgPuml = outputDir.resolve("package-diagram.puml");
            Files.writeString(pkgPuml, pkgDiagram);
            System.out.println("Generated: " + pkgPuml);
        }

        // Render to PNG
        if (render) {
            PngRenderer renderer = new PngRenderer();
            try {
                Path png = renderer.render(classPuml);
                System.out.println("Rendered:  " + png);
            } catch (Exception e) {
                System.err.println("Error rendering class diagram: " + e.getMessage());
            }

            if (packageDiagram) {
                try {
                    Path pkgPuml = outputDir.resolve("package-diagram.puml");
                    Path png = renderer.render(pkgPuml);
                    System.out.println("Rendered:  " + png);
                } catch (Exception e) {
                    System.err.println("Error rendering package diagram: " + e.getMessage());
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        if (verbose) {
            System.out.println("Done in " + elapsed + "ms");
        }

        return 0;
    }
}
