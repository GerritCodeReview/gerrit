package com.google.gerrit.entities.converter;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.common.testing.ArbitraryInstances;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.gerrit.common.Nullable;
import com.google.protobuf.MessageLite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SafeProtoConverterTest {
  @Parameter() public SafeProtoConverter<MessageLite, ConvertibleToProto> converter;

  @Parameters(name = "{0}")
  public static ImmutableList<SafeProtoConverter<MessageLite, ConvertibleToProto>> data()
      throws Exception {
    return listSafeConverters();
  }

  /**
   * If this test fails, it's likely that you added a field to a Java class that has a {@link
   * SafeProtoConverter} set, or that you have changed the default value for such a field. Please
   * update the corresponding proto accordingly.
   */
  @Test
  public void javaDefaultsKeptOnDoubleConversion() throws Exception {
    Object orig;
    try {
      orig = buildObjectWithFullFieldsOrThrow(converter.getEntityClass());
    } catch (Exception e) {
      throw new IllegalStateException(
          String.format(
              "Failed to build object for type %s, this likely means the buildObjectWithFullFieldsOrThrow should be adapted.",
              converter.getEntityClass().getName()),
          e);
    }
    Object res = converter.fromProto(converter.toProto(converter.getEntityClass().cast(orig)));
    assertThat(orig).isEqualTo(res);
    // If this assertion fails, it's likely that you forgot to update the `equals` method to include
    // your new field.
    assertThat(EqualsBuilder.reflectionEquals(orig, res)).isTrue();
  }

  /**
   * If this test fails, it's likely that you added a field to a proto that has a {@link
   * SafeProtoConverter} set, or that you have changed the default value for such a field. Please
   * update the corresponding Java class accordingly.
   */
  @Test
  public void protoDefaultsKeptOnDoubleConversion() {
    // TODO(b/335372403) - implement
  }

  private static ImmutableList<SafeProtoConverter<MessageLite, ConvertibleToProto>>
      listSafeConverters() throws Exception {
    return ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses().stream()
        .filter(type -> type.getPackageName().contains("gerrit"))
        .map(ClassInfo::load)
        .filter(SafeProtoConverter.class::isAssignableFrom)
        .filter(clz -> !SafeProtoConverter.class.equals(clz))
        .filter(Class::isEnum)
        .map(clz -> (SafeProtoConverter<MessageLite, ConvertibleToProto>) clz.getEnumConstants()[0])
        .collect(toImmutableList());
  }

  @Nullable
  private static Object buildObjectWithFullFieldsOrThrow(Class<?> clz) throws Exception {
    if (clz == null) {
      return null;
    }
    Object obj = construct(clz);
    if (isSimple(clz)) {
      return obj;
    }
    for (Field field : clz.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Class<?> parameterizedType = getParameterizedType(field);
      if (!field.getType().isArray()
          && !Map.class.isAssignableFrom(field.getType())
          && !Collection.class.isAssignableFrom(field.getType())) {
        if (!field.trySetAccessible()) {
          return null;
        }
        field.set(obj, buildObjectWithFullFieldsOrThrow(field.getType()));
      } else if (Collection.class.isAssignableFrom(field.getType()) && parameterizedType != null) {
        field.set(obj, ImmutableList.of(buildObjectWithFullFieldsOrThrow(parameterizedType)));
      }
    }
    return obj;
  }

  static boolean isAutoValueClass(Class<?> clz) {
    return toRepresentingAutoValueClass(clz).isPresent();
  }

  /**
   * AutoValue annotations are not retained on runtime. We can only find out if a class is an
   * AutoValue, by trying to load the expected AutoValue class.
   *
   * <p>For the class {@code package.Clz}, the AutoValue class name is {@code
   * package.AutoValue_Clz}, for {@code package.Enclosing$Clz}, it is {@code
   * package.AutoValue_Enclosing_Clz}
   */
  static Optional<Class<?>> toRepresentingAutoValueClass(Class<?> clz) {
    String origClzName = clz.getName();
    if (!origClzName.contains("gerrit")) {
      return Optional.empty();
    }
    String autoValueClzName =
        origClzName.substring(0, origClzName.lastIndexOf("."))
            + ".AutoValue_"
            + origClzName.substring(origClzName.lastIndexOf(".") + 1);
    autoValueClzName = autoValueClzName.replace('$', '_');
    try {
      return Optional.of(clz.getClassLoader().loadClass(autoValueClzName));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @Nullable
  private static Class<?> getParameterizedType(Field field) {
    if (!Collection.class.isAssignableFrom(field.getType())) {
      return null;
    }
    Type genericType = field.getGenericType();
    if (genericType instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
    }
    return null;
  }

  @Nullable
  static Object construct(Class<?> clz) {
    try {
      if (clz == null) {
        return null;
      }
      Object arbitrary = ArbitraryInstances.get(clz);
      if (arbitrary != null) {
        return arbitrary;
      }
      if (isAutoValueClass(clz)) {
        return construct(toRepresentingAutoValueClass(clz).get());
      }
      Constructor<?> constructor =
          Arrays.stream(clz.getDeclaredConstructors())
              // Filter out copy constructors
              .filter(
                  c ->
                      c.getParameterCount() != 1 || !c.getParameterTypes()[0].isAssignableFrom(clz))
              // Filter out private constructors which cannot be set accessible.
              .filter(c -> c.canAccess(null) || c.trySetAccessible())
              .min(Comparator.comparingInt(Constructor::getParameterCount))
              .get();
      List<Object> args = new ArrayList<>();
      for (Class<?> f : constructor.getParameterTypes()) {
        args.add(construct(f));
      }
      return constructor.newInstance(args.toArray());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to construct class " + clz.getName(), e);
    }
  }

  static boolean isSimple(Class<?> c) {
    return c.isPrimitive()
        || c.isEnum()
        || Primitives.isWrapperType(c)
        || String.class.isAssignableFrom(c)
        || Timestamp.class.isAssignableFrom(c);
  }
}
