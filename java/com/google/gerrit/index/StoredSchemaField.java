// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Definition of a field stored in the secondary index.
 *
 * <p>Each StoredSchemaField, stored in index, may have multiple {@link StoredSearchSpec} which
 * defines how it can be searched and how the index tokens are generated.
 *
 * <p>Index implementations may choose to store StoredSchemaField and {@link StoredSearchSpec}
 * (search tokens) separately, however {@link com.google.gerrit.index.query.IndexedQuery} always
 * issues the queries to {@link StoredSearchSpec}.
 *
 * <p>This allows index implementations to store StoredSchemaField once, while enabling multiple
 * tokenization strategies on the same StoredSchemaField with {@link StoredSearchSpec}
 *
 * @param <I> input type from which documents are created and search results are returned.
 * @param <T> type that should be extracted from the input object when converting to an index
 *     document.
 */
@AutoValue
public abstract class StoredSchemaField<I, T> {

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

  // Should not be used directly, only used to check if the proto is stored
  private static TypeToken<MessageLite> MESSAGE_TYPE = new TypeToken<MessageLite>() {};

  public static <I, T> Builder<I, T> builder(String name, TypeToken<T> fieldType) {
    return new AutoValue_StoredSchemaField.Builder<I, T>()
        .name(name)
        .fieldType(fieldType)
        .stored(false)
        .required(false);
  }

  public static <I> Builder<I, Iterable<String>> iterableStringBuilder(String name) {
    return builder(name, StoredSchemaField.ITERABLE_STRING_TYPE);
  }

  public static <I> Builder<I, String> stringBuilder(String name) {
    return builder(name, StoredSchemaField.STRING_TYPE);
  }

  public static <I> Builder<I, Integer> integerBuilder(String name) {
    return builder(name, StoredSchemaField.INTEGER_TYPE);
  }

  public static <I> Builder<I, Timestamp> timestampBuilder(String name) {
    return builder(name, StoredSchemaField.TIMESTAMP_TYPE);
  }

  public static <I> Builder<I, Iterable<byte[]>> iterableByteArrayBuilder(String name) {
    return builder(name, StoredSchemaField.ITERABLE_BYTE_ARRAY_TYPE);
  }

  /**
   * Defines how {@link StoredSchemaField} can be searched and how the index tokens are generated.
   *
   * <p>Multiple {@link StoredSearchSpec} can be defined on single {@link StoredSchemaField}.
   *
   * <p>Depending on the implementation, indexes can choose to store {@link StoredSchemaField} and
   * {@link StoredSearchSpec} separately. The searches are issues to {@link StoredSearchSpec}.
   */
  public class StoredSearchSpec implements SchemaField<I, T> {
    private final String name;
    private final SearchOptions searchOptions;

    public StoredSearchSpec(String name, SearchOptions searchOptions) {
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
      } else if ((fieldType.equals(StoredSchemaField.INTEGER_TYPE)
              || fieldType.equals(StoredSchemaField.ITERABLE_INTEGER_TYPE))
          && searchOptions.equals(SearchOptions.EXACT)) {
        return FieldType.INTEGER;
      } else if (fieldType.equals(StoredSchemaField.INTEGER_TYPE)
          && searchOptions.equals(SearchOptions.RANGE)) {
        return FieldType.INTEGER_RANGE;
      } else if (fieldType.equals(StoredSchemaField.LONG_TYPE)) {
        return FieldType.LONG;
      } else if (fieldType.equals(StoredSchemaField.TIMESTAMP_TYPE)) {
        return FieldType.TIMESTAMP;
      } else if (fieldType.equals(StoredSchemaField.STRING_TYPE)
          || fieldType.equals(StoredSchemaField.ITERABLE_STRING_TYPE)) {
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

    @Override
    public boolean setIfPossible(I object, StoredValue doc) {
      return getField().setIfPossible(object, doc);
    }

    /**
     * Returns {@link SearchOptions} enabled on this field.
     *
     * @return {@link SearchOptions}
     */
    public SearchOptions getSearchOptions() {
      return searchOptions;
    }

    /**
     * Returns {@link StoredSchemaField} on which this spec was created.
     *
     * @return original {@link StoredSchemaField} of this spec.
     */
    public StoredSchemaField<I, T> getField() {
      return StoredSchemaField.this;
    }

    private String checkName(String name) {
      CharMatcher m = CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyz0123456789_");
      checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
      return name;
    }
  }

  /**
   * Adds {@link StoredSearchSpec} to this {@link StoredSchemaField}
   *
   * @param name the name to use for in the search.
   * @param searchOptions the tokenization option, enabled by the new {@link StoredSearchSpec}
   * @return the added {@link StoredSearchSpec}.
   */
  public StoredSearchSpec addSearchSpec(String name, SearchOptions searchOptions) {
    StoredSearchSpec searchSpec = new StoredSearchSpec(name, searchOptions);
    checkArgument(
        !searchSpecs.containsKey(searchSpec.getName()),
        "Can not add search spec %s, because it is already defined on field %s",
        searchSpec.getName(),
        name());
    searchSpecs.put(searchSpec.getName(), searchSpec);
    return searchSpec;
  }

