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

import com.google.auto.value.AutoValue;
import java.util.Objects;

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public final class AccountGroupMember {
  public static Key key(Account.Id accountId, AccountGroup.Id groupId) {
    return new AutoValue_AccountGroupMember_Key(accountId, groupId);
  }

  @AutoValue
  public abstract static class Key {
    public abstract Account.Id accountId();

    public abstract AccountGroup.Id groupId();
  }

  protected Key key;

  protected AccountGroupMember() {}

  public AccountGroupMember(AccountGroupMember.Key k) {
    key = k;
  }

  public AccountGroupMember.Key getKey() {
    return key;
  }

  public Account.Id getAccountId() {
    return key.accountId();
  }

  public AccountGroup.Id getAccountGroupId() {
    return key.groupId();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof AccountGroupMember) && Objects.equals(key, ((AccountGroupMember) o).key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{key=" + key + "}";
  }
}
