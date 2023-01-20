// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.util.cli;

import com.google.common.base.CaseFormat;
import com.google.common.reflect.ClassPath;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility to generate Protocol Buffers (*.proto) files from existing POJO API types.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Print proto representation of all API objects: {@code bazelisk run
 *       java/com/google/gerrit/util/cli:protogen}
 * </ul>
 */
public class ApiProtocolBufferGenerator {
  private static String NOTICE =
      "// Copyright (C) 2023 The Android Open Source Project\n"
          + "//\n"
          + "// Licensed under the Apache License, Version 2.0 (the \"License\");\n"
          + "// you may not use this file except in compliance with the License.\n"
          + "// You may obtain a copy of the License at\n"
          + "//\n"
          + "// http://www.apache.org/licenses/LICENSE-2.0\n"
          + "//\n"
          + "// Unless required by applicable law or agreed to in writing, software\n"
          + "// distributed under the License is distributed on an \"AS IS\" BASIS,\n"
          + "// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
          + "// See the License for the specific language governing permissions and\n"
          + "// limitations under the License.";

  private static String PACKAGE = "com.google.gerrit.extensions.common";

  public static void main(String[] args) {
    try {
      ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses().stream()
          .filter(c -> c.getPackageName().equalsIgnoreCase(PACKAGE))
          .filter(c -> c.getName().endsWith("Input") || c.getName().endsWith("Info"))
          .map(clazz -> clazz.load())
          .forEach(ApiProtocolBufferGenerator::exportSingleClass);
    } catch (Exception e) {
      System.err.println(e);
    }
  }

  private static void exportSingleClass(Class<?> clazz) {
    StringBuilder proto = new StringBuilder(NOTICE);
    proto.append("\n\nsyntax = \"proto3\";");
    proto.append("\n\npackage gerrit.api;");
    proto.append("\n\noption java_package = \"" + PACKAGE + "\";");

    int fieldNumber = 1;

    proto.append("\n\n\nmessage " + clazz.getSimpleName() + " {\n");

    for (Field f : clazz.getFields()) {
      Class<?> type = f.getType();

      if (type.isAssignableFrom(List.class)) {
        ParameterizedType list = (ParameterizedType) f.getGenericType();
        Class<?> genericType = (Class<?>) list.getActualTypeArguments()[0];
        String protoType =
            protoType(genericType)
                .orElseThrow(() -> new IllegalStateException("unknown type: " + genericType));
        proto.append(
            String.format(
                "repeated %s %s = %d;\n", protoType, protoName(f.getName()), fieldNumber));
      } else if (type.isAssignableFrom(Map.class)) {
        ParameterizedType map = (ParameterizedType) f.getGenericType();
        Class<?> key = (Class<?>) map.getActualTypeArguments()[0];
        if (map.getActualTypeArguments()[1] instanceof ParameterizedType) {
          // TODO: This is list multimap which proto doesn't support. Move to
          // it's own types.
          proto.append(
              "reserved "
                  + fieldNumber
                  + "; // TODO(hiesel): Add support for map<?,repeated <?>>\n");
        } else {
          Class<?> value = (Class<?>) map.getActualTypeArguments()[1];
          String keyProtoType =
              protoType(key).orElseThrow(() -> new IllegalStateException("unknown type: " + key));
          String valueProtoType =
              protoType(value)
                  .orElseThrow(() -> new IllegalStateException("unknown type: " + value));
          proto.append(
              String.format(
                  "map<%s,%s> %s = %d;\n",
                  keyProtoType, valueProtoType, protoName(f.getName()), fieldNumber));
        }
      } else if (protoType(type).isPresent()) {
        proto.append(
            String.format(
                "%s %s = %d;\n", protoType(type).get(), protoName(f.getName()), fieldNumber));
      } else {
        proto.append(
            "reserved "
                + fieldNumber
                + "; // TODO(hiesel): Add support for "
                + type.getName()
                + "\n");
      }
      fieldNumber++;
    }
    proto.append("}");

    System.out.println(proto);
  }

  private static Optional<String> protoType(Class<?> type) {
    if (isInt(type)) {
      return Optional.of("int32");
    } else if (isLong(type)) {
      return Optional.of("int64");
    } else if (isChar(type)) {
      return Optional.of("string");
    } else if (isShort(type)) {
      return Optional.of("int32");
    } else if (isShort(type)) {
      return Optional.of("int32");
    } else if (isBoolean(type)) {
      return Optional.of("bool");
    } else if (type.isAssignableFrom(String.class)) {
      return Optional.of("string");
    } else if (type.isAssignableFrom(Timestamp.class)) {
      // See https://gerrit-review.googlesource.com/Documentation/rest-api.html#timestamp
      return Optional.of("string");
    } else if (type.getPackageName().startsWith("com.google.gerrit.extensions")) {
      return Optional.of("gerrit.api." + type.getSimpleName());
    }
    return Optional.empty();
  }

  private static boolean isInt(Class<?> type) {
    return type.isAssignableFrom(Integer.class) || type.isAssignableFrom(int.class);
  }

  private static boolean isLong(Class<?> type) {
    return type.isAssignableFrom(Long.class) || type.isAssignableFrom(long.class);
  }

  private static boolean isChar(Class<?> type) {
    return type.isAssignableFrom(Character.class) || type.isAssignableFrom(char.class);
  }

  private static boolean isShort(Class<?> type) {
    return type.isAssignableFrom(Short.class) || type.isAssignableFrom(short.class);
  }

  private static boolean isBoolean(Class<?> type) {
    return type.isAssignableFrom(Boolean.class) || type.isAssignableFrom(boolean.class);
  }

  private static String protoName(String name) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
  }
}
