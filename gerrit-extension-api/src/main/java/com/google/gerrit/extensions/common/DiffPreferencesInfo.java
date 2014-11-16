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

package com.google.gerrit.extensions.common;

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

  public int context;
  public int tabSize;
  public int lineLength;
  public boolean expandAllComments;
  public boolean intralineDifference;
  public boolean manualReview;
  public boolean showLineEndings;
  public boolean showTabs;
  public boolean showWhitespaceErrors;
  public boolean syntaxHighlighting;
  public boolean hideTopMenu;
  public boolean autoHideDiffTableHeader;
  public boolean hideLineNumbers;
  public boolean renderEntireFile;
  public boolean hideEmptyPane;
  public Theme theme;
  public Whitespace ignoreWhitespace;

  // These three attribuates seem not to be used in CS2?
  public boolean retainHeader;
  public boolean skipDeleted;
  public boolean skipUncommented;

  public DiffPreferencesInfo() {
    context = DEFAULT_CONTEXT;
    tabSize = 8;
    lineLength = 100;
    ignoreWhitespace = Whitespace.IGNORE_NONE;
    theme = Theme.DEFAULT;
    expandAllComments = false;
    intralineDifference = true;
    manualReview = false;
    retainHeader = false;
    showLineEndings = true;
    showTabs = true;
    showWhitespaceErrors = true;
    skipDeleted = false;
    skipUncommented = false;
    syntaxHighlighting = true;
    hideTopMenu = false;
    autoHideDiffTableHeader = true;
    hideLineNumbers = false;
    renderEntireFile = false;
    hideEmptyPane = false;
  }
}