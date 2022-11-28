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
import com.google.common.collect.ImmutableList;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Definition of a field stored in the secondary index.
 *
 * <p>Each IndexedField, stored in index, may have multiple {@link SearchSpec} which defines how it
 * can be searched and how the index tokens are generated.
 *
 * <p>Index implementations may choose to store IndexedField and {@link SearchSpec} (search tokens)
 * separately, however {@link com.google.gerrit.index.query.IndexedQuery} always issues the queries
 * to {@link SearchSpec}.
 *
 * <p>This allows index implementations to store IndexedField once, while enabling multiple
 * tokenization strategies on the same IndexedField with {@link SearchSpec}
 *
 * @param <I> input type from which documents are created and search results are returned.
 * @param <T> type that should be extracted from the input object when converting to an index
 *     document.
 */
// TODO(mariasavtchouk): revisit the class name after migration is done.
@SuppressWarnings("serial")
@AutoValue
public abstract class IndexedField<I, T> {

  public static final TypeToken<Integer> INTEGER_TYPE = new TypeToken<>() {};
  public static final TypeToken<Iterable<Integer>> ITERABLE_INTEGER_TYPE = new TypeToken<>() {};
  public static final TypeToken<Long> LONG_TYPE = new TypeToken<>() {};
  public static final TypeToken<Iterable<Long>> ITERABLE_LONG_TYPE = new TypeToken<>() {};
  public static final TypeToken<String> STRING_TYPE = new TypeToken<>() {};
  public static final TypeToken<Iterable<String>> ITERABLE_STRING_TYPE = new TypeToken<>() {};
  public static final TypeToken<byte[]> BYTE_ARRAY_TYPE = new TypeToken<>() {};
  public static final TypeToken<Iterable<byte[]>> ITERABLE_BYTE_ARRAY_TYPE = new TypeToken<>() {};
  public static final TypeToken<Timestamp> TIMESTAMP_TYPE = new TypeToken<>() {};

  // Should not be used directly, only used to check if the proto is stored
  private static final TypeToken<MessageLite> MESSAGE_TYPE = new TypeToken<>() {};

  public static <I, T> Builder<I, T> builder(String name, TypeToken<T> fieldType) {
    return new AutoValue_IndexedField.Builder<I, T>()
        .name(name)
        .fieldType(fieldType)
        .required(false);
  }

  public static <I> Builder<I, Iterable<String>> iterableStringBuilder(String name) {
    return builder(name, IndexedField.ITERABLE_STRING_TYPE);
  }

  public static <I> Builder<I, String> stringBuilder(String name) {
    return builder(name, IndexedField.STRING_TYPE);
  }

  public static <I> Builder<I, Integer> integerBuilder(String name) {
    return builder(name, IndexedField.INTEGER_TYPE);
  }

  public static <I> Builder<I, Long> longBuilder(String name) {
    return builder(name, IndexedField.LONG_TYPE);
  }

  public static <I> Builder<I, Iterable<Integer>> iterableIntegerBuilder(String name) {
    return builder(name, IndexedField.ITERABLE_INTEGER_TYPE);
  }

  public static <I> Builder<I, Timestamp> timestampBuilder(String name) {
    return builder(name, IndexedField.TIMESTAMP_TYPE);
  }

  public static <I> Builder<I, byte[]> byteArrayBuilder(String name) {
    return builder(name, IndexedField.BYTE_ARRAY_TYPE);
  }

  public static <I> Builder<I, Iterable<byte[]>> iterableByteArrayBuilder(String name) {
    return builder(name, IndexedField.ITERABLE_BYTE_ARRAY_TYPE);
  }

  /**
   * Defines how {@link IndexedField} can be searched and how the index tokens are generated.
   *
   * <p>Multiple {@link SearchSpec} can be defined on a single {@link IndexedField}.
   *
   * <p>Depending on the implementation, indexes can choose to store {@link IndexedField} and {@link
   * SearchSpec} separately. The searches are issues to {@link SearchSpec}.
   */
  public class SearchSpec implements SchemaField<I, T> {
    private final String name;
    private final SearchOption searchOption;
    /** Allow reading the actual data from the index. */
    private final boolean stored;

    public SearchSpec(String name, SearchOption searchOption, boolean stored) {
      checkName(name);
      this.name = name;
      this.stored = stored;
      this.searchOption = searchOption;
    }

    public SearchSpec(String name, SearchOption searchOption) {
      this(name, searchOption, /* stored= */ false);
      checkName(name);
    }

    @Override
    public boolean isStored() {
      return stored;
    }

    @Override
    public boolean isRepeatable() {
      return getField().repeatable();
    }

