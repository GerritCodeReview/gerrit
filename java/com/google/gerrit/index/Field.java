package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.SchemaFieldDefs.Getter;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.SchemaFieldDefs.Setter;
import com.google.gerrit.proto.Protos;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Definition of a field stored in the secondary index.
 *
 * @param <I> input type from which documents are created and search results are returned.
 * @param <T> type that should be extracted from the input object when converting to an index
 *     document.
 */
@AutoValue
public abstract class Field<I, T> {
  public static TypeToken<Integer> INTEGER_TYPE = new TypeToken<Integer>() {};
  public static TypeToken<Iterable<Integer>> ITERABLE_INTEGER_TYPE =
      new TypeToken<Iterable<Integer>>() {};
  public static TypeToken<Long> LONG_TYPE = new TypeToken<Long>() {};
  public static TypeToken<Iterable<Long>> ITERABLE_LONG_TYPE = new TypeToken<Iterable<Long>>() {};
  public static TypeToken<String> STRING_TYPE = new TypeToken<String>() {};
  public static TypeToken<Iterable<String>> ITERABLE_STRING_TYPE =
      new TypeToken<Iterable<String>>() {};
  public static TypeToken<byte[]> BYTE_ARRAY_TYPE = new TypeToken<byte[]>() {};
  public static TypeToken<Iterable<byte[]>> ITERABLE_BYTE_ARRAY_TYPE =
      new TypeToken<Iterable<byte[]>>() {};
  public static TypeToken<Timestamp> TIMESTAMP_TYPE = new TypeToken<Timestamp>() {};

  // Should not be used directly
  private static TypeToken<MessageLite> MESSAGE_TYPE = new TypeToken<MessageLite>() {};

  public boolean isProtoType() {
    if (repeatable()) {
      return false;
    }
    return MESSAGE_TYPE.isSupertypeOf(fieldType());
  }

  public boolean isProtoIterableType() {
    if (!repeatable()) {
      return false;
    }
    if (!(fieldType() instanceof ParameterizedType)) {
      return false;
    }
    ParameterizedType parameterizedType = (ParameterizedType) fieldType();
    if (parameterizedType.getActualTypeArguments().length != 1) {
      return false;
    }
    Type type = parameterizedType.getActualTypeArguments()[0];
    return MESSAGE_TYPE.isSupertypeOf(type);
  }

  public static <I, T> Builder<I, T> builder(String name, TypeToken<T> fieldType) {
    return new AutoValue_Field.Builder<I, T>().name(name).fieldType(fieldType).stored(false);
  }

  public static <I> Builder<I, Iterable<String>> iterableStringBuilder(String name) {
    return builder(name, Field.ITERABLE_STRING_TYPE);
  }

  public static <I> Builder<I, String> stringBuilder(String name) {
    return builder(name, Field.STRING_TYPE);
  }

  public static <I> Builder<I, Integer> integerBuilder(String name) {
    return builder(name, Field.INTEGER_TYPE);
  }

  public static <I> Builder<I, Timestamp> timestampBuilder(String name) {
    return builder(name, Field.TIMESTAMP_TYPE);
  }

  public static <I> Builder<I, Iterable<byte[]>> iterableByteArrayBuilder(String name) {
    return builder(name, Field.ITERABLE_BYTE_ARRAY_TYPE);
  }

  public class FieldSpec implements SchemaField<I, T> {
    private final String name;
    private final SearchOptions searchOptions;

    public FieldSpec(String name, SearchOptions searchOptions) {
      checkName(name);
      this.name = name;
      this.searchOptions = searchOptions;
    }

    @Override
    public boolean isStored() {
      return getField().stored();
    }

    @Override
    public boolean isRepeatable() {
      return getField().repeatable();
    }

