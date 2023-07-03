package com.google.gerrit.pgm;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ClassDoc;
import com.google.gerrit.extensions.common.FieldDoc;
import com.google.gerrit.pgm.util.AbstractProgram;
import java.io.IOException;
import java.lang.reflect.Field;

/*
Experiment with .proto generation.

Problems/ideas:

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
    doc(ChangeInfo.class);
    proto(ChangeInfo.class);
    return 0;
  }

  protected void doc(Class klazz) {
    ClassDoc classDoc = (ClassDoc) klazz.getAnnotation(ClassDoc.class);

    System.out.println("=== " + klazz.getName());
    System.out.println(classDoc.doc());
    System.out.println("[options=\"header\",cols=\"1,^1,5\"]");
    System.out.println("|==================================");

    for (Field f : klazz.getFields()) {
      FieldDoc fieldDoc = f.getAnnotation(FieldDoc.class);
      if (fieldDoc != null) {
        String typ = stripPrefix(f.getType().getName());
        System.out.println(
            f.getName()
                + "|"
                + typ
                + "|"
                + (fieldDoc.optional() ? "optional" : "")
                + "|"
                + fieldDoc.doc());
      }
    }
    System.out.println();
    System.out.println("|==================================");
  }

  private String stripPrefix(String typ) {
    for (String prefix : ImmutableList.of("java.lang.", "com.google.gerrit.extensions.common.")) {
      if (typ.startsWith(prefix)) {
        typ = typ.substring(prefix.length());
      }
    }
    return typ;
  }

  protected void proto(Class klazz) {
    System.out.println("message " + stripPrefix(klazz.getName()) + " {");
    for (Field f : klazz.getFields()) {
      FieldDoc fieldDoc = f.getAnnotation(FieldDoc.class);
      String typ = stripPrefix(f.getType().getName());

      if (fieldDoc != null) {
        System.out.println("   " + typ + " " + f.getName() + " = " + fieldDoc.protoTag() + ";");
      }
    }
    System.out.println("}");
  }
}
