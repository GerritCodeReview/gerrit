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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import java.util.Collection;

/** Convert preference API objects into internal representations and vice versa. */
public class PreferenceConverter {
  public static GeneralPreferencesInfo general(UserPreferences... preferences) {
    UserPreferences overlay = UserPreferences.overlayDefaults(preferences);
    GeneralPreferencesInfo info = new GeneralPreferencesInfo();
    info.changesPerPage =
        UserPreferenceFields.General.CHANGES_PER_PAGE.getOrDefaultFalseToNull(overlay);
    info.downloadScheme =
        UserPreferenceFields.General.DOWNLOAD_SCHEME.getOrDefaultFalseToNull(overlay);
    info.dateFormat = UserPreferenceFields.General.DATE_FORMAT.getOrDefaultFalseToNull(overlay);
    info.timeFormat = UserPreferenceFields.General.TIME_FORMAT.getOrDefaultFalseToNull(overlay);
    info.expandInlineDiffs =
        UserPreferenceFields.General.EXPAND_INLINE_DIFFS.getOrDefaultFalseToNull(overlay);
    info.highlightAssigneeInChangeTable =
        UserPreferenceFields.General.HIGHLIGHT_ASSIGNEE_IN_CHANGE_TABLE.getOrDefaultFalseToNull(
            overlay);
    info.relativeDateInChangeTable =
        UserPreferenceFields.General.RELATIVE_DATE_IN_CHANGE_TABLE.getOrDefaultFalseToNull(overlay);
    info.diffView = UserPreferenceFields.General.DIFF_VIEW.getOrDefaultFalseToNull(overlay);
    info.sizeBarInChangeTable =
        UserPreferenceFields.General.SIZE_BAR_IN_CHANGE_TABLE.getOrDefaultFalseToNull(overlay);
    info.legacycidInChangeTable =
        UserPreferenceFields.General.LEGACY_ID_IN_CHANGE_TABLE.getOrDefaultFalseToNull(overlay);
    info.muteCommonPathPrefixes =
        UserPreferenceFields.General.MUTE_COMMON_PATH_PREFIXED.getOrDefaultFalseToNull(overlay);
    info.signedOffBy = UserPreferenceFields.General.SIGNED_OFF_BY.getOrDefaultFalseToNull(overlay);
    info.emailStrategy =
        UserPreferenceFields.General.EMAIL_STRATEGY.getOrDefaultFalseToNull(overlay);
    info.emailFormat = UserPreferenceFields.General.EMAIL_FORMAT.getOrDefaultFalseToNull(overlay);
    info.defaultBaseForMerges =
        UserPreferenceFields.General.DEFAULT_BASE.getOrDefaultFalseToNull(overlay);
    info.publishCommentsOnPush =
        UserPreferenceFields.General.PUBLISH_COMMENTS_ON_PUSH.getOrDefaultFalseToNull(overlay);
    info.workInProgressByDefault =
        UserPreferenceFields.General.WORK_IN_PROGRESS_BY_DEFAULT.getOrDefaultFalseToNull(overlay);
    info.my = UserPreferenceFields.General.MY.getOrDefaultFalseToNull(overlay);
    info.changeTable = UserPreferenceFields.General.CHANGE_TABLE.getOrDefaultFalseToNull(overlay);
    return info;
  }

