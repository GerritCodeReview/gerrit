package com.google.gerrit.pgm;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.pgm.util.AbstractProgram;
import com.google.gerrit.proto.ClassDoc;
import com.google.gerrit.proto.FieldDoc;
import java.io.IOException;
import java.lang.reflect.Field;

/*
Experiment with .proto generation.

Problems/ideas:

 - Use JDK javadoc doclet to extract Javadoc comments,
   export those as documentation/MD/ascidoc.
 - how to ingest existing doc into the Java source code?
 - Timestamps?
 - TypeScript types?
 - Map<> types?
 - Enums?
 - Doc to protobuf documentation?

- reflection-based Protobuf generation?
*/
public class Proto extends AbstractProgram {

  @Override
  public int run() throws IOException {
    System.out.println("\n\nDOC: \n");
    doc(ChangeInfo.class);
    System.out.println("\n\nPROTODEF: \n");
    protoDef(ChangeInfo.class);
    System.out.println("\n\nPROTO: \n");
    return 0;
  }

  public void proto() {}

  protected void doc(Class klazz) {
    ClassDoc classDoc = (ClassDoc) klazz.getAnnotation(ClassDoc.class);

    System.out.println("=== " + klazz.getName());
    System.out.println(classDoc.doc());
    System.out.println("[options=\"header\",cols=\"1,^1,5\"]");
    System.out.println("|==================================");

    for (Field f : klazz.getFields()) {
      FieldDoc fieldDoc = f.getAnnotation(FieldDoc.class);
      if (fieldDoc != null) {
        String typ = normalizeType(f.getType().getName());
        System.out.println(f.getName() + "|" + typ + "|" + (fieldDoc.optional() ? "optional" : ""));
      }
    }
    System.out.println();
    System.out.println("|==================================");
  }

  private String normalizeType(String typ) {
    for (String prefix : ImmutableList.of("java.lang.", "com.google.gerrit.extensions.common.")) {
      if (typ.startsWith(prefix)) {
        typ = typ.substring(prefix.length());
      }
    }

    if (typ.equals("String")) {
      typ = "string";
    }
    return typ;
  }

  protected void protoDef(Class klazz) {
    System.out.println("message " + normalizeType(klazz.getName()) + " {");
    for (Field f : klazz.getFields()) {
      FieldDoc fieldDoc = f.getAnnotation(FieldDoc.class);
      String typ = normalizeType(f.getType().getName());

      if (fieldDoc != null) {
        System.out.println("   " + typ + " " + f.getName() + " = " + fieldDoc.protoTag() + ";");
      }
    }
    System.out.println("}");
  }
}
