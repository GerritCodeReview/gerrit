// Copyright (C) 2019 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.KeyMapType;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.client.Theme;
import java.util.Optional;

@AutoValue
public abstract class Preferences {
  @AutoValue
  public abstract static class General {
    public abstract Optional<Integer> changesPerPage();

    public abstract Optional<String> downloadScheme();

    public abstract Optional<DownloadCommand> downloadCommand();

    public abstract Optional<DateFormat> dateFormat();

    public abstract Optional<TimeFormat> timeFormat();

    public abstract Optional<Boolean> expandInlineDiffs();

    public abstract Optional<Boolean> highlightAssigneeInChangeTable();

    public abstract Optional<Boolean> relativeDateInChangeTable();

    public abstract Optional<DiffView> diffView();

    public abstract Optional<Boolean> sizeBarInChangeTable();

    public abstract Optional<Boolean> legacycidInChangeTable();

    public abstract Optional<Boolean> muteCommonPathPrefixes();

    public abstract Optional<Boolean> signedOffBy();

    public abstract Optional<EmailStrategy> emailStrategy();

    public abstract Optional<EmailFormat> emailFormat();

    public abstract Optional<DefaultBase> defaultBaseForMerges();

    public abstract Optional<Boolean> publishCommentsOnPush();

    public abstract Optional<Boolean> workInProgressByDefault();

    public abstract Optional<ImmutableList<MenuItem>> my();

    public abstract Optional<ImmutableList<String>> changeTable();

    public abstract Optional<ImmutableMap<String, String>> urlAliases();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder changesPerPage(Optional<Integer> val);

      abstract Builder downloadScheme(Optional<String> val);

      abstract Builder downloadCommand(Optional<DownloadCommand> val);

      abstract Builder dateFormat(Optional<DateFormat> val);

      abstract Builder timeFormat(Optional<TimeFormat> val);

      abstract Builder expandInlineDiffs(Optional<Boolean> val);

      abstract Builder highlightAssigneeInChangeTable(Optional<Boolean> val);

      abstract Builder relativeDateInChangeTable(Optional<Boolean> val);

      abstract Builder diffView(Optional<DiffView> val);

      abstract Builder sizeBarInChangeTable(Optional<Boolean> val);

      abstract Builder legacycidInChangeTable(Optional<Boolean> val);

      abstract Builder muteCommonPathPrefixes(Optional<Boolean> val);

      abstract Builder signedOffBy(Optional<Boolean> val);

      abstract Builder emailStrategy(Optional<EmailStrategy> val);

      abstract Builder emailFormat(Optional<EmailFormat> val);

      abstract Builder defaultBaseForMerges(Optional<DefaultBase> val);

      abstract Builder publishCommentsOnPush(Optional<Boolean> val);

      abstract Builder workInProgressByDefault(Optional<Boolean> val);

      abstract Builder my(Optional<ImmutableList<MenuItem>> val);

      abstract Builder changeTable(Optional<ImmutableList<String>> val);

      abstract Builder urlAliases(Optional<ImmutableMap<String, String>> val);

      abstract General build();
    }

    public static General fromInfo(GeneralPreferencesInfo info) {
      return (new AutoValue_Preferences_General.Builder())
          .changesPerPage(Optional.ofNullable(info.changesPerPage))
          .downloadScheme(Optional.ofNullable(info.downloadScheme))
          .downloadCommand(Optional.ofNullable(info.downloadCommand))
          .dateFormat(Optional.ofNullable(info.dateFormat))
          .timeFormat(Optional.ofNullable(info.timeFormat))
          .expandInlineDiffs(Optional.ofNullable(info.expandInlineDiffs))
          .highlightAssigneeInChangeTable(Optional.ofNullable(info.highlightAssigneeInChangeTable))
          .relativeDateInChangeTable(Optional.ofNullable(info.relativeDateInChangeTable))
          .diffView(Optional.ofNullable(info.diffView))
          .sizeBarInChangeTable(Optional.ofNullable(info.sizeBarInChangeTable))
          .legacycidInChangeTable(Optional.ofNullable(info.legacycidInChangeTable))
          .muteCommonPathPrefixes(Optional.ofNullable(info.muteCommonPathPrefixes))
          .signedOffBy(Optional.ofNullable(info.signedOffBy))
          .emailStrategy(Optional.ofNullable(info.emailStrategy))
          .emailFormat(Optional.ofNullable(info.emailFormat))
          .defaultBaseForMerges(Optional.ofNullable(info.defaultBaseForMerges))
          .publishCommentsOnPush(Optional.ofNullable(info.publishCommentsOnPush))
          .workInProgressByDefault(Optional.ofNullable(info.workInProgressByDefault))
          .my(info.my == null ? Optional.empty() : Optional.of(ImmutableList.copyOf(info.my)))
          .changeTable(
              info.changeTable == null
                  ? Optional.empty()
                  : Optional.of(ImmutableList.copyOf(info.changeTable)))
          .urlAliases(
              info.urlAliases == null
                  ? Optional.empty()
                  : Optional.of(ImmutableMap.copyOf(info.urlAliases)))
          .build();
    }

