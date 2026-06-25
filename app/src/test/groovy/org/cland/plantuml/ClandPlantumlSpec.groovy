/*
 * cland-plantuml — Java source to PlantUML diagram generator
 */
package org.cland.plantuml

import picocli.CommandLine
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ClandPlantumlSpec extends Specification {

    @TempDir
    Path tempDir

    private PrintWriter nullWriter() {
        return new PrintWriter(Writer.nullWriter())
    }

    def "application scans Java source files and generates .puml output"() {
        given: "a simple Java source file in a temp directory"
        Path srcDir = tempDir.resolve("src")
        Files.createDirectories(srcDir)
        Path javaFile = srcDir.resolve("Hello.java")
        Files.writeString(javaFile, """
package com.example;

public class Hello {
    private String name;

    public Hello(String name) {
        this.name = name;
    }

    public String greet() {
        return "Hello, " + name;
    }
}
""")

        and: "an output directory"
        Path outDir = tempDir.resolve("build/uml")

        when: "running the application via picocli with the source directory"
        def app = new ClandPlantuml()
        int exitCode = new CommandLine(app)
                .setOut(nullWriter())
                .setErr(nullWriter())
                .execute(srcDir.toString(), "-o", outDir.toString())

        then: "exit code is 0"
        exitCode == 0

        and: "class diagram file is generated"
        Files.exists(outDir.resolve("class-diagram.puml"))
    }

    def "class diagram contains the scanned type"() {
        given: "a Java source file with one class"
        Path srcDir = tempDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.writeString(srcDir.resolve("Hello.java"), """
package com.example;

public class Hello {
    private String name;

    public String greet() {
        return "Hi";
    }
}
""")

        and: "an output directory"
        Path outDir = tempDir.resolve("uml")

        when: "running the application"
        def app = new ClandPlantuml()
        int exitCode = new CommandLine(app)
                .setOut(nullWriter())
                .setErr(nullWriter())
                .execute(srcDir.toString(), "-o", outDir.toString())

        then: "exit code is 0"
        exitCode == 0

        and: "the class diagram contains the class name"
        def content = Files.readString(outDir.resolve("class-diagram.puml"))
        content.contains("Hello")
    }

    def "application returns exit code 1 when no source files found"() {
        given: "an empty directory"
        Path emptyDir = tempDir.resolve("empty")
        Files.createDirectories(emptyDir)

        and: "an output directory"
        Path outDir = tempDir.resolve("uml")

        when: "running the application on the empty directory"
        def app = new ClandPlantuml()
        int exitCode = new CommandLine(app)
                .setOut(nullWriter())
                .setErr(nullWriter())
                .execute(emptyDir.toString(), "-o", outDir.toString())

        then: "exit code is 1 indicating no files found"
        exitCode == 1
    }
}