  public static UserPreferences.ForUpdate forUpdate(GeneralPreferencesInfo info) {
    UserPreferences.ForUpdate.Builder builder = UserPreferences.ForUpdate.newBuilder();
    builder.add(UserPreferenceFields.General.CHANGES_PER_PAGE, info.changesPerPage);
    builder.add(UserPreferenceFields.General.DOWNLOAD_SCHEME, info.downloadScheme);
    builder.add(UserPreferenceFields.General.DATE_FORMAT, info.dateFormat);
    builder.add(UserPreferenceFields.General.TIME_FORMAT, info.timeFormat);
    builder.add(UserPreferenceFields.General.EXPAND_INLINE_DIFFS, info.expandInlineDiffs);
    builder.add(
        UserPreferenceFields.General.HIGHLIGHT_ASSIGNEE_IN_CHANGE_TABLE,
        info.highlightAssigneeInChangeTable);
    builder.add(
        UserPreferenceFields.General.RELATIVE_DATE_IN_CHANGE_TABLE, info.relativeDateInChangeTable);
    builder.add(UserPreferenceFields.General.DIFF_VIEW, info.diffView);
    builder.add(UserPreferenceFields.General.SIZE_BAR_IN_CHANGE_TABLE, info.sizeBarInChangeTable);
    builder.add(
        UserPreferenceFields.General.LEGACY_ID_IN_CHANGE_TABLE, info.legacycidInChangeTable);
    builder.add(
        UserPreferenceFields.General.MUTE_COMMON_PATH_PREFIXED, info.muteCommonPathPrefixes);
    builder.add(UserPreferenceFields.General.SIGNED_OFF_BY, info.signedOffBy);
    builder.add(UserPreferenceFields.General.EMAIL_STRATEGY, info.emailStrategy);
    builder.add(UserPreferenceFields.General.EMAIL_FORMAT, info.emailFormat);
    builder.add(UserPreferenceFields.General.DEFAULT_BASE, info.defaultBaseForMerges);
    builder.add(UserPreferenceFields.General.PUBLISH_COMMENTS_ON_PUSH, info.publishCommentsOnPush);
    builder.add(
        UserPreferenceFields.General.WORK_IN_PROGRESS_BY_DEFAULT, info.workInProgressByDefault);
    builder.add(UserPreferenceFields.General.MY, copyOf(info.my));
    builder.add(UserPreferenceFields.General.CHANGE_TABLE, copyOf(info.changeTable));
    return builder.build();
  }

  public static DiffPreferencesInfo diff(UserPreferences... preferences) {
    UserPreferences overlay = UserPreferences.overlayDefaults(preferences);
    DiffPreferencesInfo info = new DiffPreferencesInfo();
    info.context = UserPreferenceFields.Diff.CONTEXT.getOrDefaultFalseToNull(overlay);
    info.tabSize = UserPreferenceFields.Diff.CONTEXT.getOrDefaultFalseToNull(overlay);
    info.fontSize = UserPreferenceFields.Diff.CONTEXT.getOrDefaultFalseToNull(overlay);
    info.lineLength = UserPreferenceFields.Diff.CONTEXT.getOrDefaultFalseToNull(overlay);
    info.cursorBlinkRate = UserPreferenceFields.Diff.CONTEXT.getOrDefaultFalseToNull(overlay);
    info.expandAllComments =
        UserPreferenceFields.Diff.EXPAND_ALL_COMMENTS.getOrDefaultFalseToNull(overlay);
    info.intralineDifference =
        UserPreferenceFields.Diff.INTRALINE_DIFFERENCE.getOrDefaultFalseToNull(overlay);
    info.manualReview = UserPreferenceFields.Diff.MANUAL_REVIEW.getOrDefaultFalseToNull(overlay);
    info.showLineEndings =
        UserPreferenceFields.Diff.SHOW_LINE_ENDINGS.getOrDefaultFalseToNull(overlay);
    info.showTabs = UserPreferenceFields.Diff.SHOW_TABS.getOrDefaultFalseToNull(overlay);
    info.showWhitespaceErrors =
        UserPreferenceFields.Diff.SHOW_WHITESPACE_ERRORS.getOrDefaultFalseToNull(overlay);
    info.syntaxHighlighting =
        UserPreferenceFields.Diff.SYNTAX_HIGHLIGHTING.getOrDefaultFalseToNull(overlay);
    info.hideTopMenu = UserPreferenceFields.Diff.HIDE_TOP_MENU.getOrDefaultFalseToNull(overlay);
    info.autoHideDiffTableHeader =
        UserPreferenceFields.Diff.AUTO_HIDE_DIFF_TABLE_HEADER.getOrDefaultFalseToNull(overlay);
    info.hideLineNumbers =
        UserPreferenceFields.Diff.HIDE_LINE_NUMBERS.getOrDefaultFalseToNull(overlay);
    info.renderEntireFile =
        UserPreferenceFields.Diff.RENDER_ENTIRE_FILE.getOrDefaultFalseToNull(overlay);
    info.hideEmptyPane = UserPreferenceFields.Diff.HIDE_EMPTY_PANE.getOrDefaultFalseToNull(overlay);
    info.matchBrackets = UserPreferenceFields.Diff.MATCH_BRACKETS.getOrDefaultFalseToNull(overlay);
    info.lineWrapping = UserPreferenceFields.Diff.LINE_WRAPPING.getOrDefaultFalseToNull(overlay);
    info.ignoreWhitespace =
        UserPreferenceFields.Diff.IGNORE_WHITESPACE.getOrDefaultFalseToNull(overlay);
    info.retainHeader = UserPreferenceFields.Diff.RETAIN_HEADER.getOrDefaultFalseToNull(overlay);
    info.skipDeleted = UserPreferenceFields.Diff.SKIP_DELETED.getOrDefaultFalseToNull(overlay);
    info.skipUnchanged = UserPreferenceFields.Diff.SKIP_UNCHANGED.getOrDefaultFalseToNull(overlay);
    info.skipUncommented =
        UserPreferenceFields.Diff.SKIP_UNCOMMENTED.getOrDefaultFalseToNull(overlay);
    return info;
  }