    public GeneralPreferencesInfo toInfo() {
      GeneralPreferencesInfo info = new GeneralPreferencesInfo();
      info.changesPerPage = changesPerPage().orElse(null);
      info.downloadScheme = downloadScheme().orElse(null);
      info.downloadCommand = downloadCommand().orElse(null);
      info.dateFormat = dateFormat().orElse(null);
      info.timeFormat = timeFormat().orElse(null);
      info.expandInlineDiffs = expandInlineDiffs().orElse(null);
      info.highlightAssigneeInChangeTable = highlightAssigneeInChangeTable().orElse(null);
      info.relativeDateInChangeTable = relativeDateInChangeTable().orElse(null);
      info.diffView = diffView().orElse(null);
      info.sizeBarInChangeTable = sizeBarInChangeTable().orElse(null);
      info.legacycidInChangeTable = legacycidInChangeTable().orElse(null);
      info.muteCommonPathPrefixes = muteCommonPathPrefixes().orElse(null);
      info.signedOffBy = signedOffBy().orElse(null);
      info.emailStrategy = emailStrategy().orElse(null);
      info.emailFormat = emailFormat().orElse(null);
      info.defaultBaseForMerges = defaultBaseForMerges().orElse(null);
      info.publishCommentsOnPush = publishCommentsOnPush().orElse(null);
      info.workInProgressByDefault = workInProgressByDefault().orElse(null);
      info.my = my().orElse(null);
      info.changeTable = changeTable().orElse(null);
      info.urlAliases = urlAliases().orElse(null);
      return info;
    }
  }

  @AutoValue
  public abstract static class Edit {
    public abstract Optional<Integer> tabSize();

    public abstract Optional<Integer> lineLength();

    public abstract Optional<Integer> indentUnit();

    public abstract Optional<Integer> cursorBlinkRate();

    public abstract Optional<Boolean> hideTopMenu();

    public abstract Optional<Boolean> showTabs();

    public abstract Optional<Boolean> showWhitespaceErrors();

    public abstract Optional<Boolean> syntaxHighlighting();

    public abstract Optional<Boolean> hideLineNumbers();

    public abstract Optional<Boolean> matchBrackets();

    public abstract Optional<Boolean> lineWrapping();

    public abstract Optional<Boolean> indentWithTabs();

    public abstract Optional<Boolean> autoCloseBrackets();

    public abstract Optional<Boolean> showBase();

    public abstract Optional<Theme> theme();

    public abstract Optional<KeyMapType> keyMapType();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder tabSize(Optional<Integer> val);

      abstract Builder lineLength(Optional<Integer> val);

      abstract Builder indentUnit(Optional<Integer> val);

      abstract Builder cursorBlinkRate(Optional<Integer> val);

      abstract Builder hideTopMenu(Optional<Boolean> val);

      abstract Builder showTabs(Optional<Boolean> val);

      abstract Builder showWhitespaceErrors(Optional<Boolean> val);

      abstract Builder syntaxHighlighting(Optional<Boolean> val);

      abstract Builder hideLineNumbers(Optional<Boolean> val);

      abstract Builder matchBrackets(Optional<Boolean> val);

      abstract Builder lineWrapping(Optional<Boolean> val);

      abstract Builder indentWithTabs(Optional<Boolean> val);

      abstract Builder autoCloseBrackets(Optional<Boolean> val);

      abstract Builder showBase(Optional<Boolean> val);

      abstract Builder theme(Optional<Theme> val);

      abstract Builder keyMapType(Optional<KeyMapType> val);

      abstract Edit build();
    }

    public static Edit fromInfo(EditPreferencesInfo info) {
      return (new AutoValue_Preferences_Edit.Builder())
          .tabSize(Optional.ofNullable(info.tabSize))
          .lineLength(Optional.ofNullable(info.lineLength))
          .indentUnit(Optional.ofNullable(info.indentUnit))
          .cursorBlinkRate(Optional.ofNullable(info.cursorBlinkRate))
          .hideTopMenu(Optional.ofNullable(info.hideTopMenu))
          .showTabs(Optional.ofNullable(info.showTabs))
          .showWhitespaceErrors(Optional.ofNullable(info.showWhitespaceErrors))
          .syntaxHighlighting(Optional.ofNullable(info.syntaxHighlighting))
          .hideLineNumbers(Optional.ofNullable(info.hideLineNumbers))
          .matchBrackets(Optional.ofNullable(info.matchBrackets))
          .lineWrapping(Optional.ofNullable(info.lineWrapping))
          .indentWithTabs(Optional.ofNullable(info.indentWithTabs))
          .autoCloseBrackets(Optional.ofNullable(info.autoCloseBrackets))
          .showBase(Optional.ofNullable(info.showBase))
          .theme(Optional.ofNullable(info.theme))
          .keyMapType(Optional.ofNullable(info.keyMapType))
          .build();
    }

    public EditPreferencesInfo toInfo() {
      EditPreferencesInfo info = new EditPreferencesInfo();
      info.tabSize = tabSize().orElse(null);
      info.lineLength = lineLength().orElse(null);
      info.indentUnit = indentUnit().orElse(null);
      info.cursorBlinkRate = cursorBlinkRate().orElse(null);
      info.hideTopMenu = hideTopMenu().orElse(null);
      info.showTabs = showTabs().orElse(null);
      info.showWhitespaceErrors = showWhitespaceErrors().orElse(null);
      info.syntaxHighlighting = syntaxHighlighting().orElse(null);
      info.hideLineNumbers = hideLineNumbers().orElse(null);
      info.matchBrackets = matchBrackets().orElse(null);
      info.lineWrapping = lineWrapping().orElse(null);
      info.indentWithTabs = indentWithTabs().orElse(null);
      info.autoCloseBrackets = autoCloseBrackets().orElse(null);
      info.showBase = showBase().orElse(null);
      info.theme = theme().orElse(null);
      info.keyMapType = keyMapType().orElse(null);
      return info;
    }
  }

  @AutoValue
  public abstract static class Diff {
    public abstract Optional<Integer> context();

    public abstract Optional<Integer> tabSize();

    public abstract Optional<Integer> fontSize();

    public abstract Optional<Integer> lineLength();

    public abstract Optional<Integer> cursorBlinkRate();

    public abstract Optional<Boolean> expandAllComments();

    public abstract Optional<Boolean> intralineDifference();

    public abstract Optional<Boolean> manualReview();

    public abstract Optional<Boolean> showLineEndings();

    public abstract Optional<Boolean> showTabs();

    public abstract Optional<Boolean> showWhitespaceErrors();

    public abstract Optional<Boolean> syntaxHighlighting();

    public abstract Optional<Boolean> hideTopMenu();

    public abstract Optional<Boolean> autoHideDiffTableHeader();

    public abstract Optional<Boolean> hideLineNumbers();

    public abstract Optional<Boolean> renderEntireFile();

    public abstract Optional<Boolean> hideEmptyPane();

    public abstract Optional<Boolean> matchBrackets();

    public abstract Optional<Boolean> lineWrapping();

    public abstract Optional<Theme> theme();

    public abstract Optional<Whitespace> ignoreWhitespace();

    public abstract Optional<Boolean> retainHeader();

    public abstract Optional<Boolean> skipDeleted();

    public abstract Optional<Boolean> skipUnchanged();

    public abstract Optional<Boolean> skipUncommented();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder context(Optional<Integer> val);

      abstract Builder tabSize(Optional<Integer> val);

      abstract Builder fontSize(Optional<Integer> val);

      abstract Builder lineLength(Optional<Integer> val);

      abstract Builder cursorBlinkRate(Optional<Integer> val);

      abstract Builder expandAllComments(Optional<Boolean> val);

      abstract Builder intralineDifference(Optional<Boolean> val);

      abstract Builder manualReview(Optional<Boolean> val);

      abstract Builder showLineEndings(Optional<Boolean> val);

      abstract Builder showTabs(Optional<Boolean> val);

      abstract Builder showWhitespaceErrors(Optional<Boolean> val);

      abstract Builder syntaxHighlighting(Optional<Boolean> val);

      abstract Builder hideTopMenu(Optional<Boolean> val);

      abstract Builder autoHideDiffTableHeader(Optional<Boolean> val);

      abstract Builder hideLineNumbers(Optional<Boolean> val);

      abstract Builder renderEntireFile(Optional<Boolean> val);

      abstract Builder hideEmptyPane(Optional<Boolean> val);

      abstract Builder matchBrackets(Optional<Boolean> val);

      abstract Builder lineWrapping(Optional<Boolean> val);

      abstract Builder theme(Optional<Theme> val);

      abstract Builder ignoreWhitespace(Optional<Whitespace> val);

      abstract Builder retainHeader(Optional<Boolean> val);

      abstract Builder skipDeleted(Optional<Boolean> val);

      abstract Builder skipUnchanged(Optional<Boolean> val);

      abstract Builder skipUncommented(Optional<Boolean> val);

      abstract Diff build();
    }

    public static Diff fromInfo(DiffPreferencesInfo info) {
      return (new AutoValue_Preferences_Diff.Builder())
          .context(Optional.ofNullable(info.context))
          .tabSize(Optional.ofNullable(info.tabSize))
          .fontSize(Optional.ofNullable(info.fontSize))
          .lineLength(Optional.ofNullable(info.lineLength))
          .cursorBlinkRate(Optional.ofNullable(info.cursorBlinkRate))
          .expandAllComments(Optional.ofNullable(info.expandAllComments))
          .intralineDifference(Optional.ofNullable(info.intralineDifference))
          .manualReview(Optional.ofNullable(info.manualReview))
          .showLineEndings(Optional.ofNullable(info.showLineEndings))
          .showTabs(Optional.ofNullable(info.showTabs))
          .showWhitespaceErrors(Optional.ofNullable(info.showWhitespaceErrors))
          .syntaxHighlighting(Optional.ofNullable(info.syntaxHighlighting))
          .hideTopMenu(Optional.ofNullable(info.hideTopMenu))
          .autoHideDiffTableHeader(Optional.ofNullable(info.autoHideDiffTableHeader))
          .hideLineNumbers(Optional.ofNullable(info.hideLineNumbers))
          .renderEntireFile(Optional.ofNullable(info.renderEntireFile))
          .hideEmptyPane(Optional.ofNullable(info.hideEmptyPane))
          .matchBrackets(Optional.ofNullable(info.matchBrackets))
          .lineWrapping(Optional.ofNullable(info.lineWrapping))
          .theme(Optional.ofNullable(info.theme))
          .ignoreWhitespace(Optional.ofNullable(info.ignoreWhitespace))
          .retainHeader(Optional.ofNullable(info.retainHeader))
          .skipDeleted(Optional.ofNullable(info.skipDeleted))
          .skipUnchanged(Optional.ofNullable(info.skipUnchanged))
          .skipUncommented(Optional.ofNullable(info.skipUncommented))
          .build();
    }

    public DiffPreferencesInfo toInfo() {
      DiffPreferencesInfo info = new DiffPreferencesInfo();
      info.context = context().orElse(null);
      info.tabSize = tabSize().orElse(null);
      info.fontSize = fontSize().orElse(null);
      info.lineLength = lineLength().orElse(null);
      info.cursorBlinkRate = cursorBlinkRate().orElse(null);
      info.expandAllComments = expandAllComments().orElse(null);
      info.intralineDifference = intralineDifference().orElse(null);
      info.manualReview = manualReview().orElse(null);
      info.showLineEndings = showLineEndings().orElse(null);
      info.showTabs = showTabs().orElse(null);
      info.showWhitespaceErrors = showWhitespaceErrors().orElse(null);
      info.syntaxHighlighting = syntaxHighlighting().orElse(null);
      info.hideTopMenu = hideTopMenu().orElse(null);
      info.autoHideDiffTableHeader = autoHideDiffTableHeader().orElse(null);
      info.hideLineNumbers = hideLineNumbers().orElse(null);
      info.renderEntireFile = renderEntireFile().orElse(null);
      info.hideEmptyPane = hideEmptyPane().orElse(null);
      info.matchBrackets = matchBrackets().orElse(null);
      info.lineWrapping = lineWrapping().orElse(null);
      info.theme = theme().orElse(null);
      info.ignoreWhitespace = ignoreWhitespace().orElse(null);
      info.retainHeader = retainHeader().orElse(null);
      info.skipDeleted = skipDeleted().orElse(null);
      info.skipUnchanged = skipUnchanged().orElse(null);
      info.skipUncommented = skipUncommented().orElse(null);
      return info;
    }
  }
}
