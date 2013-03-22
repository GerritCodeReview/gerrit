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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;

/** Diff formatting preferences of an account */
public class AccountDiffPreference {

  /** Default number of lines of context. */
  public static final short DEFAULT_CONTEXT = 10;

  /** Context setting to display the entire file. */
  public static final short WHOLE_FILE_CONTEXT = -1;

  /** Typical valid choices for the default context setting. */
  public static final short[] CONTEXT_CHOICES =
      {3, 10, 25, 50, 75, 100, WHOLE_FILE_CONTEXT};

  public static final Whitespace DEFAULT_IGNORE_WHITESPACE =
      Whitespace.IGNORE_NONE;

  public static enum Whitespace implements CodedEnum {
    IGNORE_NONE('N'), //
    IGNORE_SPACE_AT_EOL('E'), //
    IGNORE_SPACE_CHANGE('S'), //
    IGNORE_ALL_SPACE('A');

    private final char code;

    private Whitespace(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static Whitespace forCode(final char c) {
      for (final Whitespace s : Whitespace.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  public static AccountDiffPreference createDefault(Account.Id accountId) {
    AccountDiffPreference p = new AccountDiffPreference(accountId);
    p.setIgnoreWhitespace(DEFAULT_IGNORE_WHITESPACE);
    p.setTabSize(8);
    p.setLineLength(100);
    p.setSyntaxHighlighting(true);
    p.setShowWhitespaceErrors(true);
    p.setShowLineEndings(true);
    p.setIntralineDifference(true);
    p.setShowTabs(true);
    p.setContext(DEFAULT_CONTEXT);
    p.setManualReview(false);
    return p;
  }

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

  /** Number of lines of context when viewing a patch. */
  @Column(id = 9)
  protected short context;

  @Column(id = 10)
  protected boolean skipDeleted;

  @Column(id = 11)
  protected boolean skipUncommented;

  @Column(id = 12)
  protected boolean expandAllComments;

  @Column(id = 13)
  protected boolean retainHeader;

  @Column(id = 14)
  protected boolean manualReview;

  @Column(id = 15)
  protected boolean showLineEndings;

  protected AccountDiffPreference() {
  }

  public AccountDiffPreference(Account.Id accountId) {
    this.accountId = accountId;
  }

  public AccountDiffPreference(AccountDiffPreference p) {
    this.accountId = p.accountId;
    this.ignoreWhitespace = p.ignoreWhitespace;
    this.tabSize = p.tabSize;
    this.lineLength = p.lineLength;
    this.syntaxHighlighting = p.syntaxHighlighting;
    this.showWhitespaceErrors = p.showWhitespaceErrors;
    this.showLineEndings = p.showLineEndings;
    this.intralineDifference = p.intralineDifference;
    this.showTabs = p.showTabs;
    this.skipDeleted = p.skipDeleted;
    this.skipUncommented = p.skipUncommented;
    this.expandAllComments = p.expandAllComments;
    this.context = p.context;
    this.retainHeader = p.retainHeader;
    this.manualReview = p.manualReview;
  }

  public Account.Id getAccountId() {
    return accountId;
  }

  public Whitespace getIgnoreWhitespace() {
    return Whitespace.forCode(ignoreWhitespace);
  }

  public void setIgnoreWhitespace(Whitespace ignoreWhitespace) {
    this.ignoreWhitespace = ignoreWhitespace.getCode();
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

  public boolean isShowLineEndings() {
    return showLineEndings;
  }

  public void setShowLineEndings(boolean showLineEndings) {
    this.showLineEndings = showLineEndings;
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

  /** Get the number of lines of context when viewing a patch. */
  public short getContext() {
    return context;
  }

  /** Set the number of lines of context when viewing a patch. */
  public void setContext(final short context) {
    assert 0 <= context || context == WHOLE_FILE_CONTEXT;
    this.context = context;
  }

  public boolean isSkipDeleted() {
    return skipDeleted;
  }

  public void setSkipDeleted(boolean skip) {
    skipDeleted = skip;
  }

  public boolean isSkipUncommented() {
    return skipUncommented;
  }

  public void setSkipUncommented(boolean skip) {
    skipUncommented = skip;
  }

  public boolean isExpandAllComments() {
    return expandAllComments;
  }

  public void setExpandAllComments(boolean expand) {
    expandAllComments = expand;
  }

  public boolean isRetainHeader() {
    return retainHeader;
  }

  public void setRetainHeader(boolean retain) {
    retainHeader = retain;
  }

  public boolean isManualReview() {
    return manualReview;
  }

  public void setManualReview(boolean manual) {
    manualReview = manual;
  }
}
