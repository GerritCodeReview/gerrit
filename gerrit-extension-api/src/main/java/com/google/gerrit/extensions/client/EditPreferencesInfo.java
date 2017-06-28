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

/* This class is stored in Git config file. */
public class EditPreferencesInfo {
  public Integer tabSize;
  public Integer lineLength;
  public Integer indentUnit;
  public Integer cursorBlinkRate;
  public Boolean hideTopMenu;
  public Boolean showTabs;
  public Boolean showWhitespaceErrors;
  public Boolean syntaxHighlighting;
  public Boolean hideLineNumbers;
  public Boolean matchBrackets;
  public Boolean lineWrapping;
  public Boolean indentWithTabs;
  public Boolean autoCloseBrackets;
  public Boolean showBase;
  public Theme theme;
  public KeyMapType keyMapType;

  public static EditPreferencesInfo defaults() {
    EditPreferencesInfo i = new EditPreferencesInfo();
    i.tabSize = 8;
    i.lineLength = 100;
    i.indentUnit = 2;
    i.cursorBlinkRate = 0;
    i.hideTopMenu = false;
    i.showTabs = true;
    i.showWhitespaceErrors = false;
    i.syntaxHighlighting = true;
    i.hideLineNumbers = false;
    i.matchBrackets = true;
    i.lineWrapping = false;
    i.indentWithTabs = false;
    i.autoCloseBrackets = false;
    i.showBase = false;
    i.theme = Theme.DEFAULT;
    i.keyMapType = KeyMapType.DEFAULT;
    return i;
  }
}
