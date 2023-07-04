package com.google.gerrit.proto;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
  RestProtoConverter converts Gerrit Java objects to protobuf objects (as defined in `rest.proto`)
  using the `protoTag` member of the `ProtoField` annotation. During conversion, it creates entities
  from the `rest.proto` definition, and checks for errors (name mismatches, missing enum values,
  etc.)

  TODO:
  * public ctors everywhere
  * explode nested Input/Info types (eg  SetReadyForReview)
  * nested maps

*/

public class RestProtoConverter {

  public static RestProtoConverter INSTANCE = new RestProtoConverter();

  // Proto
  Map<Class<?>, JavaToProto> byJavaType;
  Map<Class<? extends Message>, JavaToProto> byProtoType;

  Map<Class<?>, EnumDef> byJavaEnum;

  Map<EnumDescriptor, EnumDef> byProtoEnum;

  RestProtoConverter() {
    byJavaType = new HashMap<>();
    byProtoType = new HashMap<>();
    byProtoEnum = new HashMap<>();
    byJavaEnum = new HashMap<>();
  }

  JavaToProto getByJavaType(Class klass) {
    JavaToProto j2p = byJavaType.get(klass);
    if (j2p != null) return j2p;

    j2p = new JavaToProto(klass);
    byJavaType.put(klass, j2p);
    byProtoType.put(j2p.protoClass, j2p);
    return j2p;
  }

  JavaToProto getByProtoType(Message msg) {
    JavaToProto j2p = byProtoType.get(msg.getClass());
    if (j2p != null) return j2p;

    return getByJavaType(getJavaClass(msg.getDescriptorForType().getName()));
  }

  static List<String> API_PACKAGES =
      ImmutableList.of(
          "com.google.gerrit.extensions.common",
          "com.google.gerrit.extensions.client",
          "com.google.gerrit.extensions.api.access",
          "com.google.gerrit.extensions.api.account",
          "com.google.gerrit.extensions.api.changes",
          "com.google.gerrit.extensions.api.config",
          "com.google.gerrit.extensions.api.groups",
          "com.google.gerrit.extensions.api.plugins",
          "com.google.gerrit.extensions.api.projects");

