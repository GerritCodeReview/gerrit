// Copyright (C) 2009 The Android Open Source Project
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
import java.sql.Timestamp;

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public final class AccountGroupMemberAudit {
  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Account.Id accountId;

    @Column(id = 2)
    protected AccountGroup.Id groupId;

    @Column(id = 3)
    protected Timestamp addedOn;

    protected Key() {
      accountId = new Account.Id();
      groupId = new AccountGroup.Id();
    }

    public Key(Account.Id a, AccountGroup.Id g, Timestamp t) {
      accountId = a;
      groupId = g;
      addedOn = t;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    public AccountGroup.Id getGroupId() {
      return groupId;
    }

    public Timestamp getAddedOn() {
      return addedOn;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {groupId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected Account.Id addedBy;

  @Column(id = 3, notNull = false)
  protected Account.Id removedBy;

  @Column(id = 4, notNull = false)
  protected Timestamp removedOn;

  protected AccountGroupMemberAudit() {}

  public AccountGroupMemberAudit(final AccountGroupMember m, Account.Id adder, Timestamp addedOn) {
    final Account.Id who = m.getAccountId();
    final AccountGroup.Id group = m.getAccountGroupId();
    key = new AccountGroupMemberAudit.Key(who, group, addedOn);
    addedBy = adder;
  }

  public AccountGroupMemberAudit.Key getKey() {
    return key;
  }

  public boolean isActive() {
    return removedOn == null;
  }

  public void removed(Account.Id deleter, Timestamp when) {
    removedBy = deleter;
    removedOn = when;
  }

  public void removedLegacy() {
    removedBy = addedBy;
    removedOn = key.addedOn;
  }

  public Account.Id getAddedBy() {
    return addedBy;
  }

  public Account.Id getRemovedBy() {
    return removedBy;
  }

  public Timestamp getRemovedOn() {
    return removedOn;
  }
}
