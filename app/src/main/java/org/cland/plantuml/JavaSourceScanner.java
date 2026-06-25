package org.cland.plantuml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scans Java source files and extracts type metadata (classes, interfaces, fields, methods).
 * Supports filtering via includes/excludes glob patterns.
 */
public class JavaSourceScanner {

    // Pattern for package declaration
    private static final Pattern PACKAGE_PATTERN =
        Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");

    // Pattern for import statements
    private static final Pattern IMPORT_PATTERN =
        Pattern.compile("\\bimport\\s+(?:static\\s+)?([\\w.*]+)\\s*;");

    // Pattern for type declarations (class/interface/enum/record/@interface)
    private static final Pattern TYPE_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:(public|private|protected)\\s+)?"
        + "(?:abstract\\s+|final\\s+|sealed\\s+)?(?:static\\s+)?"
        + "(?:class|interface|enum|@interface|record)\\s+(\\w+)"
    );

    // Pattern for field declarations
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:(public|private|protected)\\s+)?"
        + "(?:static\\s+|final\\s+|transient\\s+|volatile\\s+)*"
        + "(\\S+(?:\\s*<[^>]*>)?(?:\\[\\])?)\\s+(\\w+)\\s*(?:=|;)"
    );

    // Pattern for method declarations
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:(public|private|protected)\\s+)?"
        + "(?:abstract\\s+|static\\s+|final\\s+|synchronized\\s+|native\\s+|default\\s+)*"
        + "(\\S+(?:\\s*<[^>]*>)?(?:\\[\\])?)\\s+(\\w+)\\s*\\("
    );

    // Keywords that should never be treated as field types or method returns
    private static final Set<String> KEYWORDS = Set.of(
        "return", "if", "else", "for", "while", "switch", "case",
        "throw", "new", "this", "super", "class", "interface",
        "enum", "record", "package", "import", "true", "false", "null",
        "catch", "try", "finally", "break", "continue", "do", "assert"
    );

    private final List<Pattern> includePatterns;
    private final List<Pattern> excludePatterns;
    private final List<Path> sourceDirs;

    public JavaSourceScanner(List<String> includes, List<String> excludes,
                             List<Path> sourceDirs) {
        this.includePatterns = compileGlobs(includes.isEmpty() ? List.of("**") : includes);
        this.excludePatterns = compileGlobs(excludes);
        this.sourceDirs = sourceDirs;
    }

    /**
     * Scans all Java source files and returns parsed type models and package dependencies.
     */
    public ScanResult scan() throws IOException {
        Map<String, TypeModel> types = new LinkedHashMap<>();
        Map<String, Set<String>> packageDeps = new HashMap<>();

        List<Path> javaFiles = collectJavaFiles();
        for (Path file : javaFiles) {
            parseFile(file, types, packageDeps);
        }

        // Apply filters
        Map<String, TypeModel> filtered = filterTypes(types);

        return new ScanResult(filtered, packageDeps);
    }

    // ── File collection ──

    private List<Path> collectJavaFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : sourceDirs) {
            if (!Files.exists(dir)) continue;
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return files;
    }

    // ── Source parsing ──

    private void parseFile(Path file, Map<String, TypeModel> types,
                           Map<String, Set<String>> packageDeps) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);

        // Extract package
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(content);
        if (!pkgMatcher.find()) return;
        String pkg = pkgMatcher.group(1);

        // Extract imports for package dependencies
        Matcher impMatcher = IMPORT_PATTERN.matcher(content);
        while (impMatcher.find()) {
            String imp = impMatcher.group(1);
            int dot = imp.lastIndexOf('.');
            if (dot > 0) {
                String impPkg = imp.substring(0, dot);
                if (!impPkg.equals(pkg) && !impPkg.startsWith("java.") && !impPkg.startsWith("javax.")) {
                    packageDeps.computeIfAbsent(pkg, k -> new HashSet<>()).add(impPkg);
                }
            }
        }

        // Clean content (remove comments, annotations, strings) for better pattern matching
        String cleaned = cleanContent(content);

        // Find type declarations
        Matcher typeMatcher = TYPE_PATTERN.matcher(cleaned);
        while (typeMatcher.find()) {
            String vis = typeMatcher.group(1) != null ? typeMatcher.group(1) : "package";
            String name = typeMatcher.group(2);
            String matchText = typeMatcher.group(0);

            String kind;
            if (matchText.contains("interface")) {
                kind = "interface";
            } else if (matchText.contains("enum")) {
                kind = "enum";
            } else if (matchText.contains("@interface")) {
                kind = "annotation";
            } else if (matchText.contains("record")) {
                kind = "record";
            } else {
                kind = "class";
            }

            String qualified = pkg + "." + name;
            if (types.containsKey(qualified)) continue;

            // Find type body
            int afterName = typeMatcher.end();
            if (afterName < cleaned.length() && cleaned.charAt(afterName) == '<') {
                int gt = cleaned.indexOf('>', afterName);
                if (gt > 0) afterName = gt + 1;
            }

            int brace = cleaned.indexOf('{', afterName);
            if (brace < 0) continue;

            int bodyEnd = findMatchingBrace(cleaned, brace);
            String body = (bodyEnd > brace)
                ? cleaned.substring(brace + 1, bodyEnd).trim()
                : "";

            TypeModel type = new TypeModel(name, pkg, kind, vis);

            if (!body.isEmpty()) {
                // Extract fields
                Matcher fieldMatcher = FIELD_PATTERN.matcher(body);
                while (fieldMatcher.find()) {
                    String fVis = fieldMatcher.group(1) != null ? fieldMatcher.group(1) : "package";
                    String fType = fieldMatcher.group(2);
                    String fName = fieldMatcher.group(3);
                    if (fType != null && fName != null
                        && !KEYWORDS.contains(fType)
                        && !fName.contains("(")
                        && !fName.isEmpty()) {
                        type.fields.add(new TypeModel.Member(fVis, fType, fName));
                    }
                }

                // Extract methods
                Matcher methodMatcher = METHOD_PATTERN.matcher(body);
                while (methodMatcher.find()) {
                    String mVis = methodMatcher.group(1) != null ? methodMatcher.group(1) : "package";
                    String mRet = methodMatcher.group(2);
                    String mName = methodMatcher.group(3);
                    if (mRet != null && mName != null
                        && !mName.equals(name) // skip constructors
                        && !KEYWORDS.contains(mName)
                        && mName.length() > 1) {
                        type.methods.add(new TypeModel.Member(mVis, mRet, mName));
                    }
                }
            }

            types.put(qualified, type);
        }
    }

    // ── Content cleaning ──

    private String cleanContent(String content) {
        // Remove block comments /* ... */
        String cleaned = content.replaceAll("/\\*.*?\\*/", " ");
        // Remove line comments // ...
        cleaned = cleaned.replaceAll("//.*", "");
        // Remove annotations @Foo(...)
        cleaned = cleaned.replaceAll("@\\w+(?:\\([^)]*\\))?", "");
        // Remove string literals
        cleaned = cleaned.replaceAll("\"[^\"]*\"", "\"\"");
        return cleaned;
    }

    private int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '"' || c == '\'') {
                i = skipStringLiteral(s, i, c);
            } else if (c == '/' && i + 1 < s.length()) {
                if (s.charAt(i + 1) == '/') {
                    int nl = s.indexOf('\n', i);
                    i = nl < 0 ? s.length() - 1 : nl;
                } else if (s.charAt(i + 1) == '*') {
                    int e = s.indexOf("*/", i + 2);
                    i = e < 0 ? s.length() - 1 : e + 1;
                }
            }
        }
        return s.length() - 1;
    }

    private int skipStringLiteral(String s, int pos, char quote) {
        for (int i = pos + 1; i < s.length(); i++) {
            if (s.charAt(i) == '\\') i++;
            else if (s.charAt(i) == quote) return i;
        }
        return s.length() - 1;
    }

    // ── Filtering ──

    private Map<String, TypeModel> filterTypes(Map<String, TypeModel> types) {
        Map<String, TypeModel> result = new LinkedHashMap<>();
        for (Map.Entry<String, TypeModel> entry : types.entrySet()) {
            String qualified = entry.getKey();
            TypeModel type = entry.getValue();

            boolean included = includePatterns.isEmpty()
                || includePatterns.stream().anyMatch(p ->
                    p.matcher(qualified).matches() || p.matcher(type.packageName).matches());

            boolean excluded = !excludePatterns.isEmpty()
                && excludePatterns.stream().anyMatch(p ->
                    p.matcher(qualified).matches() || p.matcher(type.packageName).matches());

            if (included && !excluded) {
                result.put(qualified, type);
            }
        }
        return result;
    }

    // ── Glob pattern compilation ──

    private List<Pattern> compileGlobs(List<String> globs) {
        return globs.stream()
            .map(this::globToRegex)
            .collect(Collectors.toList());
    }

    private Pattern globToRegex(String glob) {
        String regex = glob
            .replace(".", "\\.")
            .replace("**", ".+?")
            .replace("*", "[^.]*")
            .replace("?", ".");
        return Pattern.compile(regex);
    }

    // ── Scan result ──

    public static class ScanResult {
        public final Map<String, TypeModel> types;
        public final Map<String, Set<String>> packageDeps;

        public ScanResult(Map<String, TypeModel> types,
                          Map<String, Set<String>> packageDeps) {
            this.types = types;
            this.packageDeps = packageDeps;
        }
    }
}
