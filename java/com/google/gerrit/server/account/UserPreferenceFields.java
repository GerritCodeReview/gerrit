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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.account.UserPreferenceFields.Field.diff;
import static com.google.gerrit.server.account.UserPreferenceFields.Field.edit;
import static com.google.gerrit.server.account.UserPreferenceFields.Field.general;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Field definitions for all user preferences. These definitions provide static typing as well as
 * (de) serialization methods.
 */
public class UserPreferenceFields {
  /** Field definitions for general user preferences. */
  public static class General {
    public static final Field<Integer> CHANGES_PER_PAGE =
        general("changesPerPage", 25, INTEGER_ADAPTER);
    public static final Field<String> DOWNLOAD_SCHEME =
        general("downloadScheme", "TODO", STRING_ADAPTER);
    public static final Field<GeneralPreferencesInfo.DateFormat> DATE_FORMAT =
        general(
            "dateFormat",
            GeneralPreferencesInfo.DateFormat.STD,
            StorageAdapter.single(Object::toString, GeneralPreferencesInfo.DateFormat::valueOf));
    public static final Field<GeneralPreferencesInfo.TimeFormat> TIME_FORMAT =
        general(
            "timeFormat",
            GeneralPreferencesInfo.TimeFormat.HHMM_12,
            StorageAdapter.single(Object::toString, GeneralPreferencesInfo.TimeFormat::valueOf));
    public static final Field<Boolean> EXPAND_INLINE_DIFFS =
        general("expandInlineDiffs", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> HIGHLIGHT_ASSIGNEE_IN_CHANGE_TABLE =
        general("highlightAssigneeInChangeTable", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> RELATIVE_DATE_IN_CHANGE_TABLE =
        general("relativeDateInChangeTable", false, BOOLEAN_ADAPTER);
    public static final Field<GeneralPreferencesInfo.DiffView> DIFF_VIEW =
        general(
            "diffView",
            GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE,
            StorageAdapter.single(Object::toString, GeneralPreferencesInfo.DiffView::valueOf));
    public static final Field<Boolean> SIZE_BAR_IN_CHANGE_TABLE =
        general("sizeBarInChangeTable", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> LEGACY_ID_IN_CHANGE_TABLE =
        general("legacycidInChangeTable", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> MUTE_COMMON_PATH_PREFIXED =
        general("muteCommonPathPrefixes", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SIGNED_OFF_BY =
        general("signedOffBy", false, BOOLEAN_ADAPTER);
    public static final Field<GeneralPreferencesInfo.EmailStrategy> EMAIL_STRATEGY =
        general(
            "emailStrategy",
            GeneralPreferencesInfo.EmailStrategy.ENABLED,
            StorageAdapter.single(Object::toString, GeneralPreferencesInfo.EmailStrategy::valueOf));
    public static final Field<GeneralPreferencesInfo.EmailFormat> EMAIL_FORMAT =
        general(
            "emailFormat",
            GeneralPreferencesInfo.EmailFormat.HTML_PLAINTEXT,
            StorageAdapter.single(Object::toString, GeneralPreferencesInfo.EmailFormat::valueOf));
    public static final Field<GeneralPreferencesInfo.DefaultBase> DEFAULT_BASE =
        general(
            "defaultBase",
            GeneralPreferencesInfo.DefaultBase.FIRST_PARENT,
            StorageAdapter.single(Object::toString, GeneralPreferencesInfo.DefaultBase::valueOf));
    public static final Field<Boolean> PUBLISH_COMMENTS_ON_PUSH =
        general("publishCommentsOnPush", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> WORK_IN_PROGRESS_BY_DEFAULT =
        general("workInProgressByDefault", false, BOOLEAN_ADAPTER);
    public static final Field<ImmutableList<MenuItem>> MY =
        general(
            "my",
            ImmutableList.of(),
            StorageAdapter.repeated(
                m -> m.stream().map(Object::toString).collect(toImmutableList()),
                s -> s.stream().map(MenuItem::fromString).collect(toImmutableList())));
    public static final Field<ImmutableList<String>> CHANGE_TABLE =
        general("changeTable", ImmutableList.of(), REPEATED_STRING_ADAPTER);
  }

  /** Field definitions for edit user preferences. */
  public static class Edit {
    public static final Field<Integer> TAB_SIZE = edit("tabSize", 8, INTEGER_ADAPTER);
    public static final Field<Integer> LINE_LENGTH = edit("lineLength", 100, INTEGER_ADAPTER);
    public static final Field<Integer> INDENT_UNIT = edit("indentUnit", 2, INTEGER_ADAPTER);
    public static final Field<Integer> CURSOR_BLINK_RATE =
        edit("cursorBlinkRate", 0, INTEGER_ADAPTER);

    public static final Field<Boolean> HIDE_TOP_MENU = edit("hideTopMenu", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SHOW_TABS = edit("showTabs", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SHOW_WHITESPACE_ERRORS =
        edit("showWhitespaceErrors", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SYNTAX_HIGHLIGHTING =
        edit("syntaxHighlighting", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> HIDE_LINE_NUMBERS =
        edit("hideLineNumbers", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> MATCH_BRACKETS =
        edit("matchBrackets", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> LINE_WRAPPING = edit("lineWrapping", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> INDENT_WITH_TABS =
        edit("indentWithTabs", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> AUTO_CLOSE_TRACKETS =
        edit("autoCloseBrackets", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SHOW_BASE = edit("showBase", false, BOOLEAN_ADAPTER);
  }

  /** Field definitions for diff user preferences. */
  public static class Diff {
    public static final Field<Integer> CONTEXT = diff("context", 10, INTEGER_ADAPTER);
    public static final Field<Integer> TAB_SIZE = diff("tabSize", 8, INTEGER_ADAPTER);
    public static final Field<Integer> FONT_SIZE = diff("fontSize", 12, INTEGER_ADAPTER);
    public static final Field<Integer> LINE_LENGTH = diff("lineLength", 100, INTEGER_ADAPTER);
    public static final Field<Integer> CURSOR_BLINK_RATE =
        diff("cursorBlinkRate", 0, INTEGER_ADAPTER);

    public static final Field<Boolean> EXPAND_ALL_COMMENTS =
        diff("expandAllComments", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> INTRALINE_DIFFERENCE =
        diff("intralineDifference", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> MANUAL_REVIEW = diff("manualReview", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SHOW_LINE_ENDINGS =
        diff("showLineEndings", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SHOW_TABS = diff("showTabs", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SHOW_WHITESPACE_ERRORS =
        diff("showWhitespaceErrors", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SYNTAX_HIGHLIGHTING =
        diff("syntaxHighlighting", true, BOOLEAN_ADAPTER);
    public static final Field<Boolean> HIDE_TOP_MENU = diff("hideTopMenu", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> AUTO_HIDE_DIFF_TABLE_HEADER =
        diff("autoHideDiffTableHeader", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> HIDE_LINE_NUMBERS =
        diff("hideLineNumbers", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> RENDER_ENTIRE_FILE =
        diff("renderEntireFile", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> HIDE_EMPTY_PANE =
        diff("hideEmptyPane", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> MATCH_BRACKETS =
        diff("matchBrackets", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> LINE_WRAPPING = diff("lineWrapping", false, BOOLEAN_ADAPTER);

    public static final Field<DiffPreferencesInfo.Whitespace> IGNORE_WHITESPACE =
        diff(
            "ignoreWhitespace",
            DiffPreferencesInfo.Whitespace.IGNORE_NONE,
            StorageAdapter.single(Object::toString, DiffPreferencesInfo.Whitespace::valueOf));

    public static final Field<Boolean> RETAIN_HEADER = diff("retainHeader", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SKIP_DELETED = diff("skipDeleted", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SKIP_UNCHANGED =
        diff("skipUnchanged", false, BOOLEAN_ADAPTER);
    public static final Field<Boolean> SKIP_UNCOMMENTED =
        diff("skipUncommented", false, BOOLEAN_ADAPTER);
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

    static <T> Field<T> general(String key, T defaultValue, StorageAdapter<T> stringAdapter) {
      return new Field<>(UserPreferenceSection.GENERAL, key, defaultValue, stringAdapter);
    }

    static <T> Field<T> diff(String key, T defaultValue, StorageAdapter<T> stringAdapter) {
      return new Field<>(UserPreferenceSection.DIFF, key, defaultValue, stringAdapter);
    }

    static <T> Field<T> edit(String key, T defaultValue, StorageAdapter<T> stringAdapter) {
      return new Field<>(UserPreferenceSection.EDIT, key, defaultValue, stringAdapter);
    }

    private Field(
        UserPreferenceSection type, String key, T defaultValue, StorageAdapter<T> stringAdapter) {
      this.type = type;
      this.key = key;
      this.defaultValue = defaultValue;
      this.adapter = stringAdapter;
    }

    @Nullable
    public Optional<T> get(UserPreferences preferences) {
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
      return get(preferences).orElse(defaultValue);
    }

    @Nullable
    public T getOrDefaultFalseToNull(UserPreferences preferences) {
      T value = get(preferences).orElse(defaultValue);
      return Boolean.FALSE.equals(value) ? null : value;
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

    private Optional<T> get(Map<String, Cache.UserPreferences.RepeatedPreference> map) {
      Cache.UserPreferences.RepeatedPreference pref = map.get(key);
      if (pref == null) {
        return Optional.empty();
      }
      return Optional.of(adapter.fromString(ImmutableList.copyOf(pref.getValueList())));
    }

    Cache.UserPreferences.RepeatedPreference protoValue(T val) {
      return toProto(adapter.serializer.apply(val));
    }

    private static Cache.UserPreferences.RepeatedPreference toProto(ImmutableList<String> s) {
      return Cache.UserPreferences.RepeatedPreference.newBuilder().addAllValue(s).build();
    }
  }

  /** Common adapters for all fields. */
  static final StorageAdapter<Boolean> BOOLEAN_ADAPTER =
      StorageAdapter.single(Object::toString, Boolean::valueOf);

  static final StorageAdapter<Integer> INTEGER_ADAPTER =
      StorageAdapter.single(Object::toString, Integer::valueOf);
  static final StorageAdapter<String> STRING_ADAPTER = StorageAdapter.single(s -> s, s -> s);
  static final StorageAdapter<ImmutableList<String>> REPEATED_STRING_ADAPTER =
      StorageAdapter.repeated(s -> s, s -> s);
}
