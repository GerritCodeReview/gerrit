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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;

/** Diff formatting preferences of an account */
public class AccountDiffPreference {

  @Column(id = 1, name = Column.NONE)
  protected Account.Id accountId;

  @Column(id = 2)
  protected char ignoreWhitespace;

  @Column(id = 3)
  protected int tabSize;

  @Column(id = 4)
  protected int lineLength;

  @Column(id = 5)
  protected boolean syntaxHighlighting;

  @Column(id = 6)
  protected boolean showWhitespaceErrors;

  @Column(id = 7)
  protected boolean intralineDifference;

  @Column(id = 8)
  protected boolean showTabs;


  public AccountDiffPreference() {
  }

  public Account.Id getAccountId() {
    return accountId;
  }

  public void setAccountId(Account.Id accountId) {
    this.accountId = accountId;
  }

  public char getIgnoreWhitespace() {
    return ignoreWhitespace;
  }

  public void setIgnoreWhitespace(char ignoreWhitespace) {
    this.ignoreWhitespace = ignoreWhitespace;
  }

  public int getTabSize() {
    return tabSize;
  }

  public void setTabSize(int tabSize) {
    this.tabSize = tabSize;
  }

  public int getLineLength() {
    return lineLength;
  }

  public void setLineLength(int lineLength) {
    this.lineLength = lineLength;
  }

  public boolean isSyntaxHighlighting() {
    return syntaxHighlighting;
  }

  public void setSyntaxHighlighting(boolean syntaxHighlighting) {
    this.syntaxHighlighting = syntaxHighlighting;
  }

  public boolean isShowWhitespaceErrors() {
    return showWhitespaceErrors;
  }

  public void setShowWhitespaceErrors(boolean showWhitespaceErrors) {
    this.showWhitespaceErrors = showWhitespaceErrors;
  }

  public boolean isIntralineDifference() {
    return intralineDifference;
  }

  public void setIntralineDifference(boolean intralineDifference) {
    this.intralineDifference = intralineDifference;
  }

  public boolean isShowTabs() {
    return showTabs;
  }

  public void setShowTabs(boolean showTabs) {
    this.showTabs = showTabs;
  }
}
