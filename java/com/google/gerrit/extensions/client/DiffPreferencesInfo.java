// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

import static com.google.gerrit.extensions.client.NullableBooleanPreferencesFieldComparator.equalBooleanPreferencesFields;

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.ConvertibleToProto;
import java.util.Objects;

@ConvertibleToProto
public class DiffPreferencesInfo {

  /** Default number of lines of context. */
  public static final int DEFAULT_CONTEXT = 10;

  /** Default tab size. */
  public static final int DEFAULT_TAB_SIZE = 8;

  /** Default font size. */
  public static final int DEFAULT_FONT_SIZE = 12;

  /** Default line length. */
  public static final int DEFAULT_LINE_LENGTH = 100;

  public enum Whitespace {
    IGNORE_NONE,
    IGNORE_TRAILING,
    IGNORE_LEADING_AND_TRAILING,
    IGNORE_ALL
  }

  public Integer context;
  public Integer tabSize;
  public Integer fontSize;
  public Integer lineLength;
  public Integer cursorBlinkRate;
  public Boolean expandAllComments;
  public Boolean intralineDifference;
  public Boolean manualReview;
  public Boolean showLineEndings;
  public Boolean showTabs;
  public Boolean showWhitespaceErrors;
  public Boolean syntaxHighlighting;
  public Boolean hideTopMenu;
  public Boolean autoHideDiffTableHeader;
  public Boolean hideLineNumbers;
  public Boolean renderEntireFile;
  public Boolean hideEmptyPane;
  public Boolean matchBrackets;
  public Boolean lineWrapping;
  public Whitespace ignoreWhitespace;
  public Boolean retainHeader;
  public Boolean skipDeleted;
  public Boolean skipUnchanged;
  public Boolean skipUncommented;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DiffPreferencesInfo)) {
      return false;
    }
    DiffPreferencesInfo other = (DiffPreferencesInfo) obj;
    return Objects.equals(this.context, other.context)
        && Objects.equals(this.tabSize, other.tabSize)
        && Objects.equals(this.fontSize, other.fontSize)
        && Objects.equals(this.lineLength, other.lineLength)
        && Objects.equals(this.cursorBlinkRate, other.cursorBlinkRate)
        && equalBooleanPreferencesFields(this.expandAllComments, other.expandAllComments)
        && equalBooleanPreferencesFields(this.intralineDifference, other.intralineDifference)
        && equalBooleanPreferencesFields(this.manualReview, other.manualReview)
        && equalBooleanPreferencesFields(this.showLineEndings, other.showLineEndings)
        && equalBooleanPreferencesFields(this.showTabs, other.showTabs)
        && equalBooleanPreferencesFields(this.showWhitespaceErrors, other.showWhitespaceErrors)
        && equalBooleanPreferencesFields(this.syntaxHighlighting, other.syntaxHighlighting)
        && equalBooleanPreferencesFields(this.hideTopMenu, other.hideTopMenu)
        && equalBooleanPreferencesFields(
            this.autoHideDiffTableHeader, other.autoHideDiffTableHeader)
        && equalBooleanPreferencesFields(this.hideLineNumbers, other.hideLineNumbers)
        && equalBooleanPreferencesFields(this.renderEntireFile, other.renderEntireFile)
        && equalBooleanPreferencesFields(this.hideEmptyPane, other.hideEmptyPane)
        && equalBooleanPreferencesFields(this.matchBrackets, other.matchBrackets)
        && equalBooleanPreferencesFields(this.lineWrapping, other.lineWrapping)
        && Objects.equals(this.ignoreWhitespace, other.ignoreWhitespace)
        && equalBooleanPreferencesFields(this.retainHeader, other.retainHeader)
        && equalBooleanPreferencesFields(this.skipDeleted, other.skipDeleted)
        && equalBooleanPreferencesFields(this.skipUnchanged, other.skipUnchanged)
        && equalBooleanPreferencesFields(this.skipUncommented, other.skipUncommented);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        context,
        tabSize,
        fontSize,
        lineLength,
        cursorBlinkRate,
        expandAllComments,
        intralineDifference,
        manualReview,
        showLineEndings,
        showTabs,
        showWhitespaceErrors,
        syntaxHighlighting,
        hideTopMenu,
        autoHideDiffTableHeader,
        hideLineNumbers,
        renderEntireFile,
        hideEmptyPane,
        matchBrackets,
        lineWrapping,
        ignoreWhitespace,
        retainHeader,
        skipDeleted,
        skipUnchanged,
        skipUncommented);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("DiffPreferencesInfo")
        .add("context", context)
        .add("tabSize", tabSize)
        .add("fontSize", fontSize)
        .add("lineLength", lineLength)
        .add("cursorBlinkRate", cursorBlinkRate)
        .add("expandAllComments", expandAllComments)
        .add("intralineDifference", intralineDifference)
        .add("manualReview", manualReview)
        .add("showLineEndings", showLineEndings)
        .add("showTabs", showTabs)
        .add("showWhitespaceErrors", showWhitespaceErrors)
        .add("syntaxHighlighting", syntaxHighlighting)
        .add("hideTopMenu", hideTopMenu)
        .add("autoHideDiffTableHeader", autoHideDiffTableHeader)
        .add("hideLineNumbers", hideLineNumbers)
        .add("renderEntireFile", renderEntireFile)
        .add("hideEmptyPane", hideEmptyPane)
        .add("matchBrackets", matchBrackets)
        .add("lineWrapping", lineWrapping)
        .add("ignoreWhitespace", ignoreWhitespace)
        .add("retainHeader", retainHeader)
        .add("skipDeleted", skipDeleted)
        .add("skipUnchanged", skipUnchanged)
        .add("skipUncommented", skipUncommented)
        .toString();
  }

  public static DiffPreferencesInfo defaults() {
    DiffPreferencesInfo i = new DiffPreferencesInfo();
    i.context = DEFAULT_CONTEXT;
    i.tabSize = DEFAULT_TAB_SIZE;
    i.fontSize = DEFAULT_FONT_SIZE;
    i.lineLength = DEFAULT_LINE_LENGTH;
    i.cursorBlinkRate = 0;
    i.expandAllComments = false;
    i.intralineDifference = true;
    i.manualReview = false;
    i.showLineEndings = true;
    i.showTabs = true;
    i.showWhitespaceErrors = true;
    i.syntaxHighlighting = true;
    i.hideTopMenu = false;
    i.autoHideDiffTableHeader = true;
    i.hideLineNumbers = false;
    i.renderEntireFile = false;
    i.hideEmptyPane = false;
    i.matchBrackets = false;
    i.lineWrapping = false;
    i.ignoreWhitespace = Whitespace.IGNORE_NONE;
    i.retainHeader = false;
    i.skipDeleted = false;
    i.skipUnchanged = false;
    i.skipUncommented = false;
    return i;
  }
}
