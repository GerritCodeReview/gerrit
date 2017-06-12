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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;
import com.google.gwtorm.client.StringKey;

/** An {@link Account} interested in a {@link Project}. */
public final class AccountProjectWatch {

  public enum NotifyType {
    // sort by name, except 'ALL' which should stay last
    ABANDONED_CHANGES,
    ALL_COMMENTS,
    NEW_CHANGES,
    NEW_PATCHSETS,
    SUBMITTED_CHANGES,

    ALL
  }

  public static final String FILTER_ALL = "*";

  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Account.Id accountId;

    @Column(id = 2)
    protected Project.NameKey projectName;

    @Column(id = 3)
    protected Filter filter;

    protected Key() {
      accountId = new Account.Id();
      projectName = new Project.NameKey();
      filter = new Filter();
    }

    public Key(Account.Id a, Project.NameKey g, String f) {
      accountId = a;
      projectName = g;
      filter = new Filter(f);
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    public Project.NameKey getProjectName() {
      return projectName;
    }

    public Filter getFilter() {
      return filter;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {projectName, filter};
    }
  }

  public static class Filter extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String filter;

    protected Filter() {}

    public Filter(String f) {
      filter = f != null && !f.isEmpty() ? f : FILTER_ALL;
    }

    @Override
    public String get() {
      return filter;
    }

    @Override
    protected void set(String newValue) {
      filter = newValue;
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

  @Column(id = 5)
  protected boolean notifyNewPatchSets;

  @Column(id = 6)
  protected boolean notifyAbandonedChanges;

  protected AccountProjectWatch() {}

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

  public String getFilter() {
    return FILTER_ALL.equals(key.filter.get()) ? null : key.filter.get();
  }

  public boolean isNotify(final NotifyType type) {
    switch (type) {
      case NEW_CHANGES:
        return notifyNewChanges;

      case NEW_PATCHSETS:
        return notifyNewPatchSets;

      case ALL_COMMENTS:
        return notifyAllComments;

      case SUBMITTED_CHANGES:
        return notifySubmittedChanges;

      case ABANDONED_CHANGES:
        return notifyAbandonedChanges;

      case ALL:
        break;
    }
    return false;
  }

  public void setNotify(final NotifyType type, final boolean v) {
    switch (type) {
      case NEW_CHANGES:
        notifyNewChanges = v;
        break;

      case NEW_PATCHSETS:
        notifyNewPatchSets = v;
        break;

      case ALL_COMMENTS:
        notifyAllComments = v;
        break;

      case SUBMITTED_CHANGES:
        notifySubmittedChanges = v;
        break;

      case ABANDONED_CHANGES:
        notifyAbandonedChanges = v;
        break;

      case ALL:
        notifyNewChanges = v;
        notifyNewPatchSets = v;
        notifyAllComments = v;
        notifySubmittedChanges = v;
        notifyAbandonedChanges = v;
        break;
    }
  }
}