  private static Class getJavaClass(String simpleName) {
    for (String prefix : API_PACKAGES) {
      String name = prefix + "." + simpleName;
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException e) {
        // pass.
      }
    }
    throw new IllegalStateException("boom: " + simpleName);
  }

  EnumDef enumByJavaType(Class klass) {
    EnumDef d = byJavaEnum.get(klass);
    if (d != null) return d;

    d = new EnumDef(klass);
    byJavaEnum.put(klass, d);
    byProtoEnum.put(d.protoEnumDescriptor, d);
    return d;
  }

  EnumDef enumByProtoType(EnumDescriptor desc) {
    EnumDef d = byProtoEnum.get(desc);
    if (d != null) return d;

    Class<?> jclass = getJavaClass(desc.getName());
    return enumByJavaType(jclass);
  }

  private static class EnumDef {
    // Tricky: proto reflection doesn't want the Java enum object, but its value descriptor.

    Map<Object, EnumValueDescriptor> toProtoConstants;
    Map<EnumValueDescriptor, Object> fromProtoConstants;

    Class javaEnum;

    EnumDescriptor protoEnumDescriptor;

    Class<? extends ProtocolMessageEnum> protoClass;

    EnumDef(Class enumClass) {
      javaEnum = enumClass;
      toProtoConstants = new HashMap<>();
      fromProtoConstants = new HashMap<>();

      String protoName = "com.google.gerrit.proto.Rest$" + enumClass.getSimpleName();

      try {
        protoClass = (Class<? extends ProtocolMessageEnum>) Class.forName(protoName);
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("boom", e);
      }

      ProtocolMessageEnum[] protoConstants = protoClass.getEnumConstants();
      Object[] javaConstants = javaEnum.getEnumConstants();
      if (protoConstants.length != javaConstants.length) {
        throw new IllegalStateException(
            String.format(
                "java=%s != proto=%s",
                Arrays.toString(javaConstants), Arrays.toString(protoConstants)));
      }

      protoEnumDescriptor = protoConstants[0].getDescriptorForType();
      for (int i = 0; i < protoConstants.length; i++) {
        toProtoConstants.put(javaConstants[i], protoConstants[i].getValueDescriptor());
        fromProtoConstants.put(protoConstants[i].getValueDescriptor(), javaConstants[i]);
        if (!javaConstants[i].toString().equals(protoConstants[i].toString()))
          throw new IllegalStateException(
              String.format(
                  "%s: %d: %s != %s",
                  enumClass.getName(),
                  i,
                  javaConstants[i].toString(),
                  protoConstants[i].toString()));
      }
    }
  }

  private static class JavaToProto {

    Class javaClass;

    Descriptor protoDesc;
    Class<? extends Message> protoClass;

    Message defaultInstance;

    Map<Integer, Field> javaFieldByTag;

    JavaToProto(Class klass) {
      try {
        javaClass = klass;
        String protoName = "com.google.gerrit.proto.Rest$" + klass.getSimpleName();
        protoClass = (Class<? extends Message>) Class.forName(protoName);
        Method toBuilderMethod = protoClass.getMethod("getDefaultInstance");
        defaultInstance = (Message) toBuilderMethod.invoke(null);
      } catch (IllegalAccessException
          | ClassNotFoundException
          | NoSuchMethodException
          | InvocationTargetException e) {
        throw new IllegalStateException("boom", e);
      }

      protoDesc = defaultInstance.getDescriptorForType();
      javaFieldByTag = new HashMap<>();
      Descriptor messageDesc = defaultInstance.getDescriptorForType();

      for (Field jField : javaClass.getFields()) {
        if (Modifier.isStatic(jField.getModifiers())) continue;

        ProtoField protoField = jField.getAnnotation(ProtoField.class);
        if (protoField == null) {
          System.err.println(
              "@ProtoField missing for "
                  + javaClass.getSimpleName()
                  + "."
                  + jField.getName()
                  + " ");
          continue;
        }
        if (protoField.protoTag() <= 0) {
          throw new IllegalStateException("invalid tag");
        }

        FieldDescriptor pField = messageDesc.findFieldByNumber(protoField.protoTag());
        if (pField == null) {
          throw new IllegalStateException(
              String.format(
                  "tag %d is not known in the proto def %s",
                  protoField.protoTag(), javaClass.getSimpleName()));
        }

        checkState((pField.getType() == FieldDescriptor.Type.ENUM) == jField.getType().isEnum());

        String fieldName = toProtoName(jField.getName());
        if (!pField.getName().equals(fieldName)) {
          throw new IllegalStateException(
              String.format(
                  "names do not match: proto '%s' <-> java '%s'", pField.getName(), fieldName));
        }

        if (javaFieldByTag.containsKey(protoField.protoTag())) {
          throw new IllegalStateException(
              String.format("type %s has duplicate protoTag %d", klass, protoField.protoTag()));
        }
        javaFieldByTag.put(protoField.protoTag(), jField);
      }

      if (CustomPojo.class.isAssignableFrom(klass)) {
        return;
      }

      for (FieldDescriptor pField : protoDesc.getFields()) {
        if (!javaFieldByTag.containsKey(pField.getNumber())) {
          throw new IllegalStateException(
              String.format(
                  "type %s does not declare @ProtoField for tag %d (field %s)",
                  klass, pField.getNumber(), pField.getName()));
        }
      }
    }

    protected Object toProtoValue(FieldDescriptor protoDesc, Object src) {
      if (src instanceof java.sql.Timestamp) {
        Instant ts = ((java.sql.Timestamp) src).toInstant();
        return Timestamp.newBuilder()
            .setSeconds(ts.getEpochSecond())
            .setNanos(ts.getNano())
            .build();
      }
      checkState(!protoDesc.isMapField());
      if (protoDesc.getType() == FieldDescriptor.Type.MESSAGE) {
        return RestProtoConverter.toProtoMessage(src);
      }
      if (protoDesc.getType() == FieldDescriptor.Type.ENUM) {
        return RestProtoConverter.toProtoEnum(src);
      }

      return src;
    }

    Message toProtoMessage(Object restObj) {
      checkState(restObj.getClass() == javaClass);
      if (restObj instanceof CustomPojo) {
        return ((CustomPojo) restObj).toProto();
      }

      Message.Builder b = defaultInstance.newBuilderForType();

      for (Map.Entry<Integer, Field> e : javaFieldByTag.entrySet()) {
        Field jField = e.getValue();
        FieldDescriptor pField =
            defaultInstance.getDescriptorForType().findFieldByNumber(e.getKey());

        try {
          Object jValue = jField.get(restObj);
          if (jValue == null) continue;

          if (pField.isMapField()) {
            checkState(pField.isMapField());
            for (Map.Entry<Object, Object> me : ((Map<Object, Object>) jValue).entrySet()) {
              Message.Builder entryBuilder = b.newBuilderForField(pField);
              FieldDescriptor keyDesc = entryBuilder.getDescriptorForType().findFieldByNumber(1);
              FieldDescriptor valueDesc = entryBuilder.getDescriptorForType().findFieldByNumber(2);

              b.addRepeatedField(
                  pField,
                  entryBuilder
                      .setField(keyDesc, toProtoValue(keyDesc, me.getKey()))
                      .setField(valueDesc, toProtoValue(valueDesc, me.getValue()))
                      .build());
            }
            continue;
          } else if (pField.isRepeated()) {
            for (Object elt : (List<?>) jValue) {
              b.addRepeatedField(pField, toProtoValue(pField, elt));
            }
            continue;
          }

          Object pValue = toProtoValue(pField, jValue);
          b.setField(pField, pValue);
        } catch (IllegalAccessException exc) {
          throw new IllegalStateException("should be public", exc);
        }
      }

      return b.build();
    }

    Object newJavaObject() {
      Class<?> klass = getJavaClass(protoDesc.getName());
      try {
        return klass.getDeclaredConstructor().newInstance();
      } catch (NoSuchMethodException
          | InstantiationException
          | IllegalAccessException
          | InvocationTargetException e) {
        throw new RuntimeException("boom", e);
      }
    }

    Object fromProtoValue(Object obj) {
      if (obj instanceof Timestamp) {
        Timestamp ts = (Timestamp) obj;
        return java.sql.Timestamp.from(Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()));
      }
      if (obj instanceof EnumValueDescriptor) {
        return RestProtoConverter.fromProtoEnum((EnumValueDescriptor) obj);
      }
      if (obj instanceof Message) {
        return RestProtoConverter.fromProtoMessage((Message) obj);
      }
      return obj;
    }

    Object fromProtoMessage(Message msg) {
      checkState(msg.getDescriptorForType() == protoDesc);

      Object j = newJavaObject();
      if (j instanceof CustomPojo) {
        ((CustomPojo) j).fromProto(msg);
        return j;
      }
      for (Map.Entry<Integer, Field> e : javaFieldByTag.entrySet()) {
        int tag = e.getKey();
        FieldDescriptor pField = protoDesc.findFieldByNumber(tag);

        if (!pField.isRepeated() && !pField.isMapField() && !msg.hasField(pField)) {
          continue;
        }

        Field jField = e.getValue();

        Object jValue;
        if (pField.isMapField()) {
          Map<Object, Object> jMap = new LinkedHashMap<>();
          for (int i = 0; i < msg.getRepeatedFieldCount(pField); i++) {
            MapEntry<?, ?> entry = (MapEntry<?, ?>) msg.getRepeatedField(pField, i);
            Object k = entry.getKey();
            Object v = entry.getValue();
            jMap.put(fromProtoValue(k), fromProtoValue(v));
          }
          jValue = jMap;
        } else if (pField.isRepeated()) {
          ArrayList<Object> lst = new ArrayList<>();
          for (int i = 0; i < msg.getRepeatedFieldCount(pField); i++) {
            lst.add(fromProtoValue(msg.getRepeatedField(pField, i)));
          }
          jValue = lst;
        } else {
          jValue = fromProtoValue(msg.getField(pField));
        }
        try {
          jField.set(j, jValue);
        } catch (IllegalAccessException exc) {
          throw new IllegalStateException(jField.toString(), exc);
        }
      }
      return j;
    }
  }

  public static String toProtoName(String name) {
    while (name.startsWith("_")) name = name.substring(1);
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
  }

  public static Message toProtoMessage(Object restObj) {
    JavaToProto j2p = INSTANCE.getByJavaType(restObj.getClass());
    return j2p.toProtoMessage(restObj);
  }

  public static Object fromProtoMessage(Message msg) {
    JavaToProto j2p = INSTANCE.getByProtoType(msg);
    return j2p.fromProtoMessage(msg);
  }

  public static EnumValueDescriptor toProtoEnum(Object src) {
    EnumDef d = INSTANCE.enumByJavaType(src.getClass());
    return d.toProtoConstants.get(src);
  }

  public static Object fromProtoEnum(EnumValueDescriptor evd) {
    EnumDef d = INSTANCE.enumByProtoType(evd.getType());
    return d.fromProtoConstants.get(evd);
  }

  public static void addAllTypes() throws IOException {
    int infoCount = 0;
    int inputCount = 0;
    for (ClassInfo classInfo : ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses()) {
      if (!API_PACKAGES.contains(classInfo.getPackageName())) continue;
      if (!classInfo.getName().endsWith("Input") && !classInfo.getName().endsWith("Info")) continue;

      Class klass = classInfo.load();
      if (Modifier.isAbstract(klass.getModifiers())) continue;

      if (classInfo.getName().endsWith("Input")) inputCount++;
      if (classInfo.getName().endsWith("Info")) infoCount++;

      System.out.println(klass.getName());
      Object instance = null;
      try {
        instance = klass.getDeclaredConstructor().newInstance();
      } catch (NoSuchMethodException
          | InstantiationException
          | IllegalAccessException
          | InvocationTargetException e) {
        System.err.println(
            String.format("Class %s cannot be instantiated: %s", klass.getName(), e));

        continue;
      }

      try {
        toProtoMessage(instance);
      } catch (RuntimeException e) {
        //  System.err.println("failed: " + klass.getName());
      }
    }

    System.err.println(String.format("total: %d info, %d input", infoCount, inputCount));
  }
}