    @Override
    @Nullable
    public T get(I obj) {
      return getField().get(obj);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public FieldType<?> getType() {
      SearchOption searchOption = getSearchOption();
      TypeToken<?> fieldType = getField().fieldType();
      if (searchOption.equals(SearchOption.STORE_ONLY)) {
        return FieldType.STORED_ONLY;
      } else if ((fieldType.equals(IndexedField.INTEGER_TYPE)
              || fieldType.equals(IndexedField.ITERABLE_INTEGER_TYPE))
          && searchOption.equals(SearchOption.EXACT)) {
        return FieldType.INTEGER;
      } else if (fieldType.equals(IndexedField.INTEGER_TYPE)
          && searchOption.equals(SearchOption.RANGE)) {
        return FieldType.INTEGER_RANGE;
      } else if (fieldType.equals(IndexedField.LONG_TYPE)) {
        return FieldType.LONG;
      } else if (fieldType.equals(IndexedField.TIMESTAMP_TYPE)) {
        return FieldType.TIMESTAMP;
      } else if (fieldType.equals(IndexedField.STRING_TYPE)
          || fieldType.equals(IndexedField.ITERABLE_STRING_TYPE)) {
        if (searchOption.equals(SearchOption.EXACT)) {
          return FieldType.EXACT;
        } else if (searchOption.equals(SearchOption.FULL_TEXT)) {
          return FieldType.FULL_TEXT;
        } else if (searchOption.equals(SearchOption.PREFIX)) {
          return FieldType.PREFIX;
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "search spec [%s, %s] is not supported on field [%s, %s]",
              getName(), getSearchOption(), getField().name(), getField().fieldType()));
    }

    @Override
    public boolean setIfPossible(I object, StoredValue doc) {
      return getField().setIfPossible(object, doc);
    }

    /**
     * Returns {@link SearchOption} enabled on this field.
     *
     * @return {@link SearchOption}
     */
    public SearchOption getSearchOption() {
      return searchOption;
    }

    /**
     * Returns {@link IndexedField} on which this spec was created.
     *
     * @return original {@link IndexedField} of this spec.
     */
    public IndexedField<I, T> getField() {
      return IndexedField.this;
    }

    private String checkName(String name) {
      CharMatcher m = CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyz0123456789_");
      checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
      return name;
    }
  }

  /**
   * Adds {@link SearchSpec} to this {@link IndexedField}
   *
   * @param name the name to use for in the search.
   * @param searchOption the tokenization option, enabled by the new {@link SearchSpec}
   * @param stored see {@link SearchSpec#stored}
   * @return the added {@link SearchSpec}.
   */
  private SearchSpec addSearchSpec(String name, SearchOption searchOption, boolean stored) {
    SearchSpec searchSpec = new SearchSpec(name, searchOption, stored);
    checkArgument(
        !searchSpecs.containsKey(searchSpec.getName()),
        "Can not add search spec %s, because it is already defined on field %s",
        searchSpec.getName(),
        name());
    searchSpecs.put(searchSpec.getName(), searchSpec);
    return searchSpec;
  }

  private SearchSpec exact(String name, boolean stored) {
    return addSearchSpec(name, SearchOption.EXACT, stored);
  }

  public SearchSpec exact(String name) {
    return exact(name, /* stored= */ false);
  }

  public SearchSpec storedExact(String name) {
    return exact(name, /* stored= */ true);
  }

  private SearchSpec fullText(String name, boolean stored) {
    return addSearchSpec(name, SearchOption.FULL_TEXT, stored);
  }

  public SearchSpec fullText(String name) {
    return fullText(name, /* stored= */ false);
  }

  public SearchSpec storedFullText(String name) {
    return fullText(name, /* stored= */ true);
  }

  private SearchSpec range(String name, boolean stored) {
    return addSearchSpec(name, SearchOption.RANGE, stored);
  }

  public SearchSpec range(String name) {
    return range(name, /* stored= */ false);
  }

  public SearchSpec storedRange(String name) {
    return range(name, /* stored= */ true);
  }

  private SearchSpec integerRange(String name, boolean stored) {
    checkState(fieldType().equals(INTEGER_TYPE));
    return addSearchSpec(name, SearchOption.RANGE, stored);
  }

  public SearchSpec integerRange(String name) {
    return integerRange(name, /* stored= */ false);
  }

  public SearchSpec storedIntegerRange(String name) {
    return integerRange(name, /* stored= */ true);
  }

  private SearchSpec integer(String name, boolean stored) {
    checkState(fieldType().equals(INTEGER_TYPE) || fieldType().equals(ITERABLE_INTEGER_TYPE));
    return addSearchSpec(name, SearchOption.EXACT, stored);
  }

  public SearchSpec integer(String name) {
    return integer(name, /* stored= */ false);
  }

  public SearchSpec storedInteger(String name) {
    return integer(name, /* stored= */ true);
  }

  private SearchSpec longSearch(String name, boolean stored) {
    checkState(fieldType().equals(LONG_TYPE) || fieldType().equals(ITERABLE_LONG_TYPE));
    return addSearchSpec(name, SearchOption.EXACT, stored);
  }

  public SearchSpec longSearch(String name) {
    return longSearch(name, /* stored= */ false);
  }

  public SearchSpec storedLongSearch(String name) {
    return longSearch(name, /* stored= */ true);
  }

