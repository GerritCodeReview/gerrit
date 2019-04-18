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
import static java.util.Objects.requireNonNull;

import com.google.common.base.CharMatcher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.sql.Timestamp;

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
      return new FieldDef<>(name, type, stored, false, getter);
    }

    public <I> FieldDef<I, Iterable<T>> buildRepeatable(Getter<I, Iterable<T>> getter) {
      return new FieldDef<>(name, type, stored, true, getter);
    }
  }

  private final String name;
  private final FieldType<?> type;
  private final boolean stored;
  private final boolean repeatable;
  private final Getter<I, T> getter;

  private FieldDef(
      String name, FieldType<?> type, boolean stored, boolean repeatable, Getter<I, T> getter) {
    checkArgument(
        !(repeatable && type == FieldType.INTEGER_RANGE),
        "Range queries against repeated fields are unsupported");
    this.name = checkName(name);
    this.type = requireNonNull(type);
    this.stored = stored;
    this.repeatable = repeatable;
    this.getter = requireNonNull(getter);
  }

  private static String checkName(String name) {
    CharMatcher m = CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyz0123456789_");
    checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
    return name;
  }

  /** @return name of the field. */
  public String getName() {
    return name;
  }

  /** @return type of the field; for repeatable fields, the inner type, not the iterable type. */
  public FieldType<?> getType() {
    return type;
  }

  /** @return whether the field should be stored in the index. */
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

  /** @return whether the field is repeatable. */
  public boolean isRepeatable() {
    return repeatable;
  }
}
