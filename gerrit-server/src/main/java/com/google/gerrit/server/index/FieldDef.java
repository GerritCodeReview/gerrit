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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CharMatcher;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.Config;

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
    T get(I input) throws OrmException, IOException;
  }

  @FunctionalInterface
  public interface GetterWithArgs<I, T> {
    T get(I input, FillArgs args) throws OrmException, IOException;
  }

  /** Arguments needed to fill in missing data in the input object. */
  public static class FillArgs {
    public final TrackingFooters trackingFooters;
    public final boolean allowsDrafts;
    public final Accounts accounts;
    public final AllUsersName allUsers;

    @Inject
    FillArgs(
        TrackingFooters trackingFooters,
        @GerritServerConfig Config cfg,
        Accounts accounts,
        AllUsersName allUsers) {
      this.trackingFooters = trackingFooters;
      this.allowsDrafts = cfg == null ? true : cfg.getBoolean("change", "allowDrafts", true);
      this.accounts = accounts;
      this.allUsers = allUsers;
    }
  }

  public static class Builder<T> {
    private final FieldType<T> type;
    private final String name;
    private boolean stored;

    public Builder(FieldType<T> type, String name) {
      this.type = checkNotNull(type);
      this.name = checkNotNull(name);
    }

    public Builder<T> stored() {
      this.stored = true;
      return this;
    }

    public <I> FieldDef<I, T> build(Getter<I, T> getter) {
      return build((in, a) -> getter.get(in));
    }

    public <I> FieldDef<I, T> build(GetterWithArgs<I, T> getter) {
      return new FieldDef<>(name, type, stored, false, getter);
    }

    public <I> FieldDef<I, Iterable<T>> buildRepeatable(Getter<I, Iterable<T>> getter) {
      return buildRepeatable((in, a) -> getter.get(in));
    }

    public <I> FieldDef<I, Iterable<T>> buildRepeatable(GetterWithArgs<I, Iterable<T>> getter) {
      return new FieldDef<>(name, type, stored, true, getter);
    }
  }

  private final String name;
  private final FieldType<?> type;
  private final boolean stored;
  private final boolean repeatable;
  private final GetterWithArgs<I, T> getter;

  private FieldDef(
      String name,
      FieldType<?> type,
      boolean stored,
      boolean repeatable,
      GetterWithArgs<I, T> getter) {
    checkArgument(
        !(repeatable && type == FieldType.INTEGER_RANGE),
        "Range queries against repeated fields are unsupported");
    this.name = checkName(name);
    this.type = checkNotNull(type);
    this.stored = stored;
    this.repeatable = repeatable;
    this.getter = checkNotNull(getter);
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
   * @param args arbitrary arguments needed to fill in indexable fields of the input object.
   * @return the field value(s) to index.
   * @throws OrmException
   */
  public T get(I input, FillArgs args) throws OrmException {
    try {
      return getter.get(input, args);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  /** @return whether the field is repeatable. */
  public boolean isRepeatable() {
    return repeatable;
  }
}
