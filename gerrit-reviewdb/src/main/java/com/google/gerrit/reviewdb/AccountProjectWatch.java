// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gwtorm.client.CompoundKey;
import com.google.gwtorm.client.StringKey;

/** An {@link Account} interested in a {@link Project}. */
public final class AccountProjectWatch {

  public static final String DEFAULT_REGEX = "*";

  /** Project Watch name key */
  public static class RegexKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String regex;

    protected RegexKey() {
    }

    public RegexKey(final String r) {
      regex = r;
    }

    @Override
    public String get() {
      return regex;
    }

    @Override
    protected void set(String newValue) {
      regex = newValue;
    }
  }

  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = "account_id")
    protected Account.Id accountId;

    @Column(id = 2, name = "project_name")
    protected Project.NameKey projectName;

    @Column(id = 3, name = "file_match_regex")
    protected RegexKey fileMatchRegex;

    protected Key() {
      accountId = new Account.Id();
      projectName = new Project.NameKey();
      fileMatchRegex = new RegexKey(DEFAULT_REGEX);
    }

    public Key(final Account.Id a, final Project.NameKey g, String cR) {
      accountId = a;
      projectName = g;

      if (cR == null || cR.isEmpty()) {
        cR = DEFAULT_REGEX;
      }

      fileMatchRegex = new RegexKey(cR);
    }

    public RegexKey getFileMatchRegex() {
      return fileMatchRegex;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {projectName, fileMatchRegex};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  /** Automatically send email notifications of new changes? */
  @Column(id = 2)
  protected boolean notifyNewChanges;

  /** Automatically receive comments published to this project */
  @Column(id = 3)
  protected boolean notifyAllComments;

  /** Automatically receive changes submitted to this project */
  @Column(id = 4)
  protected boolean notifySubmittedChanges;

  protected AccountProjectWatch() {
  }

  public AccountProjectWatch(final AccountProjectWatch.Key k) {
    key = k;
  }

  public AccountProjectWatch.Key getKey() {
    return key;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public Project.NameKey getProjectNameKey() {
    return key.projectName;
  }

  public boolean isNotifyNewChanges() {
    return notifyNewChanges;
  }

  public void setNotifyNewChanges(final boolean a) {
    notifyNewChanges = a;
  }

  public boolean isNotifyAllComments() {
    return notifyAllComments;
  }

  public void setNotifyAllComments(final boolean a) {
    notifyAllComments = a;
  }

  public boolean isNotifySubmittedChanges() {
    return notifySubmittedChanges;
  }

  public void setNotifySubmittedChanges(final boolean a) {
    notifySubmittedChanges = a;
  }
}
