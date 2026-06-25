/*
 * cland-plantuml — Java source to PlantUML diagram generator
 */
package org.cland.plantuml;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.Type;

/**
 * Scans Java source files using JavaParser AST and extracts type metadata (classes, interfaces,
 * fields, methods, relationships).
 *
 * <p>Supports filtering via includes/excludes glob patterns. Package dependencies are extracted
 * from import statements.
 */
public class JavaSourceScanner {

		private final List<Pattern> includePatterns;
		private final List<Pattern> excludePatterns;
		private final List<Path> sourceDirs;

		public JavaSourceScanner(List<String> includes, List<String> excludes, List<Path> sourceDirs) {
				this.includePatterns = compileGlobs(includes.isEmpty() ? List.of("**") : includes);
				this.excludePatterns = compileGlobs(excludes);
				this.sourceDirs = sourceDirs;

				// Configure JavaParser for latest Java features (records, sealed, pattern matching)
				ParserConfiguration config = new ParserConfiguration();
				// Set to the highest supported language level
				config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
				StaticJavaParser.setConfiguration(config);
		}

		/** Scans all Java source files and returns parsed type models and package dependencies. */
		public ScanResult scan() throws IOException {
				// Use LinkedHashMap to preserve insertion order
				Map<String, TypeModel> types = new LinkedHashMap<>();
				Map<String, Set<String>> packageDeps = new HashMap<>();
				// Track which packages have been seen (for filtering)
				Set<String> seenPackages = new HashSet<>();

				List<Path> javaFiles = collectJavaFiles();
				for (Path file : javaFiles) {
						parseFile(file, types, packageDeps, seenPackages);
				}

				Map<String, TypeModel> filtered = filterTypes(types);
				return new ScanResult(filtered, packageDeps);
		}

		// ── File collection ──

