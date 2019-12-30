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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.KeyMapType;
import com.google.gerrit.extensions.client.MenuItem;
import java.util.Optional;

@AutoValue
public abstract class Preferences {
  @AutoValue
  public abstract static class General {
    public abstract Optional<Integer> changesPerPage();

    public abstract Optional<String> downloadScheme();

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

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder changesPerPage(@Nullable Integer val);

      abstract Builder downloadScheme(@Nullable String val);

      abstract Builder dateFormat(@Nullable DateFormat val);

      abstract Builder timeFormat(@Nullable TimeFormat val);

      abstract Builder expandInlineDiffs(@Nullable Boolean val);

      abstract Builder highlightAssigneeInChangeTable(@Nullable Boolean val);

      abstract Builder relativeDateInChangeTable(@Nullable Boolean val);

      abstract Builder diffView(@Nullable DiffView val);

      abstract Builder sizeBarInChangeTable(@Nullable Boolean val);

      abstract Builder legacycidInChangeTable(@Nullable Boolean val);

      abstract Builder muteCommonPathPrefixes(@Nullable Boolean val);

      abstract Builder signedOffBy(@Nullable Boolean val);

      abstract Builder emailStrategy(@Nullable EmailStrategy val);

      abstract Builder emailFormat(@Nullable EmailFormat val);

      abstract Builder defaultBaseForMerges(@Nullable DefaultBase val);

      abstract Builder publishCommentsOnPush(@Nullable Boolean val);

      abstract Builder workInProgressByDefault(@Nullable Boolean val);

      abstract Builder my(@Nullable ImmutableList<MenuItem> val);

      abstract Builder changeTable(@Nullable ImmutableList<String> val);

      abstract General build();
    }

    public static General fromInfo(GeneralPreferencesInfo info) {
      return (new AutoValue_Preferences_General.Builder())
          .changesPerPage(info.changesPerPage)
          .downloadScheme(info.downloadScheme)
          .dateFormat(info.dateFormat)
          .timeFormat(info.timeFormat)
          .expandInlineDiffs(info.expandInlineDiffs)
          .highlightAssigneeInChangeTable(info.highlightAssigneeInChangeTable)
          .relativeDateInChangeTable(info.relativeDateInChangeTable)
          .diffView(info.diffView)
          .sizeBarInChangeTable(info.sizeBarInChangeTable)
          .legacycidInChangeTable(info.legacycidInChangeTable)
          .muteCommonPathPrefixes(info.muteCommonPathPrefixes)
          .signedOffBy(info.signedOffBy)
          .emailStrategy(info.emailStrategy)
          .emailFormat(info.emailFormat)
          .defaultBaseForMerges(info.defaultBaseForMerges)
          .publishCommentsOnPush(info.publishCommentsOnPush)
          .workInProgressByDefault(info.workInProgressByDefault)
          .my(info.my == null ? null : ImmutableList.copyOf(info.my))
          .changeTable(info.changeTable == null ? null : ImmutableList.copyOf(info.changeTable))
          .build();
    }

    public GeneralPreferencesInfo toInfo() {
      GeneralPreferencesInfo info = new GeneralPreferencesInfo();
      info.changesPerPage = changesPerPage().orElse(null);
      info.downloadScheme = downloadScheme().orElse(null);
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

    public abstract Optional<KeyMapType> keyMapType();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder tabSize(@Nullable Integer val);

      abstract Builder lineLength(@Nullable Integer val);

      abstract Builder indentUnit(@Nullable Integer val);

      abstract Builder cursorBlinkRate(@Nullable Integer val);

      abstract Builder hideTopMenu(@Nullable Boolean val);

      abstract Builder showTabs(@Nullable Boolean val);

      abstract Builder showWhitespaceErrors(@Nullable Boolean val);

      abstract Builder syntaxHighlighting(@Nullable Boolean val);

      abstract Builder hideLineNumbers(@Nullable Boolean val);

      abstract Builder matchBrackets(@Nullable Boolean val);

      abstract Builder lineWrapping(@Nullable Boolean val);

      abstract Builder indentWithTabs(@Nullable Boolean val);

      abstract Builder autoCloseBrackets(@Nullable Boolean val);

      abstract Builder showBase(@Nullable Boolean val);

      abstract Builder keyMapType(@Nullable KeyMapType val);

      abstract Edit build();
    }

