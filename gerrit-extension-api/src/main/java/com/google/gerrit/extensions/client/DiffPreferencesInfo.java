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

  /** Context setting to display the entire file. */
  public static final short WHOLE_FILE_CONTEXT = -1;

  /** Typical valid choices for the default context setting. */
  public static final short[] CONTEXT_CHOICES =
      {3, 10, 25, 50, 75, 100, WHOLE_FILE_CONTEXT};

  public static enum Whitespace {
    IGNORE_NONE,
    IGNORE_SPACE_AT_EOL,
    IGNORE_SPACE_CHANGE,
    IGNORE_ALL_SPACE;
  }

  public Integer context;
  public Integer tabSize;
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
  public Theme theme;
  public Whitespace ignoreWhitespace;
  public Boolean retainHeader;
  public Boolean skipDeleted;
  public Boolean skipUncommented;
  public Boolean migrated; // Needed for life migration only

  public static DiffPreferencesInfo defaults() {
    DiffPreferencesInfo i = new DiffPreferencesInfo();
    i.context = DEFAULT_CONTEXT;
    i.tabSize = 8;
    i.lineLength = 100;
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
    i.skipUncommented = false;
    i.syntaxHighlighting = true;
    i.hideTopMenu = false;
    i.autoHideDiffTableHeader = true;
    i.hideLineNumbers = false;
    i.renderEntireFile = false;
    i.hideEmptyPane = false;
    i.migrated = false;
    return i;
  }
}