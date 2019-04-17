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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Objects;

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public final class AccountGroupMemberAudit {
  public static Key key(Account.Id accountId, AccountGroup.Id groupId, Timestamp addedOn) {
    return new AutoValue_AccountGroupMemberAudit_Key(accountId, groupId, addedOn);
  }

  @AutoValue
  public abstract static class Key {
    public abstract Account.Id accountId();

    public abstract AccountGroup.Id groupId();

    public abstract Timestamp addedOn();
  }

  protected Key key;

  protected Account.Id addedBy;

  @Nullable protected Account.Id removedBy;

  @Nullable protected Timestamp removedOn;

  protected AccountGroupMemberAudit() {}

  public AccountGroupMemberAudit(final AccountGroupMember m, Account.Id adder, Timestamp addedOn) {
    final Account.Id who = m.getAccountId();
    final AccountGroup.Id group = m.getAccountGroupId();
    key = key(who, group, addedOn);
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
    return key.groupId();
  }

  public Account.Id getMemberId() {
    return key.accountId();
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
    removedOn = key.addedOn();
  }

  public Account.Id getAddedBy() {
    return addedBy;
  }

  public Timestamp getAddedOn() {
    return key.addedOn();
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