    public static Edit fromInfo(EditPreferencesInfo info) {
      return (new AutoValue_Preferences_Edit.Builder())
          .tabSize(info.tabSize)
          .lineLength(info.lineLength)
          .indentUnit(info.indentUnit)
          .cursorBlinkRate(info.cursorBlinkRate)
          .hideTopMenu(info.hideTopMenu)
          .showTabs(info.showTabs)
          .showWhitespaceErrors(info.showWhitespaceErrors)
          .syntaxHighlighting(info.syntaxHighlighting)
          .hideLineNumbers(info.hideLineNumbers)
          .matchBrackets(info.matchBrackets)
          .lineWrapping(info.lineWrapping)
          .indentWithTabs(info.indentWithTabs)
          .autoCloseBrackets(info.autoCloseBrackets)
          .showBase(info.showBase)
          .keyMapType(info.keyMapType)
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

    public abstract Optional<Whitespace> ignoreWhitespace();

    public abstract Optional<Boolean> retainHeader();

    public abstract Optional<Boolean> skipDeleted();

    public abstract Optional<Boolean> skipUnchanged();

    public abstract Optional<Boolean> skipUncommented();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder context(@Nullable Integer val);

      abstract Builder tabSize(@Nullable Integer val);

      abstract Builder fontSize(@Nullable Integer val);

      abstract Builder lineLength(@Nullable Integer val);

      abstract Builder cursorBlinkRate(@Nullable Integer val);

      abstract Builder expandAllComments(@Nullable Boolean val);

      abstract Builder intralineDifference(@Nullable Boolean val);

      abstract Builder manualReview(@Nullable Boolean val);

      abstract Builder showLineEndings(@Nullable Boolean val);

      abstract Builder showTabs(@Nullable Boolean val);

      abstract Builder showWhitespaceErrors(@Nullable Boolean val);

      abstract Builder syntaxHighlighting(@Nullable Boolean val);

      abstract Builder hideTopMenu(@Nullable Boolean val);

      abstract Builder autoHideDiffTableHeader(@Nullable Boolean val);

      abstract Builder hideLineNumbers(@Nullable Boolean val);

      abstract Builder renderEntireFile(@Nullable Boolean val);

      abstract Builder hideEmptyPane(@Nullable Boolean val);

      abstract Builder matchBrackets(@Nullable Boolean val);

      abstract Builder lineWrapping(@Nullable Boolean val);

      abstract Builder ignoreWhitespace(@Nullable Whitespace val);

      abstract Builder retainHeader(@Nullable Boolean val);

      abstract Builder skipDeleted(@Nullable Boolean val);

      abstract Builder skipUnchanged(@Nullable Boolean val);

      abstract Builder skipUncommented(@Nullable Boolean val);

      abstract Diff build();
    }

    public static Diff fromInfo(DiffPreferencesInfo info) {
      return (new AutoValue_Preferences_Diff.Builder())
          .context(info.context)
          .tabSize(info.tabSize)
          .fontSize(info.fontSize)
          .lineLength(info.lineLength)
          .cursorBlinkRate(info.cursorBlinkRate)
          .expandAllComments(info.expandAllComments)
          .intralineDifference(info.intralineDifference)
          .manualReview(info.manualReview)
          .showLineEndings(info.showLineEndings)
          .showTabs(info.showTabs)
          .showWhitespaceErrors(info.showWhitespaceErrors)
          .syntaxHighlighting(info.syntaxHighlighting)
          .hideTopMenu(info.hideTopMenu)
          .autoHideDiffTableHeader(info.autoHideDiffTableHeader)
          .hideLineNumbers(info.hideLineNumbers)
          .renderEntireFile(info.renderEntireFile)
          .hideEmptyPane(info.hideEmptyPane)
          .matchBrackets(info.matchBrackets)
          .lineWrapping(info.lineWrapping)
          .ignoreWhitespace(info.ignoreWhitespace)
          .retainHeader(info.retainHeader)
          .skipDeleted(info.skipDeleted)
          .skipUnchanged(info.skipUnchanged)
          .skipUncommented(info.skipUncommented)
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
      info.ignoreWhitespace = ignoreWhitespace().orElse(null);
      info.retainHeader = retainHeader().orElse(null);
      info.skipDeleted = skipDeleted().orElse(null);
      info.skipUnchanged = skipUnchanged().orElse(null);
      info.skipUncommented = skipUncommented().orElse(null);
      return info;
    }
  }
}
