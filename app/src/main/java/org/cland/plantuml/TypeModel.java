/*
 * cland-plantuml — Java source to PlantUML diagram generator
 */
package org.cland.plantuml;

import java.util.ArrayList;
import java.util.List;

/** Data model for a parsed Java type (class, interface, enum, record, annotation). */
public class TypeModel {
		public final String name;
		public final String packageName;
		public final String kind; // class, interface, enum, record, annotation
		public final String visibility; // public, private, protected, package
		public final List<Member> fields = new ArrayList<>();
		public final List<Member> methods = new ArrayList<>();

		public TypeModel(String name, String packageName, String kind, String visibility) {
				this.name = name;
				this.packageName = packageName;
				this.kind = kind;
				this.visibility = visibility;
		}

		public String qualifiedName() {
				return packageName != null && !packageName.isEmpty() ? packageName + "." + name : name;
		}

		public static class Member {
				public final String visibility;
				public final String type;
				public final String name;

				public Member(String visibility, String type, String name) {
						this.visibility = visibility;
						this.type = type;
						this.name = name;
				}
		}
}
