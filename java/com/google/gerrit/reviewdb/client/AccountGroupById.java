// Copyright (C) 2011 The Android Open Source Project
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

/** Membership of an {@link AccountGroup} in an {@link AccountGroup}. */
public final class AccountGroupById {
  public static Key key(AccountGroup.Id groupId, AccountGroup.UUID includeUuid) {
    return new AutoValue_AccountGroupById_Key(groupId, includeUuid);
  }

  @AutoValue
  public abstract static class Key {
    public abstract AccountGroup.Id groupId();

    public abstract AccountGroup.UUID includeUuid();
  }

  protected Key key;

  protected AccountGroupById() {}

  public AccountGroupById(AccountGroupById.Key k) {
    key = k;
  }

  public AccountGroupById.Key getKey() {
    return key;
  }

  public AccountGroup.Id getGroupId() {
    return key.groupId();
  }

  public AccountGroup.UUID getIncludeUuid() {
    return key.includeUuid();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof AccountGroupById) && Objects.equals(key, ((AccountGroupById) o).key);
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
