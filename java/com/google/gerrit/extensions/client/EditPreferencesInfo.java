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

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof EditPreferencesInfo)) {
      return false;
    }
    EditPreferencesInfo other = (EditPreferencesInfo) obj;
    return this.tabSize.equals(other.tabSize)
        && this.lineLength.equals(other.lineLength)
        && this.indentUnit.equals(other.indentUnit)
        && this.cursorBlinkRate.equals(other.cursorBlinkRate)
        && this.hideTopMenu.equals(other.hideTopMenu)
        && this.showTabs.equals(other.showTabs)
        && this.showWhitespaceErrors.equals(other.showWhitespaceErrors)
        && this.syntaxHighlighting.equals(other.syntaxHighlighting)
        && this.hideLineNumbers.equals(other.hideLineNumbers)
        && this.matchBrackets.equals(other.matchBrackets)
        && this.lineWrapping.equals(other.lineWrapping)
        && this.indentWithTabs.equals(other.indentWithTabs)
        && this.autoCloseBrackets.equals(other.autoCloseBrackets)
        && this.showBase.equals(other.showBase);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        tabSize,
        lineLength,
        indentUnit,
        cursorBlinkRate,
        hideTopMenu,
        showTabs,
        showWhitespaceErrors,
        syntaxHighlighting,
        hideLineNumbers,
        matchBrackets,
        lineWrapping,
        indentWithTabs,
        autoCloseBrackets,
        showBase);
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append("EditPreferencesInfo{")
        .append("tabSize=")
        .append(tabSize)
        .append(',')
        .append("lineLength=")
        .append(lineLength)
        .append(',')
        .append("indentUnit=")
        .append(indentUnit)
        .append(',')
        .append("cursorBlinkRate=")
        .append(cursorBlinkRate)
        .append(',')
        .append("hideTopMenu=")
        .append(hideTopMenu)
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
        .append("hideLineNumbers=")
        .append(hideLineNumbers)
        .append(',')
        .append("matchBrackets=")
        .append(matchBrackets)
        .append(',')
        .append("lineWrapping=")
        .append(lineWrapping)
        .append(',')
        .append("indentWithTabs=")
        .append(indentWithTabs)
        .append(',')
        .append("autoCloseBrackets=")
        .append(autoCloseBrackets)
        .append(',')
        .append("showBase=")
        .append(showBase)
        .append('}')
        .toString();
  }

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
    return i;
  }
}