  public static UserPreferences.ForUpdate forUpdate(DiffPreferencesInfo info) {
    UserPreferences.ForUpdate.Builder builder = UserPreferences.ForUpdate.newBuilder();
    builder.add(UserPreferenceFields.Diff.CONTEXT, info.context);
    builder.add(UserPreferenceFields.Diff.TAB_SIZE, info.tabSize);
    builder.add(UserPreferenceFields.Diff.FONT_SIZE, info.fontSize);
    builder.add(UserPreferenceFields.Diff.LINE_LENGTH, info.lineLength);
    builder.add(UserPreferenceFields.Diff.CURSOR_BLINK_RATE, info.cursorBlinkRate);
    builder.add(UserPreferenceFields.Diff.EXPAND_ALL_COMMENTS, info.expandAllComments);
    builder.add(UserPreferenceFields.Diff.INTRALINE_DIFFERENCE, info.intralineDifference);
    builder.add(UserPreferenceFields.Diff.MANUAL_REVIEW, info.manualReview);
    builder.add(UserPreferenceFields.Diff.SHOW_LINE_ENDINGS, info.showLineEndings);
    builder.add(UserPreferenceFields.Diff.SHOW_TABS, info.showTabs);
    builder.add(UserPreferenceFields.Diff.SHOW_WHITESPACE_ERRORS, info.showWhitespaceErrors);
    builder.add(UserPreferenceFields.Diff.SYNTAX_HIGHLIGHTING, info.syntaxHighlighting);
    builder.add(UserPreferenceFields.Diff.HIDE_TOP_MENU, info.hideTopMenu);
    builder.add(
        UserPreferenceFields.Diff.AUTO_HIDE_DIFF_TABLE_HEADER, info.autoHideDiffTableHeader);
    builder.add(UserPreferenceFields.Diff.HIDE_LINE_NUMBERS, info.hideLineNumbers);
    builder.add(UserPreferenceFields.Diff.RENDER_ENTIRE_FILE, info.renderEntireFile);
    builder.add(UserPreferenceFields.Diff.HIDE_EMPTY_PANE, info.hideEmptyPane);
    builder.add(UserPreferenceFields.Diff.MATCH_BRACKETS, info.matchBrackets);
    builder.add(UserPreferenceFields.Diff.LINE_WRAPPING, info.lineWrapping);
    builder.add(UserPreferenceFields.Diff.IGNORE_WHITESPACE, info.ignoreWhitespace);
    builder.add(UserPreferenceFields.Diff.RETAIN_HEADER, info.retainHeader);
    builder.add(UserPreferenceFields.Diff.SKIP_DELETED, info.skipDeleted);
    builder.add(UserPreferenceFields.Diff.SKIP_UNCHANGED, info.skipUnchanged);
    builder.add(UserPreferenceFields.Diff.SKIP_UNCOMMENTED, info.skipUncommented);
    return builder.build();
  }

