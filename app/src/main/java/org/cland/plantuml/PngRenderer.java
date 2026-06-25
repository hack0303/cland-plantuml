package org.cland.plantuml;

import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Renders PlantUML .puml content to PNG images.
 */
public class PngRenderer {

    /**
     * Renders a .puml file to PNG. The PNG file is created alongside the .puml file.
     *
     * @param pumlFile path to the .puml file
     * @return the path to the generated PNG file
     * @throws IOException if reading/writing fails
     */
    public Path render(Path pumlFile) throws IOException {
        String source = Files.readString(pumlFile);
        String pngName = pumlFile.getFileName().toString().replace(".puml", ".png");
        Path pngFile = pumlFile.resolveSibling(pngName);

        SourceStringReader reader = new SourceStringReader(source);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            String desc = reader.outputImage(baos);
            if (desc != null && !desc.contains("error")) {
                Files.write(pngFile, baos.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                throw new IOException("PlantUML rendering error: " + desc);
            }
        }

        return pngFile;
    }
}
