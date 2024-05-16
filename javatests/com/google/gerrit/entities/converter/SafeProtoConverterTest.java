package com.google.gerrit.entities.converter;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.common.testing.ArbitraryInstances;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.gerrit.common.Nullable;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
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
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  SafeProtoConverterTest.ListSafeProtoConverterTest.class, //
  SafeProtoConverterTest.PerTypeSafeProtoConverterTest.class, //
})
public class SafeProtoConverterTest {
  public static class ListSafeProtoConverterTest {
    @Test
    public void areAllConvertersEnums() throws Exception {
      Stream<? extends Class<?>> safeConverters =
          ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses().stream()
              .filter(type -> type.getPackageName().contains("gerrit"))
              .map(ClassInfo::load)
              .filter(SafeProtoConverter.class::isAssignableFrom)
              .filter(clz -> !SafeProtoConverter.class.equals(clz));
      // Safe converters must be enums. See also `isConverterAValidEnum` test below.
      assertThat(safeConverters.allMatch(Class::isEnum)).isTrue();
    }
  }

  @RunWith(Parameterized.class)
  public static class PerTypeSafeProtoConverterTest {
    @Parameter(0)
    public SafeProtoConverter<Message, Object> converter;

    @Parameter(1)
    public String converterName;

    @Parameters(name = "PerTypeSafeProtoConverterTest${1}")
    public static ImmutableList<Object[]> listSafeConverters() throws Exception {
      return ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses().stream()
          .filter(type -> type.getPackageName().contains("gerrit"))
          .map(ClassInfo::load)
          .filter(SafeProtoConverter.class::isAssignableFrom)
          .filter(clz -> !SafeProtoConverter.class.equals(clz))
          .filter(Class::isEnum)
          .map(clz -> (SafeProtoConverter<Message, Object>) clz.getEnumConstants()[0])
          .map(clz -> new Object[] {clz, clz.getClass().getSimpleName()})
          .collect(toImmutableList());
    }

    /**
     * For rising visibility, all Java Entity classes which have a {@link SafeProtoConverter}, must
     * be annotated with {@link ConvertibleToProto}.
     */
    @Test
    public void isJavaClassMarkedAsConvertibleToProto() {
      assertThat(converter.getEntityClass().getDeclaredAnnotation(ConvertibleToProto.class))
          .isNotNull();
    }

    /**
     * All {@link SafeProtoConverter} implementations must be enums with a single instance. Please
     * prefer descriptive enum and instance names, such as {@code
     * MyTypeConverter::MY_TYPE_CONVERTER}.
     */
    @Test
    public void isConverterAValidEnum() {
      assertThat(converter.getClass().isEnum()).isTrue();
      assertThat(converter.getClass().getEnumConstants().length).isEqualTo(1);
    }

    /**
     * If this test fails, it's likely that you added a field to a Java class that has a {@link
     * SafeProtoConverter} set, or that you have changed the default value for such a field. Please
     * update the corresponding proto accordingly.
     */
    @Test
    public void javaDefaultsKeptOnDoubleConversion() {
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
      // If this assertion fails, it's likely that you forgot to update the `equals` method to
      // include your new field.
      assertThat(EqualsBuilder.reflectionEquals(orig, res)).isTrue();
    }

    /**
     * If this test fails, it's likely that you added a field to a proto that has a {@link
     * SafeProtoConverter} set, or that you have changed the default value for such a field. Please
     * update the corresponding Java class accordingly.
     */
    @Test
    public void protoDefaultsKeptOnDoubleConversion() {
      Message defaultInstance = getProtoDefaultInstance(converter.getProtoClass());
      Message preFilled = explicitlyFillProtoDefaults(defaultInstance);
      Message resFromDefault =
          converter.toProto(converter.fromProto(converter.getProtoClass().cast(preFilled)));
      Message resFromPrefilled =
          converter.toProto(converter.fromProto(converter.getProtoClass().cast(preFilled)));
      assertThat(resFromDefault).isEqualTo(preFilled);
      assertThat(resFromPrefilled).isEqualTo(preFilled);
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
        } else if (Collection.class.isAssignableFrom(field.getType())
            && parameterizedType != null) {
          field.set(obj, ImmutableList.of(buildObjectWithFullFieldsOrThrow(parameterizedType)));
        }
      }
      return obj;
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

    @Nonnull
    static Object construct(@Nonnull Class<?> clz) {
      try {
        Object arbitrary = ArbitraryInstances.get(clz);
        if (arbitrary != null) {
          return arbitrary;
        }
        Optional<Class<?>> optionalAutoValueRepresentation = toRepresentingAutoValueClass(clz);
        if (optionalAutoValueRepresentation.isPresent()) {
          return construct(optionalAutoValueRepresentation.get());
        }
        Constructor<?> constructor =
            Arrays.stream(clz.getDeclaredConstructors())
                // Filter out copy constructors
                .filter(
                    c ->
                        c.getParameterCount() != 1
                            || !c.getParameterTypes()[0].isAssignableFrom(clz))
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

    /**
     * Returns the default instance for the given MessageLite class, if it has the {@code
     * getDefaultInstance} static method.
     *
     * @param type the protobuf message class
     * @throws IllegalArgumentException if the given class doesn't have the static {@code
     *     getDefaultInstance} method
     */
    public static <T extends MessageLite> T getProtoDefaultInstance(Class<T> type) {
      try {
        return type.cast(type.getMethod("getDefaultInstance").invoke(null));
      } catch (ReflectiveOperationException | ClassCastException e) {
        throw new IllegalStateException("Cannot get default instance for " + type, e);
      }
    }

    private static Message explicitlyFillProtoDefaults(Message defaultInstance) {
      Message.Builder res = defaultInstance.toBuilder();
      for (FieldDescriptor f : defaultInstance.getDescriptorForType().getFields()) {
        try {
          if (f.getType().equals(FieldDescriptor.Type.MESSAGE)) {
            if (f.isRepeated()) {
              res.addRepeatedField(
                  f,
                  explicitlyFillProtoDefaults(
                      explicitlyFillProtoDefaults(
                          getProtoDefaultInstance(res.newBuilderForField(f).build().getClass()))));
            } else {
              res.setField(f, explicitlyFillProtoDefaults((Message) defaultInstance.getField(f)));
            }
          } else {
            res.setField(f, defaultInstance.getField(f));
          }
        } catch (Exception e) {
          throw new IllegalStateException("Failed to fill default instance for " + f.getName(), e);
        }
      }
      return res.build();
    }
  }
}