  public static EditPreferencesInfo edit(UserPreferences... preferences) {
    UserPreferences overlay = UserPreferences.overlayDefaults(preferences);
    EditPreferencesInfo info = new EditPreferencesInfo();
    info.tabSize = UserPreferenceFields.Edit.TAB_SIZE.getOrDefaultFalseToNull(overlay);
    info.lineLength = UserPreferenceFields.Edit.TAB_SIZE.getOrDefaultFalseToNull(overlay);
    info.indentUnit = UserPreferenceFields.Edit.TAB_SIZE.getOrDefaultFalseToNull(overlay);
    info.cursorBlinkRate = UserPreferenceFields.Edit.TAB_SIZE.getOrDefaultFalseToNull(overlay);
    info.hideTopMenu = UserPreferenceFields.Edit.HIDE_TOP_MENU.getOrDefaultFalseToNull(overlay);
    info.showTabs = UserPreferenceFields.Edit.SHOW_TABS.getOrDefaultFalseToNull(overlay);
    info.showWhitespaceErrors =
        UserPreferenceFields.Edit.SHOW_WHITESPACE_ERRORS.getOrDefaultFalseToNull(overlay);
    info.syntaxHighlighting =
        UserPreferenceFields.Edit.SYNTAX_HIGHLIGHTING.getOrDefaultFalseToNull(overlay);
    info.hideLineNumbers =
        UserPreferenceFields.Edit.HIDE_LINE_NUMBERS.getOrDefaultFalseToNull(overlay);
    info.matchBrackets = UserPreferenceFields.Edit.MATCH_BRACKETS.getOrDefaultFalseToNull(overlay);
    info.lineWrapping = UserPreferenceFields.Edit.LINE_WRAPPING.getOrDefaultFalseToNull(overlay);
    info.indentWithTabs =
        UserPreferenceFields.Edit.INDENT_WITH_TABS.getOrDefaultFalseToNull(overlay);
    info.autoCloseBrackets =
        UserPreferenceFields.Edit.AUTO_CLOSE_TRACKETS.getOrDefaultFalseToNull(overlay);
    info.showBase = UserPreferenceFields.Edit.SHOW_BASE.getOrDefaultFalseToNull(overlay);
    return info;
  }

  public static UserPreferences.ForUpdate forUpdate(EditPreferencesInfo info) {
    UserPreferences.ForUpdate.Builder builder = UserPreferences.ForUpdate.newBuilder();
    builder.add(UserPreferenceFields.Edit.TAB_SIZE, info.tabSize);
    builder.add(UserPreferenceFields.Edit.LINE_LENGTH, info.lineLength);
    builder.add(UserPreferenceFields.Edit.INDENT_UNIT, info.indentUnit);
    builder.add(UserPreferenceFields.Edit.CURSOR_BLINK_RATE, info.cursorBlinkRate);
    builder.add(UserPreferenceFields.Edit.HIDE_LINE_NUMBERS, info.hideTopMenu);
    builder.add(UserPreferenceFields.Edit.SHOW_TABS, info.showTabs);
    builder.add(UserPreferenceFields.Edit.SHOW_WHITESPACE_ERRORS, info.showWhitespaceErrors);
    builder.add(UserPreferenceFields.Edit.SYNTAX_HIGHLIGHTING, info.syntaxHighlighting);
    builder.add(UserPreferenceFields.Edit.HIDE_LINE_NUMBERS, info.hideLineNumbers);
    builder.add(UserPreferenceFields.Edit.MATCH_BRACKETS, info.matchBrackets);
    builder.add(UserPreferenceFields.Edit.LINE_WRAPPING, info.lineWrapping);
    builder.add(UserPreferenceFields.Edit.INDENT_WITH_TABS, info.indentWithTabs);
    builder.add(UserPreferenceFields.Edit.AUTO_CLOSE_TRACKETS, info.autoCloseBrackets);
    builder.add(UserPreferenceFields.Edit.AUTO_CLOSE_TRACKETS, info.autoCloseBrackets);
    builder.add(UserPreferenceFields.Edit.SHOW_BASE, info.showBase);
    return builder.build();
  }

  private static <T> ImmutableList<T> copyOf(@Nullable Collection<T> collection) {
    return collection == null ? ImmutableList.of() : ImmutableList.copyOf(collection);
  }
}
