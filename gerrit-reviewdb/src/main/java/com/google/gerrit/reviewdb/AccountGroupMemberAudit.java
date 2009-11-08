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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;

import java.sql.Timestamp;

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public final class AccountGroupMemberAudit {
  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column
    protected Account.Id accountId;

    @Column
    protected AccountGroup.Id groupId;

    @Column
    protected Timestamp addedOn;

    protected Key() {
      accountId = new Account.Id();
      groupId = new AccountGroup.Id();
    }

    public Key(final Account.Id a, final AccountGroup.Id g, final Timestamp t) {
      accountId = a;
      groupId = g;
      addedOn = t;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {groupId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  @Column
  protected Account.Id addedBy;

  @Column(notNull = false)
  protected Account.Id removedBy;

  @Column(notNull = false)
  protected Timestamp removedOn;

  protected AccountGroupMemberAudit() {
  }

  public AccountGroupMemberAudit(final AccountGroupMember m,
      final Account.Id adder) {
    final Account.Id who = m.getAccountId();
    final AccountGroup.Id group = m.getAccountGroupId();
    key = new AccountGroupMemberAudit.Key(who, group, now());
    addedBy = adder;
  }

  public AccountGroupMemberAudit.Key getKey() {
    return key;
  }

  public boolean isActive() {
    return removedOn == null;
  }

  public void removed(final Account.Id deleter) {
    removedBy = deleter;
    removedOn = now();
  }

  public void removedLegacy() {
    removedBy = addedBy;
    removedOn = key.addedOn;
  }

  private static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }
}