    @Override
    public T get(I obj) {
      return (T) getField().get(obj);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public FieldType<?> getType() {
      SearchOptions searchOptions = getSearchOptions();
      TypeToken<?> fieldType = getField().fieldType();
      if (searchOptions.equals(SearchOptions.STORE_ONLY)) {
        return FieldType.STORED_ONLY;
      } else if ((fieldType.equals(Field.INTEGER_TYPE)
              || fieldType.equals(Field.ITERABLE_INTEGER_TYPE))
          && searchOptions.equals(SearchOptions.EXACT)) {
        return FieldType.INTEGER;
      } else if (fieldType.equals(Field.INTEGER_TYPE)
          && searchOptions.equals(SearchOptions.RANGE)) {
        return FieldType.INTEGER_RANGE;
      } else if (fieldType.equals(Field.LONG_TYPE)) {
        return FieldType.LONG;
      } else if (fieldType.equals(Field.TIMESTAMP_TYPE)) {
        return FieldType.TIMESTAMP;
      } else if (fieldType.equals(Field.STRING_TYPE)
          || fieldType.equals(Field.ITERABLE_STRING_TYPE)) {
        if (searchOptions.equals(SearchOptions.EXACT)) {
          return FieldType.EXACT;
        } else if (searchOptions.equals(SearchOptions.FULL_TEXT)) {
          return FieldType.FULL_TEXT;
        } else if (searchOptions.equals(SearchOptions.PREFIX)) {
          return FieldType.PREFIX;
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "search spec [%s, %s] is not supported on field [%s, %s]",
              getName(), getSearchOptions(), getField().name(), getField().fieldType()));
    }

    public SearchOptions getSearchOptions() {
      return searchOptions;
    }

    public Field<I, T> getField() {
      return Field.this;
    }

    @Override
    public boolean setIfPossible(I object, StoredValue doc) {
      return getField().setIfPossible(object, doc);
    }

    private String checkName(String name) {
      CharMatcher m = CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyz0123456789_");
      checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
      return name;
    }
  }

  public FieldSpec addFieldSpec(String name, SearchOptions searchOptions) {
    FieldSpec fieldSpec = new FieldSpec(name, searchOptions);
    fieldSpecs.add(fieldSpec);
    return fieldSpec;
  }

  public FieldSpec exact(String name) {
    return addFieldSpec(name, SearchOptions.EXACT);
  }

  public FieldSpec fullText(String name) {
    return addFieldSpec(name, SearchOptions.FULL_TEXT);
  }

  public FieldSpec range(String name) {
    return addFieldSpec(name, SearchOptions.RANGE);
  }

  public FieldSpec integerRange(String name) {
    checkState(fieldType().equals(INTEGER_TYPE));
    return addFieldSpec(name, SearchOptions.RANGE);
  }

  public FieldSpec integer(String name) {
    checkState(fieldType().equals(INTEGER_TYPE) || fieldType().equals(ITERABLE_INTEGER_TYPE));
    return addFieldSpec(name, SearchOptions.EXACT);
  }

  public FieldSpec prefix(String name) {
    return addFieldSpec(name, SearchOptions.PREFIX);
  }

  public FieldSpec storedOnly(String name) {
    checkState(stored());
    return addFieldSpec(name, SearchOptions.STORE_ONLY);
  }

  public FieldSpec timestamp(String name) {
    checkState(fieldType().equals(TIMESTAMP_TYPE));
    return addFieldSpec(name, SearchOptions.RANGE);
  }

  @AutoValue.Builder
  public abstract static class Builder<I, T> {

    public abstract Field.Builder<I, T> name(String name);

    /** Allow reading the actual data from the index. */
    public abstract Builder<I, T> stored(boolean stored);

    public Builder<I, T> stored() {
      stored(true);
      return this;
    }

    abstract Builder<I, T> repeatable(boolean repeatable);

    public abstract Builder<I, T> size(Optional<Integer> value);

    public abstract Builder<I, T> size(Integer value);

    public abstract Builder<I, T> getter(Getter<I, T> getter);

    public abstract Builder<I, T> fieldSetter(Optional<Setter<I, T>> setter);

    abstract TypeToken<T> fieldType();

    public abstract Builder<I, T> fieldType(TypeToken<T> type);

    public abstract Builder<I, T> protoConverter(Optional<ProtoConverter> value);

    abstract Field<I, T> autoBuild(); // not public

    public final Field<I, T> build() {
      boolean isRepeatable = fieldType().isSubtypeOf(Iterable.class);
      repeatable(isRepeatable);
      Field<I, T> field = autoBuild();
      checkName(field.name());
      checkArgument(!field.size().isPresent() || (int) field.size().get() > 0);
      // check that this is not int, long timestamp? or just ignore?
      return field;
    }

    public final Field build(Getter<I, T> getter, Setter<I, T> setter) {
      return this.getter(getter).fieldSetter(Optional.of(setter)).build();
    }

