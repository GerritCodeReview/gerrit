// Copyright 2008 Google Inc.
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

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public final class AccountGroupMember {
  public static class Key extends CompoundKey<Account.Id> {
    @Column
    protected Account.Id accountId;

    @Column
    protected AccountGroup.Id groupId;

    protected Key() {
      accountId = new Account.Id();
      groupId = new AccountGroup.Id();
    }

    public Key(final Account.Id a, final AccountGroup.Id g) {
      accountId = a;
      groupId = g;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    public AccountGroup.Id getAccountGroupId() {
      return groupId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {groupId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  protected AccountGroupMember() {
  }

  public AccountGroupMember(final AccountGroupMember.Key k) {
    key = k;
  }

  public AccountGroupMember.Key getKey() {
    return key;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public AccountGroup.Id getAccountGroupId() {
    return key.groupId;
  }
}