  private SearchSpec prefix(String name, boolean stored) {
    return addSearchSpec(name, SearchOption.PREFIX, stored);
  }

  public SearchSpec prefix(String name) {
    return prefix(name, /* stored= */ false);
  }

  public SearchSpec storedPrefix(String name) {
    return prefix(name, /* stored= */ true);
  }

  public SearchSpec storedOnly(String name) {
    return addSearchSpec(name, SearchOption.STORE_ONLY, /* stored= */ true);
  }

  private SearchSpec timestamp(String name, boolean stored) {
    checkState(fieldType().equals(TIMESTAMP_TYPE));
    return addSearchSpec(name, SearchOption.RANGE, stored);
  }

  public SearchSpec timestamp(String name) {
    return timestamp(name, /* stored= */ false);
  }

  public SearchSpec storedTimestamp(String name) {
    return timestamp(name, /* stored= */ true);
  }

  /** A builder for {@link IndexedField}. */
  @AutoValue.Builder
  public abstract static class Builder<I, T> {

    public abstract IndexedField.Builder<I, T> name(String name);

    public abstract IndexedField.Builder<I, T> description(Optional<String> description);

    public abstract IndexedField.Builder<I, T> description(String description);

    public abstract Builder<I, T> required(boolean required);

    public Builder<I, T> required() {
      required(true);
      return this;
    }

    abstract Builder<I, T> repeatable(boolean repeatable);

    public abstract Builder<I, T> size(Optional<Integer> value);

    public abstract Builder<I, T> size(Integer value);

    public abstract Builder<I, T> getter(Getter<I, T> getter);

    public abstract Builder<I, T> fieldSetter(Optional<Setter<I, T>> setter);

    abstract TypeToken<T> fieldType();

    public abstract Builder<I, T> fieldType(TypeToken<T> type);

    public abstract Builder<I, T> protoConverter(
        Optional<ProtoConverter<? extends MessageLite, ?>> value);

    abstract IndexedField<I, T> autoBuild(); // not public

    public final IndexedField<I, T> build() {
      boolean isRepeatable = fieldType().isSubtypeOf(Iterable.class);
      repeatable(isRepeatable);
      IndexedField<I, T> field = autoBuild();
      checkName(field.name());
      checkArgument(!field.size().isPresent() || field.size().get() > 0);
      return field;
    }

    public final IndexedField<I, T> build(Getter<I, T> getter, Setter<I, T> setter) {
      return this.getter(getter).fieldSetter(Optional.of(setter)).build();
    }

    public final IndexedField<I, T> build(
        Getter<I, T> getter,
        Setter<I, T> setter,
        ProtoConverter<? extends MessageLite, ?> protoConverter) {
      return this.getter(getter)
          .fieldSetter(Optional.of(setter))
          .protoConverter(Optional.of(protoConverter))
          .build();
    }

    public final IndexedField<I, T> build(Getter<I, T> getter) {
      return this.getter(getter).fieldSetter(Optional.empty()).build();
    }

    private static String checkName(String name) {
      String allowedCharacters = "abcdefghijklmnopqrstuvwxyz0123456789_";
      CharMatcher m = CharMatcher.anyOf(allowedCharacters + allowedCharacters.toUpperCase());
      checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
      return name;
    }
  }

  private Map<String, SearchSpec> searchSpecs = new HashMap<>();

  /**
   * The name to store this field under.
   *
   * <p>The name should use the UpperCamelCase format, see {@link Builder#checkName}.
   */
  public abstract String name();

  /** Optional description of the field data. */
  public abstract Optional<String> description();

  /** True if this field is mandatory. Default is false. */
  public abstract boolean required();

  /** True if this field is repeatable. */
  public abstract boolean repeatable();

  /**
   * Optional size constrain on the field. The size is not constrained if this property is {@link
   * Optional#empty()}
   *
   * <p>If the field is {@link #repeatable()}, the constraint applies to each element separately.
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
  public abstract Optional<ProtoConverter<? extends MessageLite, ?>> protoConverter();

  /**
   * Returns all {@link SearchSpec}, enabled on this field.
   *
   * <p>Note: weather or not a search is supported by the index depends on {@link Schema} version.
   */
  public ImmutableMap<String, SearchSpec> getSearchSpecs() {
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
    if (!(fieldType().getType() instanceof ParameterizedType)) {
      return false;
    }
    ParameterizedType parameterizedType = (ParameterizedType) fieldType().getType();
    if (parameterizedType.getActualTypeArguments().length != 1) {
      return false;
    }
    Type type = parameterizedType.getActualTypeArguments()[0];
    return MESSAGE_TYPE.isSupertypeOf(type);
  }

  private ImmutableList<MessageLite> decodeProtos(Iterable<byte[]> raw) {
    return StreamSupport.stream(raw.spliterator(), false)
        .map(bytes -> parseProtoFrom(bytes))
        .collect(toImmutableList());
  }

  private MessageLite parseProtoFrom(byte[] bytes) {
    return Protos.parseUnchecked(protoConverter().get().getParser(), bytes);
  }
}
