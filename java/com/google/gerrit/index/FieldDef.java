// Copyright (C) 2013 The Android Open Source Project
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
import static java.util.Objects.requireNonNull;

import com.google.common.base.CharMatcher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Definition of a field stored in the secondary index.
 *
 * @param <I> input type from which documents are created and search results are returned.
 * @param <T> type that should be extracted from the input object when converting to an index
 *     document.
 */
public final class FieldDef<I, T> {
  public static FieldDef.Builder<String> exact(String name) {
    return new FieldDef.Builder<>(FieldType.EXACT, name);
  }

  public static FieldDef.Builder<String> fullText(String name) {
    return new FieldDef.Builder<>(FieldType.FULL_TEXT, name);
  }

  public static FieldDef.Builder<Integer> intRange(String name) {
    return new FieldDef.Builder<>(FieldType.INTEGER_RANGE, name).stored();
  }

  public static FieldDef.Builder<Integer> integer(String name) {
    return new FieldDef.Builder<>(FieldType.INTEGER, name);
  }

  public static FieldDef.Builder<String> prefix(String name) {
    return new FieldDef.Builder<>(FieldType.PREFIX, name);
  }

  public static FieldDef.Builder<byte[]> storedOnly(String name) {
    return new FieldDef.Builder<>(FieldType.STORED_ONLY, name).stored();
  }

  public static FieldDef.Builder<Timestamp> timestamp(String name) {
    return new FieldDef.Builder<>(FieldType.TIMESTAMP, name);
  }

  @FunctionalInterface
  public interface Getter<I, T> {
    @Nullable
    T get(I input) throws IOException;
  }

  @FunctionalInterface
  public interface Setter<I, T> {
    void set(I object, T value);
  }

  public static class Builder<T> {
    private final FieldType<T> type;
    private final String name;
    private boolean stored;

    public Builder(FieldType<T> type, String name) {
      this.type = requireNonNull(type);
      this.name = requireNonNull(name);
    }

    public Builder<T> stored() {
      this.stored = true;
      return this;
    }

    public <I> FieldDef<I, T> build(Getter<I, T> getter) {
      return new FieldDef<>(name, type, stored, false, getter, null);
    }

    public <I> FieldDef<I, T> build(Getter<I, T> getter, Setter<I, T> setter) {
      return new FieldDef<>(name, type, stored, false, getter, setter);
    }

    public <I> FieldDef<I, Stream<T>> buildRepeatable(Getter<I, Stream<T>> getter) {
      return new FieldDef<>(name, type, stored, true, getter, null);
    }

    public <I> FieldDef<I, Stream<T>> buildRepeatable(
        Getter<I, Stream<T>> getter, Setter<I, Stream<T>> setter) {
      return new FieldDef<>(name, type, stored, true, getter, setter);
    }
  }

  private final String name;
  private final FieldType<?> type;
  /** Allow reading the actual data from the index. */
  private final boolean stored;

  private final boolean repeatable;
  private final Getter<I, T> getter;
  private final Optional<Setter<I, T>> setter;

  private FieldDef(
      String name,
      FieldType<?> type,
      boolean stored,
      boolean repeatable,
      Getter<I, T> getter,
      @Nullable Setter<I, T> setter) {
    checkArgument(
        !(repeatable && type == FieldType.INTEGER_RANGE),
        "Range queries against repeated fields are unsupported");
    this.name = checkName(name);
    this.type = requireNonNull(type);
    this.stored = stored;
    this.repeatable = repeatable;
    this.getter = requireNonNull(getter);
    this.setter = Optional.ofNullable(setter);
  }

  private static String checkName(String name) {
    CharMatcher m = CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyz0123456789_");
    checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
    return name;
  }

  /** Returns name of the field. */
  public String getName() {
    return name;
  }

  /** Returns type of the field; for repeatable fields, the inner type, not the iterable type. */
  public FieldType<?> getType() {
    return type;
  }

  /** Returns whether the field should be stored in the index. */
  public boolean isStored() {
    return stored;
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
      return getter.get(input);
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
    if (!setter.isPresent()) {
      return false;
    }

    if (FieldType.STRING_TYPES.stream().anyMatch(t -> t.getName().equals(getType().getName()))) {
      setter.get().set(object, (T) (isRepeatable() ? doc.asStrings() : doc.asString()));
      return true;
    } else if (FieldType.INTEGER_TYPES.stream()
        .anyMatch(t -> t.getName().equals(getType().getName()))) {
      setter.get().set(object, (T) (isRepeatable() ? doc.asIntegers() : doc.asInteger()));
      return true;
    } else if (FieldType.LONG.getName().equals(getType().getName())) {
      setter.get().set(object, (T) (isRepeatable() ? doc.asLongs() : doc.asLong()));
      return true;
    } else if (FieldType.STORED_ONLY.getName().equals(getType().getName())) {
      setter.get().set(object, (T) (isRepeatable() ? doc.asByteArrays() : doc.asByteArray()));
      return true;
    } else if (FieldType.TIMESTAMP.getName().equals(getType().getName())) {
      checkState(!isRepeatable(), "can't repeat timestamp values");
      setter.get().set(object, (T) doc.asTimestamp());
      return true;
    }
    return false;
  }

  /** Returns whether the field is repeatable. */
  public boolean isRepeatable() {
    return repeatable;
  }
}
