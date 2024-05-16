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

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.ConvertibleToProto;
import java.util.Objects;

/* This class is stored in Git config file. */
@ConvertibleToProto
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
    return Objects.equals(this.tabSize, other.tabSize)
        && Objects.equals(this.lineLength, other.lineLength)
        && Objects.equals(this.indentUnit, other.indentUnit)
        && Objects.equals(this.cursorBlinkRate, other.cursorBlinkRate)
        && Objects.equals(this.hideTopMenu, other.hideTopMenu)
        && Objects.equals(this.showTabs, other.showTabs)
        && Objects.equals(this.showWhitespaceErrors, other.showWhitespaceErrors)
        && Objects.equals(this.syntaxHighlighting, other.syntaxHighlighting)
        && Objects.equals(this.hideLineNumbers, other.hideLineNumbers)
        && Objects.equals(this.matchBrackets, other.matchBrackets)
        && Objects.equals(this.lineWrapping, other.lineWrapping)
        && Objects.equals(this.indentWithTabs, other.indentWithTabs)
        && Objects.equals(this.autoCloseBrackets, other.autoCloseBrackets)
        && Objects.equals(this.showBase, other.showBase);
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
    return MoreObjects.toStringHelper("EditPreferencesInfo")
        .add("tabSize", tabSize)
        .add("lineLength", lineLength)
        .add("indentUnit", indentUnit)
        .add("cursorBlinkRate", cursorBlinkRate)
        .add("hideTopMenu", hideTopMenu)
        .add("showTabs", showTabs)
        .add("showWhitespaceErrors", showWhitespaceErrors)
        .add("syntaxHighlighting", syntaxHighlighting)
        .add("hideLineNumbers", hideLineNumbers)
        .add("matchBrackets", matchBrackets)
        .add("lineWrapping", lineWrapping)
        .add("indentWithTabs", indentWithTabs)
        .add("autoCloseBrackets", autoCloseBrackets)
        .add("showBase", showBase)
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
