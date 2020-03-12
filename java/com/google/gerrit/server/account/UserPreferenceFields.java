// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.Map;
import java.util.function.Function;

/**
 * Field definitions for all user preferences. These definitions provide static typing as well as
 * (de) serialization methods.
 */
public class UserPreferenceFields {
  /** Field definitions for general user preferences. */
  public static class General {
    public static final Field<Integer> CHANGES_PER_PAGE =
        Field.of(UserPreferenceSection.GENERAL, "changesPerPage", 0, INTEGER_ADAPTER);
    // TODO(hiesel): Add all fields

    public static final Field<String> DOWNLOAD_SCHEME =
        Field.of(UserPreferenceSection.GENERAL, "downloadScheme", "foo", STRING_ADAPTER);
    public static final Field<ImmutableList<String>> CHANGE_TABLE =
        Field.of(UserPreferenceSection.GENERAL, "changeTable", null, REPEATED_STRING_ADAPTER);
  }

  /** Field definitions for edit user preferences. */
  public static class Edit {
    public static final Field<Integer> TAB_SIZE =
        Field.of(UserPreferenceSection.EDIT, "tabSize", 8, INTEGER_ADAPTER);
    public static final Field<Boolean> HIDE_TOP_MENU =
        Field.of(UserPreferenceSection.EDIT, "hideTopMenu", false, BOOLEAN_ADAPTER);
    // TODO(hiesel): Add all fields
  }

  /** Field definitions for diff user preferences. */
  public static class Diff {
    public static final Field<Integer> CONTEXT =
        Field.of(UserPreferenceSection.DIFF, "context", 10, INTEGER_ADAPTER);
    public static final Field<Integer> TAB_SIZE =
        Field.of(UserPreferenceSection.DIFF, "downloadScheme", 8, INTEGER_ADAPTER);
    // TODO(hiesel): Add all fields
  }

  /**
   * Adapter to read from and write to the serialized representation. To be used when interacting
   * with values stored in Git or a cache.
   */
  static class StorageAdapter<T> {
    /** Creates an adapter that (de)serializes single values. */
    static <T> StorageAdapter<T> single(
        Function<T, String> serializer, Function<String, T> deserializer) {
      return new StorageAdapter<>(
          t -> ImmutableList.of(serializer.apply(t)),
          s -> deserializer.apply(Iterables.getOnlyElement(s)));
    }

    /** Creates an adapter that (de)serializes repeated values. */
    static <T> StorageAdapter<T> repeated(
        Function<T, ImmutableList<String>> serializer,
        Function<ImmutableList<String>, T> deserializer) {
      return new StorageAdapter<>(serializer, deserializer);
    }

    private final Function<T, ImmutableList<String>> serializer;
    private final Function<ImmutableList<String>, T> deserializer;

    private StorageAdapter(
        Function<T, ImmutableList<String>> serializer,
        Function<ImmutableList<String>, T> deserializer) {
      this.serializer = serializer;
      this.deserializer = deserializer;
    }

    ImmutableList<String> toString(T v) {
      return serializer.apply(v);
    }

    T fromString(ImmutableList<String> s) {
      return deserializer.apply(s);
    }
  }

  /** Field definition for a user preference. Can be used to obtain a typed instance. */
  public static class Field<T> {
    private final UserPreferenceSection type;
    private final String key;
    private final T defaultValue;
    private final StorageAdapter<T> adapter;

    static <T> Field<T> of(
        UserPreferenceSection type, String key, T defaultValue, StorageAdapter<T> stringAdapter) {
      return new Field<>(type, key, defaultValue, stringAdapter);
    }

    private Field(
        UserPreferenceSection type, String key, T defaultValue, StorageAdapter<T> stringAdapter) {
      this.type = type;
      this.key = key;
      this.defaultValue = defaultValue;
      this.adapter = stringAdapter;
    }

    @Nullable
    public T get(UserPreferences preferences) {
      switch (type) {
        case GENERAL:
          return get(preferences.preferences().getGeneralMap());
        case EDIT:
          return get(preferences.preferences().getEditMap());
        case DIFF:
          return get(preferences.preferences().getDiffMap());
        default:
          // TODO(hiesel): Remove in Java 12
          throw new IllegalStateException();
      }
    }

    public T getOrDefault(UserPreferences preferences) {
      return firstNonNull(get(preferences), defaultValue);
    }

    public String key() {
      return key;
    }

    public T defaultValue() {
      return defaultValue;
    }

    public UserPreferenceSection type() {
      return type;
    }

    @Nullable
    private T get(Map<String, Cache.UserPreferences.RepeatedPreference> map) {
      Cache.UserPreferences.RepeatedPreference pref = map.get(key);
      if (pref == null) {
        return null;
      }
      return adapter.fromString(ImmutableList.copyOf(pref.getValueList()));
    }

    public void set(Map<String, Cache.UserPreferences.RepeatedPreference> map, T val) {
      Cache.UserPreferences.RepeatedPreference pref = map.get(key);
      map.put(key(), toProto(adapter.toString(val)));
    }

    public void setDefault(Map<String, Cache.UserPreferences.RepeatedPreference> map) {
      set(map, defaultValue);
    }

    public static Cache.UserPreferences.RepeatedPreference toProto(ImmutableList<String> s) {
      return Cache.UserPreferences.RepeatedPreference.newBuilder().addAllValue(s).build();
    }
  }

  /** Adapters for all fields. */
  static final StorageAdapter<Boolean> BOOLEAN_ADAPTER =
      StorageAdapter.single(Object::toString, Boolean::valueOf);

  static final StorageAdapter<Integer> INTEGER_ADAPTER =
      StorageAdapter.single(Object::toString, Integer::valueOf);
  static final StorageAdapter<String> STRING_ADAPTER = StorageAdapter.single(s -> s, s -> s);
  static final StorageAdapter<ImmutableList<String>> REPEATED_STRING_ADAPTER =
      StorageAdapter.repeated(s -> s, s -> s);
}
