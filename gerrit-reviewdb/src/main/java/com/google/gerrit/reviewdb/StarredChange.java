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

/** A {@link Change} starred by an {@link Account}. */
public class StarredChange {
  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Account.Id accountId;

    @Column(id = 2)
    protected Change.Id changeId;

    protected Key() {
      accountId = new Account.Id();
      changeId = new Change.Id();
    }

    public Key(final Account.Id a, final Change.Id g) {
      accountId = a;
      changeId = g;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    public Change.Id getChangeId() {
      return changeId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {changeId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  protected StarredChange() {
  }

  public StarredChange(final StarredChange.Key k) {
    key = k;
  }

  public StarredChange.Key getKey() {
    return key;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public Change.Id getChangeId() {
    return key.changeId;
  }
}
