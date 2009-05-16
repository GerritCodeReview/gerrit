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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;

/** An {@link Account} interested in a {@link Project}. */
public final class AccountProjectWatch {
  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column
    protected Account.Id accountId;

    @Column
    protected Project.Id projectId;

    protected Key() {
      accountId = new Account.Id();
      projectId = new Project.Id();
    }

    public Key(final Account.Id a, final Project.Id g) {
      accountId = a;
      projectId = g;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {projectId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  /** Automatically send email notifications of new changes? */
  @Column
  protected boolean notifyNewChanges;

  /** Automatically receive comments published to this project */
  @Column
  protected boolean notifyAllComments;

  /** Automatically receive changes submitted to this project */
  @Column
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

  public Project.Id getProjectId() {
    return key.projectId;
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
