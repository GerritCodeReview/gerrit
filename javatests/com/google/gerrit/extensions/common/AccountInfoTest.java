package com.google.gerrit.extensions.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Random;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AccountInfoTest {
  Random r = new Random();
  RandomStringGenerator rs = new RandomStringGenerator.Builder().build();

  /** Very minimal random object generator that is only meant to work with AccountInfo */
  <T> void generateObject(T original, Class<T> cl) throws Exception {
    if (!cl.getName().startsWith("com.google.gerrit.extensions.common.")) {
      throw new AssertionError("Can only generate class object for Gerrit value classes");
    }
    Field[] fields = cl.getDeclaredFields();
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      switch (field.getGenericType().getTypeName()) {
        case "java.lang.Integer" -> field.set(original, r.nextInt());
        case "java.lang.String" -> field.set(original, rs.generate(5));
        case "java.util.List<com.google.gerrit.extensions.common.AvatarInfo>" -> {
          AvatarInfo obj1 = new AvatarInfo();
          generateObject(obj1, AvatarInfo.class);
          AvatarInfo obj2 = new AvatarInfo();
          generateObject(obj2, AvatarInfo.class);
          field.set(original, ImmutableList.of(obj1, obj2));
        }
        case "java.util.List<java.lang.String>" ->
            field.set(original, ImmutableList.of(rs.generate(5), rs.generate(5)));
        case "java.lang.Boolean" -> field.set(original, r.nextBoolean());
        default ->
            throw new AssertionError(
                "Unsupported type for random generation" + field.getGenericType().getTypeName());
      }
    }
  }

  @Test
  public void copyTo_createsEqual() throws Exception {
    AccountInfo original = new AccountInfo();
    generateObject(original, AccountInfo.class);
    AccountInfo other = new AccountInfo();
    original.copyTo(other);
    assertThat(original).isEqualTo(other);
  }
}