    /*public final Field build(Field.Getter<I, T> getter, Field.Setter<I, T> setter, Field.FromBytesSetter<I> fromBytesSetter) {
      return this.getter(getter).fieldSetter(Optional.of(setter)).fieldFromBytesSetter(Optional.of(fromBytesSetter)).build();
    }

     */
    public final Field build(
        Getter<I, T> getter, Setter<I, T> setter, ProtoConverter protoConverter) {
      return this.getter(getter)
          .fieldSetter(Optional.of(setter))
          .protoConverter(Optional.of(protoConverter))
          .build();
    }

    public final Field build(Getter<I, T> getter) {
      return this.getter(getter).fieldSetter(Optional.empty()).build();
    }
  }

  public abstract String name();

  /** Allow reading the actual data from the index. */
  public abstract boolean stored();

  public abstract boolean repeatable();

  public abstract Optional<Integer> size();

  public abstract Getter<I, T> getter();

  public abstract Optional<Setter<I, T>> fieldSetter();

  public abstract TypeToken<T> fieldType();

  public abstract Optional<ProtoConverter> protoConverter();

  public ImmutableList<FieldSpec> getFieldSpecs() {
    return ImmutableList.copyOf(fieldSpecs);
  }

  public void setFieldSpecs(ImmutableList<FieldSpec> fieldSpecs) {
    this.fieldSpecs = fieldSpecs;
  }

  private List<FieldSpec> fieldSpecs = new ArrayList<>();

  private static String checkName(String name) {
    String allowedCharacters = "abcdefghijklmnopqrstuvwxyz0123456789_";
    CharMatcher m = CharMatcher.anyOf(allowedCharacters + allowedCharacters.toUpperCase());
    checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
    return name;
  }

  /**
   * Get the field contents from the input object.
   *
   * @param input input object.
   * @return the field value(s) to index.
   */
  @Nullable
  public T get(I input) {
    try {
      return getter().get(input);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  /**
   * Set the field contents back to an object. Used to reconstruct fields from indexed values. No-op
   * if the field can't be reconstructed.
   *
   * @param object input object.
   * @param doc indexed document
   * @return {@code true} if the field was set, {@code false} otherwise
   */
  @SuppressWarnings("unchecked")
  public boolean setIfPossible(I object, StoredValue doc) {
    if (!fieldSetter().isPresent()) {
      return false;
    }

    if (this.fieldType().equals(STRING_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asString());
      return true;
    } else if (this.fieldType().equals(ITERABLE_STRING_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asStrings());
      return true;
    } else if (this.fieldType().equals(INTEGER_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asInteger());
      return true;
    } else if (this.fieldType().equals(ITERABLE_INTEGER_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asIntegers());
      return true;
    } else if (this.fieldType().equals(LONG_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asLong());
      return true;
    } else if (this.fieldType().equals(ITERABLE_LONG_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asLongs());
      return true;
    } else if (this.fieldType().equals(BYTE_ARRAY_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asByteArray());
      return true;
    } else if (this.fieldType().equals(ITERABLE_BYTE_ARRAY_TYPE)) {
      fieldSetter().get().set(object, (T) doc.asByteArrays());
      return true;
    } else if (this.fieldType().equals(TIMESTAMP_TYPE)) {
      checkState(!repeatable(), "can't repeat timestamp values");
      fieldSetter().get().set(object, (T) doc.asTimestamp());
      return true;
    } else if (isProtoType()) {
      MessageLite proto = doc.asProto();
      if (proto != null) {
        fieldSetter().get().set(object, (T) proto);
        return true;
      }
      byte[] bytes = doc.asByteArray();
      if (bytes != null && protoConverter().isPresent()) {
        fieldSetter().get().set(object, (T) parseProtoFrom(bytes));
        return true;
      }
    } else if (isProtoIterableType()) {
      Iterable<MessageLite> protos = doc.asProtos();
      if (protos != null) {
        fieldSetter().get().set(object, (T) protos);
        return true;
      }
      Iterable<byte[]> bytes = doc.asByteArrays();
      if (bytes != null && protoConverter().isPresent()) {
        fieldSetter().get().set(object, (T) decodeProtos(bytes));
        return true;
      }
    }
    return false;
  }

  private List<MessageLite> decodeProtos(Iterable<byte[]> raw) {
    return StreamSupport.stream(raw.spliterator(), false)
        .map(bytes -> parseProtoFrom(bytes))
        .collect(toImmutableList());
  }

  private MessageLite parseProtoFrom(byte[] bytes) {
    return Protos.parseUnchecked(protoConverter().get().getParser(), bytes, 0, bytes.length);
  }
}