		private List<Path> collectJavaFiles() throws IOException {
				List<Path> files = new ArrayList<>();
				for (Path dir : sourceDirs) {
						if (!Files.exists(dir)) continue;
						Files.walkFileTree(
										dir,
										new SimpleFileVisitor<Path>() {
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

		// ── AST parsing ──

		private void parseFile(
						Path file,
						Map<String, TypeModel> types,
						Map<String, Set<String>> packageDeps,
						Set<String> seenPackages)
						throws IOException {

				String content = Files.readString(file, StandardCharsets.UTF_8);

				// Skip module-info.java — not a type declaration
				if (file.getFileName().toString().equals("module-info.java")) return;

				CompilationUnit cu;
				try {
						cu = StaticJavaParser.parse(content);
				} catch (Exception e) {
						// If JavaParser fails, skip the file (malformed source)
						System.err.println("Warning: Failed to parse " + file + ": " + e.getMessage());
						return;
				}

				// Extract package name
				String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

				seenPackages.add(pkg);

				// Build import map: simple name → fully qualified name
				// e.g. "List" → "java.util.List", "AliceFacade" → "org.cland.alice.agent.spi.AliceFacade"
				Map<String, String> importMap = buildImportMap(cu, pkg);

				// Extract package dependencies from imports (same logic as before)
				cu.getImports()
								.forEach(
												imp -> {
														String impName = imp.getNameAsString();
														int dot = impName.lastIndexOf('.');
														if (dot > 0) {
																String impPkg = impName.substring(0, dot);
																if (!impPkg.equals(pkg)
																				&& !impPkg.startsWith("java.")
																				&& !impPkg.startsWith("javax.")) {
																		packageDeps
																						.computeIfAbsent(pkg, k -> new HashSet<>())
																						.add(impPkg);
																}
														}
												});

				// Visit all top-level type declarations
				for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
						processTypeDeclaration(typeDecl, pkg, importMap, types, true);
				}
		}

		/** Build a simple-name→qualified-name map from imports and same-package types. */
		private Map<String, String> buildImportMap(CompilationUnit cu, String pkg) {
				Map<String, String> map = new HashMap<>();
				// Same-package types are implicitly resolvable
				// (will be resolved during relationship phase — for now, return simple name)
				cu.getImports()
								.forEach(
												imp -> {
														String full = imp.getNameAsString();
														String simple = full.substring(full.lastIndexOf('.') + 1);
														// Only add if not a wildcard import
														if (!imp.isAsterisk()) {
																map.put(simple, full);
														}
												});
				// java.lang types are implicitly imported
				map.put("String", "java.lang.String");
				map.put("Object", "java.lang.Object");
				map.put("Integer", "java.lang.Integer");
				map.put("Long", "java.lang.Long");
				map.put("Double", "java.lang.Double");
				map.put("Boolean", "java.lang.Boolean");
				map.put("Void", "java.lang.Void");
				return map;
		}

		/**
		 * Process a type declaration and its inner types.
		 *
		 * @param addToGlobal if true, add this type to the global {@code types} map
		 */
		private TypeModel processTypeDeclaration(
						TypeDeclaration<?> typeDecl,
						String pkg,
						Map<String, String> importMap,
						Map<String, TypeModel> types,
						boolean addToGlobal) {

				// Determine kind
				String kind;
				if (typeDecl.isClassOrInterfaceDeclaration()) {
						ClassOrInterfaceDeclaration coi = typeDecl.asClassOrInterfaceDeclaration();
						kind = coi.isInterface() ? "interface" : "class";
				} else if (typeDecl.isEnumDeclaration()) {
						kind = "enum";
				} else if (typeDecl.isAnnotationDeclaration()) {
						kind = "annotation";
				} else if (typeDecl.isRecordDeclaration()) {
						kind = "record";
				} else {
						kind = "class";
				}

				String name = typeDecl.getNameAsString();
				String qualified = pkg.isEmpty() ? name : pkg + "." + name;

				// Skip if already seen
				if (types.containsKey(qualified)) return types.get(qualified);

				// Visibility
				String visibility = getVisibility(typeDecl);

				TypeModel type = new TypeModel(name, pkg, kind, visibility);

				// ── Annotations ──
				for (var ann : typeDecl.getAnnotations()) {
						type.annotations.add("@" + ann.getNameAsString());
				}

				// ── Type parameters (generics) ──
				if (typeDecl instanceof ClassOrInterfaceDeclaration coi) {
						for (var tp : coi.getTypeParameters()) {
								String tpStr = tp.getNameAsString();
								if (tp.getTypeBound() != null && !tp.getTypeBound().isEmpty()) {
										tpStr +=
														" extends "
																		+ tp.getTypeBound().stream()
																						.map(b -> b.getNameAsString())
																						.collect(Collectors.joining(" & "));
								}
								type.typeParameters.add(tpStr);
						}
				} else if (typeDecl instanceof RecordDeclaration rd) {
						for (var tp : rd.getTypeParameters()) {
								type.typeParameters.add(tp.getNameAsString());
						}
				}

				// ── Superclass ──
				if (typeDecl instanceof ClassOrInterfaceDeclaration coi) {
						coi.getExtendedTypes()
										.forEach(
														ext -> {
																type.superClass = resolveTypeName(ext, importMap, pkg);
														});
						coi.getImplementedTypes()
										.forEach(
														imp -> {
																String iface = resolveTypeName(imp, importMap, pkg);
																type.interfaces.add(iface);
																type.dependencies.add(iface);
														});
				} else if (typeDecl instanceof EnumDeclaration ed) {
						// enums implicitly extend java.lang.Enum
						type.superClass = "java.lang.Enum<" + qualified + ">";
				} else if (typeDecl instanceof RecordDeclaration rd) {
						rd.getImplementedTypes()
										.forEach(
														imp -> {
																String iface = resolveTypeName(imp, importMap, pkg);
																type.interfaces.add(iface);
																type.dependencies.add(iface);
														});
				}

				// ── Permits (sealed classes) ──
				if (typeDecl instanceof ClassOrInterfaceDeclaration coi) {
						coi.getPermittedTypes()
										.forEach(
														perm -> {
																type.permits.add(perm.getNameAsString());
														});
				}

				// ── Enum constants ──
				if (typeDecl instanceof EnumDeclaration ed) {
						for (var constant : ed.getEntries()) {
								type.enumConstants.add(constant.getNameAsString());
						}
				}

				// ── Record components ──
				if (typeDecl instanceof RecordDeclaration rd) {
						for (var param : rd.getParameters()) {
								String compType = param.getType().toString();
								String compName = param.getNameAsString();
								type.recordComponents.add(compName);
								// Record components become constructor params, shown as fields
								type.fields.add(new TypeModel.Member("public", compType, compName));
								// Track dependency
								resolveAndAddDependency(param.getType(), importMap, pkg, type);
						}
				}

				// ── Fields ──
				for (var field : typeDecl.getFields()) {
						String fVis = getVisibility(field);
						String fType = field.getCommonType().toString();
						String resolvedFieldType = resolveTypeName(field.getCommonType(), importMap, pkg);
						if (!resolvedFieldType.equals(fType)) {
								type.dependencies.add(resolvedFieldType);
						}
						for (var variable : field.getVariables()) {
								type.fields.add(new TypeModel.Member(fVis, fType, variable.getNameAsString()));
						}
				}

				// ── Methods ──
				for (var method : typeDecl.getMethods()) {
						String mVis = getVisibility(method);
						String mRet = method.getType().toString();
						String mName = method.getNameAsString();

						// Skip constructors (they have the same name as the type)
						if (mName.equals(name)) continue;

						type.methods.add(new TypeModel.Member(mVis, mRet, mName));

						// Track return type dependency
						String resolvedRet = resolveTypeName(method.getType(), importMap, pkg);
						if (!resolvedRet.equals(mRet) && !resolvedRet.equals("void")) {
								type.dependencies.add(resolvedRet);
						}

						// Track parameter type dependencies
						for (var param : method.getParameters()) {
								String resolvedParam = resolveTypeName(param.getType(), importMap, pkg);
								if (!resolvedParam.equals(param.getType().toString())) {
										type.dependencies.add(resolvedParam);
								}
						}

						// ── Method call extraction (for sequence diagrams) ──
						method.getBody()
										.ifPresent(
														body -> {
																body.findAll(MethodCallExpr.class)
																				.forEach(
																								callExpr -> {
																										String calledMethod =
																														callExpr.getNameAsString();
																										String targetType =
																														resolveCallTarget(
																																		callExpr, importMap, pkg);
																										type.methodCalls.add(
																														new TypeModel.MethodCall(
																																		qualified,
																																		mName,
																																		targetType,
																																		calledMethod));
																								});
														});
				}

				// ── Inner types (recursive) — NOT added to global types map ──
				for (var inner : typeDecl.getMembers()) {
						if (inner instanceof TypeDeclaration<?> innerDecl) {
								TypeModel innerType =
												processTypeDeclaration(innerDecl, pkg, importMap, types, false);
								type.innerTypes.add(innerType);
						}
				}

				// Also skip same-package standard types (like String[]) from dependency arrows
				if (addToGlobal) {
						types.put(qualified, type);
				}
				return type;
		}

		// ── Type name resolution ──

		/**
		 * Resolve the target type of a method call expression for sequence diagrams. Returns a
		 * best-effort fully qualified type name, or simple name if unresolved.
		 */
		private String resolveCallTarget(
						MethodCallExpr call, Map<String, String> importMap, String pkg) {
				// No scope → call on self (this)
				if (!call.getScope().isPresent()) {
						return "this";
				}
				Expression scope = call.getScope().get();

				// NameExpr → variable/class name, try to resolve via import map
				if (scope instanceof NameExpr nameExpr) {
						String name = nameExpr.getNameAsString();
						// Static method call on a class name?
						String qualified = importMap.get(name);
						if (qualified != null) {
								return qualified;
						}
						// Logger, List, etc. — use simple name if not known
						// Check if it's a known dependency from imports
						if (Character.isUpperCase(name.charAt(0))) {
								return name; // likely a class/type
						}
						return name; // likely a variable
				}

				// Other scopes (FieldAccessExpr, MethodCallExpr chaining, etc.)
				String scopeStr = scope.toString();
				if (scopeStr.contains(".")) {
						String first = scopeStr.split("\\.")[0];
						String resolved = importMap.get(first);
						if (resolved != null) {
								return resolved + "." + scopeStr.substring(first.length() + 1);
						}
				}

				return scopeStr;
		}

		/**
		 * Resolve a type to its fully qualified name using the import map, or return the simple name if
		 * unknown.
		 */
		private String resolveTypeName(Type type, Map<String, String> importMap, String pkg) {
				String raw = type.toString();

				// Strip generics and array brackets for resolution
				String baseName = raw.replaceAll("<.*?>", "").replaceAll("\\[\\]", "").trim();
				String arraySuffix = raw.endsWith("[]") ? "[]" : "";

				// If already qualified, return as-is
				if (baseName.contains(".")) return raw;

				// Check import map
				String qualified = importMap.get(baseName);
				if (qualified != null) {
						// Re-attach generics and array suffix if present
						int gt = raw.indexOf('<');
						if (gt >= 0) {
								return qualified + raw.substring(gt);
						}
						return qualified + arraySuffix;
				}

				// Check if it's a primitive or void
				switch (baseName) {
						case "byte":
						case "short":
						case "int":
						case "long":
						case "float":
						case "double":
						case "boolean":
						case "char":
						case "void":
								return baseName + arraySuffix;
				}

				// Same-package type, only if not empty
				if (!pkg.isEmpty() && !baseName.isEmpty()) {
						return pkg + "." + raw;
				}

				return raw;
		}

		private void resolveAndAddDependency(
						Type type, Map<String, String> importMap, String pkg, TypeModel model) {
				String resolved = resolveTypeName(type, importMap, pkg);
				String raw = type.toString();
				if (!resolved.equals(raw) && !resolved.equals("void")) {
						model.dependencies.add(resolved);
				}
		}

		// ── Visibility helper ──

		private String getVisibility(TypeDeclaration<?> decl) {
				if (decl.isPublic()) return "public";
				if (decl.isPrivate()) return "private";
				if (decl.isProtected()) return "protected";
				return "package";
		}

		private String getVisibility(FieldDeclaration decl) {
				if (decl.isPublic()) return "public";
				if (decl.isPrivate()) return "private";
				if (decl.isProtected()) return "protected";
				return "package";
		}

		private String getVisibility(MethodDeclaration decl) {
				if (decl.isPublic()) return "public";
				if (decl.isPrivate()) return "private";
				if (decl.isProtected()) return "protected";
				return "package";
		}

		// ── Filtering ──

		private Map<String, TypeModel> filterTypes(Map<String, TypeModel> types) {
				Map<String, TypeModel> result = new LinkedHashMap<>();
				for (Map.Entry<String, TypeModel> entry : types.entrySet()) {
						String qualified = entry.getKey();
						TypeModel type = entry.getValue();

						boolean included =
										includePatterns.isEmpty()
														|| includePatterns.stream()
																		.anyMatch(
																						p ->
																										p.matcher(qualified).matches()
																														|| p.matcher(type.packageName)
																																		.matches());

						boolean excluded =
										!excludePatterns.isEmpty()
														&& excludePatterns.stream()
																		.anyMatch(
																						p ->
																										p.matcher(qualified).matches()
																														|| p.matcher(type.packageName)
																																		.matches());

						if (included && !excluded) {
								result.put(qualified, type);
						}
				}
				return result;
		}

		// ── Glob pattern compilation ──

		private List<Pattern> compileGlobs(List<String> globs) {
				return globs.stream().map(this::globToRegex).collect(Collectors.toList());
		}

		private Pattern globToRegex(String glob) {
				String regex =
								glob.replace(".", "\\.")
												.replace("**", ".+?")
												.replace("*", "[^.]*")
												.replace("?", ".");
				return Pattern.compile(regex);
		}

		// ── Scan result ──

		public static class ScanResult {
				public final Map<String, TypeModel> types;
				public final Map<String, Set<String>> packageDeps;

				public ScanResult(Map<String, TypeModel> types, Map<String, Set<String>> packageDeps) {
						this.types = types;
						this.packageDeps = packageDeps;
				}
		}
}
