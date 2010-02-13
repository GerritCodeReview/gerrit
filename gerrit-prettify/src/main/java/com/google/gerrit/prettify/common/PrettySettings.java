// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.prettify.common;

/** Settings to configure a {@link PrettyFormatter}. */
public class PrettySettings {
  protected String fileName;
  protected boolean showWhiteSpaceErrors;
  protected int lineLength;
  protected int tabSize;
  protected boolean showTabs;
  protected boolean syntaxHighlighting;

  public PrettySettings() {
    showWhiteSpaceErrors = true;
    lineLength = 100;
    tabSize = 2;
    showTabs = true;
    syntaxHighlighting = true;
  }

  public PrettySettings(PrettySettings pretty) {
    fileName = pretty.fileName;
    showWhiteSpaceErrors = pretty.showWhiteSpaceErrors;
    lineLength = pretty.lineLength;
    tabSize = pretty.tabSize;
    showTabs = pretty.showTabs;
    syntaxHighlighting = pretty.syntaxHighlighting;
  }

  public String getFilename() {
    return fileName;
  }

  public PrettySettings setFileName(final String name) {
    fileName = name;
    return this;
  }

  public boolean isShowWhiteSpaceErrors() {
    return showWhiteSpaceErrors;
  }

  public PrettySettings setShowWhiteSpaceErrors(final boolean show) {
    showWhiteSpaceErrors = show;
    return this;
  }

  public int getLineLength() {
    return lineLength;
  }

  public PrettySettings setLineLength(final int len) {
    lineLength = len;
    return this;
  }

  public int getTabSize() {
    return tabSize;
  }

  public PrettySettings setTabSize(final int len) {
    tabSize = len;
    return this;
  }

  public boolean isShowTabs() {
    return showTabs;
  }

  public PrettySettings setShowTabs(final boolean show) {
    showTabs = show;
    return this;
  }

  public boolean isSyntaxHighlighting() {
    return syntaxHighlighting;
  }

  public void setSyntaxHighlighting(final boolean on) {
    syntaxHighlighting = on;
  }
}
