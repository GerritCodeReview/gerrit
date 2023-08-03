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

import java.util.Objects;

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
    return this.context.equals(other.context)
        && this.tabSize.equals(other.tabSize)
        && this.fontSize.equals(other.fontSize)
        && this.lineLength.equals(other.lineLength)
        && this.cursorBlinkRate.equals(other.cursorBlinkRate)
        && this.expandAllComments.equals(other.expandAllComments)
        && this.intralineDifference.equals(other.intralineDifference)
        && this.manualReview.equals(other.manualReview)
        && this.showLineEndings.equals(other.showLineEndings)
        && this.showTabs.equals(other.showTabs)
        && this.showWhitespaceErrors.equals(other.showWhitespaceErrors)
        && this.syntaxHighlighting.equals(other.syntaxHighlighting)
        && this.hideTopMenu.equals(other.hideTopMenu)
        && this.autoHideDiffTableHeader.equals(other.autoHideDiffTableHeader)
        && this.hideLineNumbers.equals(other.hideLineNumbers)
        && this.renderEntireFile.equals(other.renderEntireFile)
        && this.hideEmptyPane.equals(other.hideEmptyPane)
        && this.matchBrackets.equals(other.matchBrackets)
        && this.lineWrapping.equals(other.lineWrapping)
        && this.ignoreWhitespace.equals(other.ignoreWhitespace)
        && this.retainHeader.equals(other.retainHeader)
        && this.skipDeleted.equals(other.skipDeleted)
        && this.skipUnchanged.equals(other.skipUnchanged)
        && this.skipUncommented.equals(other.skipUncommented);
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
    return new StringBuilder()
        .append("DiffPreferencesInfo{")
        .append("context=")
        .append(context)
        .append(',')
        .append("tabSize=")
        .append(tabSize)
        .append(',')
        .append("fontSize=")
        .append(fontSize)
        .append(',')
        .append("lineLength=")
        .append(lineLength)
        .append(',')
        .append("cursorBlinkRate=")
        .append(cursorBlinkRate)
        .append(',')
        .append("expandAllComments=")
        .append(expandAllComments)
        .append(',')
        .append("intralineDifference=")
        .append(intralineDifference)
        .append(',')
        .append("manualReview=")
        .append(manualReview)
        .append(',')
        .append("showLineEndings=")
        .append(showLineEndings)
        .append(',')
        .append("showTabs=")
        .append(showTabs)
        .append(',')
        .append("showWhitespaceErrors=")
        .append(showWhitespaceErrors)
        .append(',')
        .append("syntaxHighlighting=")
        .append(syntaxHighlighting)
        .append(',')
        .append("hideTopMenu=")
        .append(hideTopMenu)
        .append(',')
        .append("autoHideDiffTableHeader=")
        .append(autoHideDiffTableHeader)
        .append(',')
        .append("hideLineNumbers=")
        .append(hideLineNumbers)
        .append(',')
        .append("renderEntireFile=")
        .append(renderEntireFile)
        .append(',')
        .append("hideEmptyPane=")
        .append(hideEmptyPane)
        .append(',')
        .append("matchBrackets=")
        .append(matchBrackets)
        .append(',')
        .append("lineWrapping=")
        .append(lineWrapping)
        .append(',')
        .append("ignoreWhitespace=")
        .append(ignoreWhitespace)
        .append(',')
        .append("retainHeader=")
        .append(retainHeader)
        .append(',')
        .append("skipDeleted=")
        .append(skipDeleted)
        .append(',')
        .append("skipUnchanged=")
        .append(skipUnchanged)
        .append(',')
        .append("skipUncommented=")
        .append(skipUncommented)
        .append('}')
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