  public StoredSearchSpec exact(String name) {
    return addSearchSpec(name, SearchOptions.EXACT);
  }

  public StoredSearchSpec fullText(String name) {
    return addSearchSpec(name, SearchOptions.FULL_TEXT);
  }

  public StoredSearchSpec range(String name) {
    return addSearchSpec(name, SearchOptions.RANGE);
  }

  public StoredSearchSpec integerRange(String name) {
    checkState(fieldType().equals(INTEGER_TYPE));
    return addSearchSpec(name, SearchOptions.RANGE);
  }

  public StoredSearchSpec integer(String name) {
    checkState(fieldType().equals(INTEGER_TYPE) || fieldType().equals(ITERABLE_INTEGER_TYPE));
    return addSearchSpec(name, SearchOptions.EXACT);
  }

  public StoredSearchSpec prefix(String name) {
    return addSearchSpec(name, SearchOptions.PREFIX);
  }

  public StoredSearchSpec storedOnly(String name) {
    checkState(stored());
    return addSearchSpec(name, SearchOptions.STORE_ONLY);
  }

  public StoredSearchSpec timestamp(String name) {
    checkState(fieldType().equals(TIMESTAMP_TYPE));
    return addSearchSpec(name, SearchOptions.RANGE);
  }

  /** A builder for {@link StoredSchemaField}. */
  @AutoValue.Builder
  public abstract static class Builder<I, T> {

    public abstract StoredSchemaField.Builder<I, T> name(String name);

    public abstract Builder<I, T> required(boolean required);

    public Builder<I, T> required() {
      required(true);
      return this;
    }

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

    abstract StoredSchemaField<I, T> autoBuild(); // not public

    public final StoredSchemaField<I, T> build() {
      boolean isRepeatable = fieldType().isSubtypeOf(Iterable.class);
      repeatable(isRepeatable);
      StoredSchemaField<I, T> field = autoBuild();
      checkName(field.name());
      checkArgument(!field.size().isPresent() || field.size().get() > 0);
      return field;
    }

    public final StoredSchemaField build(Getter<I, T> getter, Setter<I, T> setter) {
      return this.getter(getter).fieldSetter(Optional.of(setter)).build();
    }

    public final StoredSchemaField build(
        Getter<I, T> getter, Setter<I, T> setter, ProtoConverter protoConverter) {
      return this.getter(getter)
          .fieldSetter(Optional.of(setter))
          .protoConverter(Optional.of(protoConverter))
          .build();
    }

    public final StoredSchemaField build(Getter<I, T> getter) {
      return this.getter(getter).fieldSetter(Optional.empty()).build();
    }

    private static String checkName(String name) {
      String allowedCharacters = "abcdefghijklmnopqrstuvwxyz0123456789_";
      CharMatcher m = CharMatcher.anyOf(allowedCharacters + allowedCharacters.toUpperCase());
      checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
      return name;
    }
  }

  private Map<String, StoredSearchSpec> searchSpecs = new HashMap<>();

  /** The name to store this field under. */
  public abstract String name();

  /** True if this field is mandatory. */
  public abstract boolean required();

  /** Allow reading the actual data from the index. */
  public abstract boolean stored();

  /** True if this field is repeatable. */
  public abstract boolean repeatable();

  /**
   * Optional size constrain on the field. The size is not constrained if this property is {@link
   * Optional#empty()}
   */
  public abstract Optional<Integer> size();

  /** See {@link Getter} */
  public abstract Getter<I, T> getter();

  /** See {@link Setter} */
  public abstract Optional<Setter<I, T>> fieldSetter();

  /**
   * The {@link TypeToken} describing the contents of the field. See static constants for the common
   * supported types.
   *
   * @return {@link TypeToken} of this field.
   */
  public abstract TypeToken<T> fieldType();

  /** If the {@link #fieldType()} is proto, the converter to use on byte/proto conversions. */
  public abstract Optional<ProtoConverter> protoConverter();

  /**
   * Returns all {@link StoredSearchSpec}, enabled on this field.
   *
   * <p>Note: weather or not a search is supported by the index depends on {@link Schema} version.
   */
  public ImmutableMap<String, StoredSearchSpec> getSearchSpecs() {
    return ImmutableMap.copyOf(searchSpecs);
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

  /** Returns true if the {@link #fieldType} is a proto message. */
  public boolean isProtoType() {
    if (repeatable()) {
      return false;
    }
    return MESSAGE_TYPE.isSupertypeOf(fieldType());
  }

  /** Returns true if the {@link #fieldType} is a list of proto messages. */
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

  private List<MessageLite> decodeProtos(Iterable<byte[]> raw) {
    return StreamSupport.stream(raw.spliterator(), false)
        .map(bytes -> parseProtoFrom(bytes))
        .collect(toImmutableList());
  }

  private MessageLite parseProtoFrom(byte[] bytes) {
    return Protos.parseUnchecked(protoConverter().get().getParser(), bytes, 0, bytes.length);
  }
}
