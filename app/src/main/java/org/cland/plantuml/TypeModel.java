/*
 * cland-plantuml — Java source to PlantUML diagram generator
 */
package org.cland.plantuml;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for a parsed Java type (class, interface, enum, record, annotation). Populated by
 * {@link JavaSourceScanner} using JavaParser AST.
 */
public class TypeModel {
		public final String name;
		public final String packageName;
		public final String kind; // class, interface, enum, record, annotation
		public final String visibility; // public, private, protected, package
		public final List<Member> fields = new ArrayList<>();
		public final List<Member> methods = new ArrayList<>();

		// AST-enriched fields
		public String superClass; // fully qualified, e.g. "java.util.ArrayList"
		public final List<String> interfaces = new ArrayList<>();
		public final List<String> permits = new ArrayList<>();
		public final List<String> typeParameters = new ArrayList<>();
		public final List<String> annotations = new ArrayList<>();
		public final List<String> recordComponents = new ArrayList<>();
		public final List<String> enumConstants = new ArrayList<>();
		public final List<TypeModel> innerTypes = new ArrayList<>();
		public final List<String> dependencies = new ArrayList<>(); // resolved type references

		public TypeModel(String name, String packageName, String kind, String visibility) {
				this.name = name;
				this.packageName = packageName;
				this.kind = kind;
				this.visibility = visibility;
		}

		public String qualifiedName() {
				return packageName != null && !packageName.isEmpty() ? packageName + "." + name : name;
		}

		/** Human-readable label for the kind of type. */
		public String plantUmlKind() {
				return kind.equals("record") ? "class" : kind;
		}

		/** Whether this type is a record (shown as class with <<record>> stereotype). */
		public boolean isRecord() {
				return kind.equals("record");
		}

		/** Returns the type parameter clause, e.g. "<T extends Number>", or empty string if none. */
		public String typeParametersClause() {
				if (typeParameters.isEmpty()) return "";
				return "<" + String.join(", ", typeParameters) + ">";
		}

		/** Returns the extends clause for PlantUML, e.g. "extends java.util.ArrayList". */
		public String extendsClause() {
				if (superClass == null || superClass.isEmpty() || superClass.equals("java.lang.Object")) {
						return "";
				}
				return " extends " + superClass + typeParametersClause();
		}

		/**
		 * Returns the implements clause for PlantUML, e.g. "implements java.util.List,
		 * java.io.Serializable".
		 */
		public String implementsClause() {
				if (interfaces.isEmpty()) return "";
				return " implements " + String.join(", ", interfaces);
		}

		/** Relationship arrows to be emitted for this type. */
		public List<String> relationshipArrows() {
				List<String> arrows = new ArrayList<>();
				String qn = qualifiedName();
				if (superClass != null && !superClass.isEmpty() && !superClass.equals("java.lang.Object")) {
						arrows.add(superClass + " <|-- " + qn);
				}
				for (String iface : interfaces) {
						arrows.add(iface + " <|.. " + qn);
				}
				for (String dep : dependencies) {
						// Skip noisy dependencies
						if (isNoiseDependency(dep)) continue;
						arrows.add(qn + " --> " + dep);
				}
				return arrows;
		}

		/** Returns true if this dependency is noise and should not appear as an arrow. */
		private static boolean isNoiseDependency(String dep) {
				// java.lang types
				if (dep.startsWith("java.lang.")) return true;
				// Primitive types (including arrays)
				if (dep.matches("(byte|short|int|long|float|double|boolean|char|void)(\\[\\])?"))
						return true;
				// Kotlin/other common noise
				if (dep.startsWith("kotlin.")) return true;
				return false;
		}

		/** A recorded method call (for sequence diagrams). */
		public static class MethodCall {
				public final String callerType; // fully qualified caller
				public final String callerMethod;
				public final String targetType; // fully qualified target (or simple name if unresolved)
				public final String targetMethod;

				public MethodCall(
								String callerType, String callerMethod, String targetType, String targetMethod) {
						this.callerType = callerType;
						this.callerMethod = callerMethod;
						this.targetType = targetType;
						this.targetMethod = targetMethod;
				}
		}

		// Sequence diagram data: method calls extracted from method bodies
		public final List<MethodCall> methodCalls = new ArrayList<>();

		public static class Member {
				public final String visibility;
				public final String type;
				public final String name;

				public Member(String visibility, String type, String name) {
						this.visibility = visibility;
						this.type = type;
						this.name = name;
				}

				public String plantUmlSymbol() {
						switch (visibility) {
								case "public":
										return "+";
								case "private":
										return "-";
								case "protected":
										return "#";
								default:
										return "~";
						}
				}
		}
}
