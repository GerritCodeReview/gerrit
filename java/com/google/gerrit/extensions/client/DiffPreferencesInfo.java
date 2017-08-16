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

public class DiffPreferencesInfo {

  /** Default number of lines of context. */
  public static final int DEFAULT_CONTEXT = 10;

  /** Default tab size. */
  public static final int DEFAULT_TAB_SIZE = 8;

  /** Default font size. */
  public static final int DEFAULT_FONT_SIZE = 12;

  /** Default line length. */
  public static final int DEFAULT_LINE_LENGTH = 100;

  /** Context setting to display the entire file. */
  public static final short WHOLE_FILE_CONTEXT = -1;

  /** Typical valid choices for the default context setting. */
  public static final short[] CONTEXT_CHOICES = {3, 10, 25, 50, 75, 100, WHOLE_FILE_CONTEXT};

  public enum Whitespace {
    IGNORE_NONE,
    IGNORE_TRAILING,
    IGNORE_LEADING_AND_TRAILING,
    IGNORE_ALL;
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
  public Theme theme;
  public Whitespace ignoreWhitespace;
  public Boolean retainHeader;
  public Boolean skipDeleted;
  public Boolean skipUnchanged;
  public Boolean skipUncommented;

  public static DiffPreferencesInfo defaults() {
    DiffPreferencesInfo i = new DiffPreferencesInfo();
    i.context = DEFAULT_CONTEXT;
    i.tabSize = DEFAULT_TAB_SIZE;
    i.fontSize = DEFAULT_FONT_SIZE;
    i.lineLength = DEFAULT_LINE_LENGTH;
    i.cursorBlinkRate = 0;
    i.ignoreWhitespace = Whitespace.IGNORE_NONE;
    i.theme = Theme.DEFAULT;
    i.expandAllComments = false;
    i.intralineDifference = true;
    i.manualReview = false;
    i.retainHeader = false;
    i.showLineEndings = true;
    i.showTabs = true;
    i.showWhitespaceErrors = true;
    i.skipDeleted = false;
    i.skipUnchanged = false;
    i.skipUncommented = false;
    i.syntaxHighlighting = true;
    i.hideTopMenu = false;
    i.autoHideDiffTableHeader = true;
    i.hideLineNumbers = false;
    i.renderEntireFile = false;
    i.hideEmptyPane = false;
    i.matchBrackets = false;
    i.lineWrapping = false;
    return i;
  }
}
