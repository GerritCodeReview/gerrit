// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.converter.SafeProtoConverter;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.proto.Entities.UserPreferences;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.google.protobuf.ProtocolMessageEnum;
import java.util.function.Function;

/**
 * Converters for user preferences data classes
 *
 * <p>Upstream, we use java representations of the preference classes. Internally, we store proto
 * equivalents in Spanner.
 */
public final class UserPreferencesConverter {
  public enum GeneralPreferencesInfoConverter
      implements
          SafeProtoConverter<UserPreferences.GeneralPreferencesInfo, GeneralPreferencesInfo> {
    GENERAL_PREFERENCES_INFO_CONVERTER;

    @Override
    public UserPreferences.GeneralPreferencesInfo toProto(GeneralPreferencesInfo info) {
      UserPreferences.GeneralPreferencesInfo.Builder builder =
          UserPreferences.GeneralPreferencesInfo.newBuilder();
      builder = setIfNotNull(builder, builder::setChangesPerPage, info.changesPerPage);
      builder = setIfNotNull(builder, builder::setDownloadScheme, info.downloadScheme);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setTheme,
              UserPreferences.GeneralPreferencesInfo.Theme::valueOf,
              info.theme);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setDateFormat,
              UserPreferences.GeneralPreferencesInfo.DateFormat::valueOf,
              info.dateFormat);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setTimeFormat,
              UserPreferences.GeneralPreferencesInfo.TimeFormat::valueOf,
              info.timeFormat);
      builder = setIfNotNull(builder, builder::setExpandInlineDiffs, info.expandInlineDiffs);
      builder =
          setIfNotNull(
              builder, builder::setRelativeDateInChangeTable, info.relativeDateInChangeTable);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setDiffView,
              UserPreferences.GeneralPreferencesInfo.DiffView::valueOf,
              info.diffView);
      builder = setIfNotNull(builder, builder::setSizeBarInChangeTable, info.sizeBarInChangeTable);
      builder =
          setIfNotNull(builder, builder::setLegacycidInChangeTable, info.legacycidInChangeTable);
      builder =
          setIfNotNull(builder, builder::setMuteCommonPathPrefixes, info.muteCommonPathPrefixes);
      builder = setIfNotNull(builder, builder::setSignedOffBy, info.signedOffBy);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setEmailStrategy,
              UserPreferences.GeneralPreferencesInfo.EmailStrategy::valueOf,
              info.emailStrategy);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setEmailFormat,
              UserPreferences.GeneralPreferencesInfo.EmailFormat::valueOf,
              info.emailFormat);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setDefaultBaseForMerges,
              UserPreferences.GeneralPreferencesInfo.DefaultBase::valueOf,
              info.defaultBaseForMerges);
      builder =
          setIfNotNull(builder, builder::setPublishCommentsOnPush, info.publishCommentsOnPush);
      builder =
          setIfNotNull(
              builder, builder::setDisableKeyboardShortcuts, info.disableKeyboardShortcuts);
      builder =
          setIfNotNull(
              builder, builder::setDisableTokenHighlighting, info.disableTokenHighlighting);
      builder =
          setIfNotNull(builder, builder::setWorkInProgressByDefault, info.workInProgressByDefault);
      if (info.my != null) {
        builder =
            builder.addAllMyMenuItems(
                info.my.stream()
                    .map(MenuItemConverter.MENU_ITEM_CONVERTER::toProto)
                    .collect(toImmutableList()));
      }
      if (info.changeTable != null) {
        builder = builder.addAllChangeTable(info.changeTable);
      }
      builder =
          setIfNotNull(
              builder, builder::setAllowBrowserNotifications, info.allowBrowserNotifications);
      builder =
          setIfNotNull(
              builder,
              builder::setAllowSuggestCodeWhileCommenting,
              info.allowSuggestCodeWhileCommenting);
      builder =
          setIfNotNull(
              builder, builder::setAllowAutocompletingComments, info.allowAutocompletingComments);
      builder = setIfNotNull(builder, builder::setDiffPageSidebar, info.diffPageSidebar);
      return builder.build();
    }

    @Override
    public GeneralPreferencesInfo fromProto(UserPreferences.GeneralPreferencesInfo proto) {
      GeneralPreferencesInfo res = new GeneralPreferencesInfo();
      res.changesPerPage = proto.hasChangesPerPage() ? proto.getChangesPerPage() : null;
      res.downloadScheme = proto.hasDownloadScheme() ? proto.getDownloadScheme() : null;
      res.theme =
          proto.hasTheme() ? GeneralPreferencesInfo.Theme.valueOf(proto.getTheme().name()) : null;
      res.dateFormat =
          proto.hasDateFormat()
              ? GeneralPreferencesInfo.DateFormat.valueOf(proto.getDateFormat().name())
              : null;
      res.timeFormat =
          proto.hasTimeFormat()
              ? GeneralPreferencesInfo.TimeFormat.valueOf(proto.getTimeFormat().name())
              : null;
      res.expandInlineDiffs = proto.hasExpandInlineDiffs() ? proto.getExpandInlineDiffs() : null;
      res.relativeDateInChangeTable =
          proto.hasRelativeDateInChangeTable() ? proto.getRelativeDateInChangeTable() : null;
      res.diffView =
          proto.hasDiffView()
              ? GeneralPreferencesInfo.DiffView.valueOf(proto.getDiffView().name())
              : null;
      res.sizeBarInChangeTable =
          proto.hasSizeBarInChangeTable() ? proto.getSizeBarInChangeTable() : null;
      res.legacycidInChangeTable =
          proto.hasLegacycidInChangeTable() ? proto.getLegacycidInChangeTable() : null;
      res.muteCommonPathPrefixes =
          proto.hasMuteCommonPathPrefixes() ? proto.getMuteCommonPathPrefixes() : null;
      res.signedOffBy = proto.hasSignedOffBy() ? proto.getSignedOffBy() : null;
      res.emailStrategy =
          proto.hasEmailStrategy()
              ? GeneralPreferencesInfo.EmailStrategy.valueOf(proto.getEmailStrategy().name())
              : null;
      res.emailFormat =
          proto.hasEmailFormat()
              ? GeneralPreferencesInfo.EmailFormat.valueOf(proto.getEmailFormat().name())
              : null;
      res.defaultBaseForMerges =
          proto.hasDefaultBaseForMerges()
              ? GeneralPreferencesInfo.DefaultBase.valueOf(proto.getDefaultBaseForMerges().name())
              : null;
      res.publishCommentsOnPush =
          proto.hasPublishCommentsOnPush() ? proto.getPublishCommentsOnPush() : null;
      res.disableKeyboardShortcuts =
          proto.hasDisableKeyboardShortcuts() ? proto.getDisableKeyboardShortcuts() : null;
      res.disableTokenHighlighting =
          proto.hasDisableTokenHighlighting() ? proto.getDisableTokenHighlighting() : null;
      res.workInProgressByDefault =
          proto.hasWorkInProgressByDefault() ? proto.getWorkInProgressByDefault() : null;
      res.my =
          proto.getMyMenuItemsCount() != 0
              ? proto.getMyMenuItemsList().stream()
                  .map(MenuItemConverter.MENU_ITEM_CONVERTER::fromProto)
                  .collect(toImmutableList())
              : null;
      res.changeTable = proto.getChangeTableCount() != 0 ? proto.getChangeTableList() : null;
      res.allowBrowserNotifications =
          proto.hasAllowBrowserNotifications() ? proto.getAllowBrowserNotifications() : null;
      res.allowSuggestCodeWhileCommenting =
          proto.hasAllowSuggestCodeWhileCommenting()
              ? proto.getAllowSuggestCodeWhileCommenting()
              : null;
      res.allowAutocompletingComments =
          proto.hasAllowAutocompletingComments() ? proto.getAllowAutocompletingComments() : null;
      res.diffPageSidebar = proto.hasDiffPageSidebar() ? proto.getDiffPageSidebar() : null;
      return res;
    }

    @Override
    public Parser<UserPreferences.GeneralPreferencesInfo> getParser() {
      return UserPreferences.GeneralPreferencesInfo.parser();
    }

    public enum MenuItemConverter
        implements SafeProtoConverter<UserPreferences.GeneralPreferencesInfo.MenuItem, MenuItem> {
      MENU_ITEM_CONVERTER;

      @Override
      public UserPreferences.GeneralPreferencesInfo.MenuItem toProto(MenuItem javaItem) {
        UserPreferences.GeneralPreferencesInfo.MenuItem.Builder builder =
            UserPreferences.GeneralPreferencesInfo.MenuItem.newBuilder();
        builder = setIfNotNull(builder, builder::setName, trimSafe(javaItem.name));
        builder = setIfNotNull(builder, builder::setUrl, trimSafe(javaItem.url));
        builder = setIfNotNull(builder, builder::setTarget, trimSafe(javaItem.target));
        builder = setIfNotNull(builder, builder::setId, trimSafe(javaItem.id));
        return builder.build();
      }

      private static @Nullable String trimSafe(@Nullable String s) {
        return s == null ? s : s.trim();
      }

      @Override
      public MenuItem fromProto(UserPreferences.GeneralPreferencesInfo.MenuItem proto) {
        return new MenuItem(
            proto.hasName() ? proto.getName().trim() : null,
            proto.hasUrl() ? proto.getUrl().trim() : null,
            proto.hasTarget() ? proto.getTarget().trim() : null,
            proto.hasId() ? proto.getId().trim() : null);
      }

      @Override
      public Parser<UserPreferences.GeneralPreferencesInfo.MenuItem> getParser() {
        return UserPreferences.GeneralPreferencesInfo.MenuItem.parser();
      }

      @Override
      public Class<UserPreferences.GeneralPreferencesInfo.MenuItem> getProtoClass() {
        return UserPreferences.GeneralPreferencesInfo.MenuItem.class;
      }

      @Override
      public Class<MenuItem> getEntityClass() {
        return MenuItem.class;
      }
    }

    @Override
    public Class<UserPreferences.GeneralPreferencesInfo> getProtoClass() {
      return UserPreferences.GeneralPreferencesInfo.class;
    }

    @Override
    public Class<GeneralPreferencesInfo> getEntityClass() {
      return GeneralPreferencesInfo.class;
    }
  }

  public enum DiffPreferencesInfoConverter
      implements SafeProtoConverter<UserPreferences.DiffPreferencesInfo, DiffPreferencesInfo> {
    DIFF_PREFERENCES_INFO_CONVERTER;

    @Override
    public UserPreferences.DiffPreferencesInfo toProto(DiffPreferencesInfo info) {
      UserPreferences.DiffPreferencesInfo.Builder builder =
          UserPreferences.DiffPreferencesInfo.newBuilder();
      builder = setIfNotNull(builder, builder::setContext, info.context);
      builder = setIfNotNull(builder, builder::setTabSize, info.tabSize);
      builder = setIfNotNull(builder, builder::setFontSize, info.fontSize);
      builder = setIfNotNull(builder, builder::setLineLength, info.lineLength);
      builder = setIfNotNull(builder, builder::setCursorBlinkRate, info.cursorBlinkRate);
      builder = setIfNotNull(builder, builder::setExpandAllComments, info.expandAllComments);
      builder = setIfNotNull(builder, builder::setIntralineDifference, info.intralineDifference);
      builder = setIfNotNull(builder, builder::setManualReview, info.manualReview);
      builder = setIfNotNull(builder, builder::setShowLineEndings, info.showLineEndings);
      builder = setIfNotNull(builder, builder::setShowTabs, info.showTabs);
      builder = setIfNotNull(builder, builder::setShowWhitespaceErrors, info.showWhitespaceErrors);
      builder = setIfNotNull(builder, builder::setSyntaxHighlighting, info.syntaxHighlighting);
      builder = setIfNotNull(builder, builder::setHideTopMenu, info.hideTopMenu);
      builder =
          setIfNotNull(builder, builder::setAutoHideDiffTableHeader, info.autoHideDiffTableHeader);
      builder = setIfNotNull(builder, builder::setHideLineNumbers, info.hideLineNumbers);
      builder = setIfNotNull(builder, builder::setRenderEntireFile, info.renderEntireFile);
      builder = setIfNotNull(builder, builder::setHideEmptyPane, info.hideEmptyPane);
      builder = setIfNotNull(builder, builder::setMatchBrackets, info.matchBrackets);
      builder = setIfNotNull(builder, builder::setLineWrapping, info.lineWrapping);
      builder =
          setEnumIfNotNull(
              builder,
              builder::setIgnoreWhitespace,
              UserPreferences.DiffPreferencesInfo.Whitespace::valueOf,
              info.ignoreWhitespace);
      builder = setIfNotNull(builder, builder::setRetainHeader, info.retainHeader);
      builder = setIfNotNull(builder, builder::setSkipDeleted, info.skipDeleted);
      builder = setIfNotNull(builder, builder::setSkipUnchanged, info.skipUnchanged);
      builder = setIfNotNull(builder, builder::setSkipUncommented, info.skipUncommented);
      return builder.build();
    }

    @Override
    public DiffPreferencesInfo fromProto(UserPreferences.DiffPreferencesInfo proto) {
      DiffPreferencesInfo res = new DiffPreferencesInfo();
      res.context = proto.hasContext() ? proto.getContext() : null;
      res.tabSize = proto.hasTabSize() ? proto.getTabSize() : null;
      res.fontSize = proto.hasFontSize() ? proto.getFontSize() : null;
      res.lineLength = proto.hasLineLength() ? proto.getLineLength() : null;
      res.cursorBlinkRate = proto.hasCursorBlinkRate() ? proto.getCursorBlinkRate() : null;
      res.expandAllComments = proto.hasExpandAllComments() ? proto.getExpandAllComments() : null;
      res.intralineDifference =
          proto.hasIntralineDifference() ? proto.getIntralineDifference() : null;
      res.manualReview = proto.hasManualReview() ? proto.getManualReview() : null;
      res.showLineEndings = proto.hasShowLineEndings() ? proto.getShowLineEndings() : null;
      res.showTabs = proto.hasShowTabs() ? proto.getShowTabs() : null;
      res.showWhitespaceErrors =
          proto.hasShowWhitespaceErrors() ? proto.getShowWhitespaceErrors() : null;
      res.syntaxHighlighting = proto.hasSyntaxHighlighting() ? proto.getSyntaxHighlighting() : null;
      res.hideTopMenu = proto.hasHideTopMenu() ? proto.getHideTopMenu() : null;
      res.autoHideDiffTableHeader =
          proto.hasAutoHideDiffTableHeader() ? proto.getAutoHideDiffTableHeader() : null;
      res.hideLineNumbers = proto.hasHideLineNumbers() ? proto.getHideLineNumbers() : null;
      res.renderEntireFile = proto.hasRenderEntireFile() ? proto.getRenderEntireFile() : null;
      res.hideEmptyPane = proto.hasHideEmptyPane() ? proto.getHideEmptyPane() : null;
      res.matchBrackets = proto.hasMatchBrackets() ? proto.getMatchBrackets() : null;
      res.lineWrapping = proto.hasLineWrapping() ? proto.getLineWrapping() : null;
      res.ignoreWhitespace =
          proto.hasIgnoreWhitespace()
              ? DiffPreferencesInfo.Whitespace.valueOf(proto.getIgnoreWhitespace().name())
              : null;
      res.retainHeader = proto.hasRetainHeader() ? proto.getRetainHeader() : null;
      res.skipDeleted = proto.hasSkipDeleted() ? proto.getSkipDeleted() : null;
      res.skipUnchanged = proto.hasSkipUnchanged() ? proto.getSkipUnchanged() : null;
      res.skipUncommented = proto.hasSkipUncommented() ? proto.getSkipUncommented() : null;
      return res;
    }

    @Override
    public Parser<UserPreferences.DiffPreferencesInfo> getParser() {
      return UserPreferences.DiffPreferencesInfo.parser();
    }

    @Override
    public Class<UserPreferences.DiffPreferencesInfo> getProtoClass() {
      return UserPreferences.DiffPreferencesInfo.class;
    }

    @Override
    public Class<DiffPreferencesInfo> getEntityClass() {
      return DiffPreferencesInfo.class;
    }
  }

  public enum EditPreferencesInfoConverter
      implements SafeProtoConverter<UserPreferences.EditPreferencesInfo, EditPreferencesInfo> {
    EDIT_PREFERENCES_INFO_CONVERTER;

    @Override
    public UserPreferences.EditPreferencesInfo toProto(EditPreferencesInfo info) {
      UserPreferences.EditPreferencesInfo.Builder builder =
          UserPreferences.EditPreferencesInfo.newBuilder();
      builder = setIfNotNull(builder, builder::setTabSize, info.tabSize);
      builder = setIfNotNull(builder, builder::setLineLength, info.lineLength);
      builder = setIfNotNull(builder, builder::setIndentUnit, info.indentUnit);
      builder = setIfNotNull(builder, builder::setCursorBlinkRate, info.cursorBlinkRate);
      builder = setIfNotNull(builder, builder::setHideTopMenu, info.hideTopMenu);
      builder = setIfNotNull(builder, builder::setShowTabs, info.showTabs);
      builder = setIfNotNull(builder, builder::setShowWhitespaceErrors, info.showWhitespaceErrors);
      builder = setIfNotNull(builder, builder::setSyntaxHighlighting, info.syntaxHighlighting);
      builder = setIfNotNull(builder, builder::setHideLineNumbers, info.hideLineNumbers);
      builder = setIfNotNull(builder, builder::setMatchBrackets, info.matchBrackets);
      builder = setIfNotNull(builder, builder::setLineWrapping, info.lineWrapping);
      builder = setIfNotNull(builder, builder::setIndentWithTabs, info.indentWithTabs);
      builder = setIfNotNull(builder, builder::setAutoCloseBrackets, info.autoCloseBrackets);
      builder = setIfNotNull(builder, builder::setShowBase, info.showBase);
      return builder.build();
    }

    @Override
    public EditPreferencesInfo fromProto(UserPreferences.EditPreferencesInfo proto) {
      EditPreferencesInfo res = new EditPreferencesInfo();
      res.tabSize = proto.hasTabSize() ? proto.getTabSize() : null;
      res.lineLength = proto.hasLineLength() ? proto.getLineLength() : null;
      res.indentUnit = proto.hasIndentUnit() ? proto.getIndentUnit() : null;
      res.cursorBlinkRate = proto.hasCursorBlinkRate() ? proto.getCursorBlinkRate() : null;
      res.hideTopMenu = proto.hasHideTopMenu() ? proto.getHideTopMenu() : null;
      res.showTabs = proto.hasShowTabs() ? proto.getShowTabs() : null;
      res.showWhitespaceErrors =
          proto.hasShowWhitespaceErrors() ? proto.getShowWhitespaceErrors() : null;
      res.syntaxHighlighting = proto.hasSyntaxHighlighting() ? proto.getSyntaxHighlighting() : null;
      res.hideLineNumbers = proto.hasHideLineNumbers() ? proto.getHideLineNumbers() : null;
      res.matchBrackets = proto.hasMatchBrackets() ? proto.getMatchBrackets() : null;
      res.lineWrapping = proto.hasLineWrapping() ? proto.getLineWrapping() : null;
      res.indentWithTabs = proto.hasIndentWithTabs() ? proto.getIndentWithTabs() : null;
      res.autoCloseBrackets = proto.hasAutoCloseBrackets() ? proto.getAutoCloseBrackets() : null;
      res.showBase = proto.hasShowBase() ? proto.getShowBase() : null;
      return res;
    }

    @Override
    public Parser<UserPreferences.EditPreferencesInfo> getParser() {
      return UserPreferences.EditPreferencesInfo.parser();
    }

    @Override
    public Class<UserPreferences.EditPreferencesInfo> getProtoClass() {
      return UserPreferences.EditPreferencesInfo.class;
    }

    @Override
    public Class<EditPreferencesInfo> getEntityClass() {
      return EditPreferencesInfo.class;
    }
  }

  private static <ValueT, BuilderT extends Message.Builder> BuilderT setIfNotNull(
      BuilderT builder, Function<ValueT, BuilderT> protoFieldSetterFn, ValueT javaField) {
    if (javaField != null) {
      return protoFieldSetterFn.apply(javaField);
    }
    return builder;
  }

  private static <
          JavaEnumT extends Enum<?>,
          ProtoEnumT extends ProtocolMessageEnum,
          BuilderT extends Message.Builder>
      BuilderT setEnumIfNotNull(
          BuilderT builder,
          Function<ProtoEnumT, BuilderT> protoFieldSetterFn,
          Function<String, ProtoEnumT> protoEnumFromNameFn,
          JavaEnumT javaEnum) {
    if (javaEnum != null) {
      return protoFieldSetterFn.apply(protoEnumFromNameFn.apply(javaEnum.name()));
    }
    return builder;
  }

  private UserPreferencesConverter() {}
}
