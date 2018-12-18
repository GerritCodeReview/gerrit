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

import com.google.gwtorm.client.CompoundKey;
import java.sql.Timestamp;
import java.util.Objects;

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public final class AccountGroupMemberAudit {
  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    protected Account.Id accountId;

    protected AccountGroup.Id groupId;

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

    @Override
    public String toString() {
      return "Key{"
          + "groupId="
          + groupId
          + ", accountId="
          + accountId
          + ", addedOn="
          + addedOn
          + '}';
    }
  }

  protected Key key;

  protected Account.Id addedBy;

  protected Account.Id removedBy;

  protected Timestamp removedOn;

  protected AccountGroupMemberAudit() {}

  public AccountGroupMemberAudit(final AccountGroupMember m, Account.Id adder, Timestamp addedOn) {
    final Account.Id who = m.getAccountId();
    final AccountGroup.Id group = m.getAccountGroupId();
    key = new AccountGroupMemberAudit.Key(who, group, addedOn);
    addedBy = adder;
  }

  public AccountGroupMemberAudit(AccountGroupMemberAudit.Key key, Account.Id adder) {
    this.key = key;
    addedBy = adder;
  }

  public AccountGroupMemberAudit.Key getKey() {
    return key;
  }

  public AccountGroup.Id getGroupId() {
    return key.getGroupId();
  }

  public Account.Id getMemberId() {
    return key.getParentKey();
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

  public Timestamp getAddedOn() {
    return key.getAddedOn();
  }

  public Account.Id getRemovedBy() {
    return removedBy;
  }

  public Timestamp getRemovedOn() {
    return removedOn;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AccountGroupMemberAudit)) {
      return false;
    }
    AccountGroupMemberAudit a = (AccountGroupMemberAudit) o;
    return Objects.equals(key, a.key)
        && Objects.equals(addedBy, a.addedBy)
        && Objects.equals(removedBy, a.removedBy)
        && Objects.equals(removedOn, a.removedOn);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, addedBy, removedBy, removedOn);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "key="
        + key
        + ", addedBy="
        + addedBy
        + ", removedBy="
        + removedBy
        + ", removedOn="
        + removedOn
        + "}";
  }
}
