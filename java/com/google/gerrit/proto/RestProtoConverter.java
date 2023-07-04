package com.google.gerrit.proto;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class RestProtoConverter {

  public static String toProtoName(String name) {
    while (name.startsWith("_")) name = name.substring(1);

    String snake = "";
    for (int i = 0; i < name.length(); i++) {
      Character c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        snake += "_";
        c = Character.toLowerCase(c);
      }
      snake += c;
    }
    return snake;
  }

  public static Message toProtoMessage(Object restObj) {
    Class restClass = restObj.getClass();
    Message.Builder b = builderFor(restClass);
    Descriptor messageDesc = b.getDescriptorForType();

    for (Field jField : restClass.getFields()) {
      FieldDoc fieldDoc = jField.getAnnotation(FieldDoc.class);
      if (fieldDoc == null) {
        System.err.println(
            "field doc missing for " + restClass.getSimpleName() + "." + jField.getName() + " ");
        continue;
      }
      if (fieldDoc.protoTag() == 0) {
        System.err.println("no tag");
        continue;
      }
      FieldDescriptor pField = messageDesc.findFieldByNumber(fieldDoc.protoTag());
      if (pField == null) {
        throw new IllegalStateException(
            String.format(
                "tag %d is not known in the proto def %s",
                fieldDoc.protoTag(), restClass.getSimpleName()));
      }
      String fieldName = toProtoName(jField.getName());
      if (!pField.getName().equals(fieldName)) {
        throw new IllegalStateException(
            String.format(
                "names do not match: proto '%s' <-> java '%s'", pField.getName(), fieldName));
      }

      try {
        Object jValue = jField.get(restObj);
        if (jValue == null) continue;

        if (jValue instanceof List) {
          for (Object elt : (List<?>) jValue) {
            b.addRepeatedField(pField, convertValue(pField, elt));
          }
          continue;
        }

        b.setField(pField, convertValue(pField, jValue));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("should be public", e);
      }
    }

    return b.build();
  }

  protected static Object convertValue(FieldDescriptor protoDesc, Object src) {
    if (protoDesc.getType() == FieldDescriptor.Type.MESSAGE) {
      return toProtoMessage(src);
    }
    if (protoDesc.getType() == FieldDescriptor.Type.ENUM) {
      if (!src.getClass().isEnum()) {
        throw new IllegalStateException(
            String.format("field %s: %s is not an enum constant.", protoDesc.getName(), src));
      }

      Object[] constants = src.getClass().getEnumConstants();
      for (int i = 0; i < constants.length; i++) {
        if (protoDesc.getEnumType().findValueByName(constants[i].toString()) == null) {
          throw new IllegalStateException("missing proto enum value for " + constants[i]);
        }
      }

      /* TODO This will mess up if the enum overrides toString() */
      return protoDesc.getEnumType().findValueByName(src.toString());
    }

    return src;
  }

  protected static Message.Builder builderFor(Class clazz) {
    String protoName = "com.google.gerrit.proto.Rest$" + clazz.getSimpleName();
    try {
      Class<? extends Message> msgClass = (Class<? extends Message>) Class.forName(protoName);
      Method toBuilderMethod = msgClass.getMethod("getDefaultInstance");
      Message msg = (Message) toBuilderMethod.invoke(null);
      return msg.toBuilder();
    } catch (IllegalAccessException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new IllegalStateException("boom", e);
    }
  }
}
