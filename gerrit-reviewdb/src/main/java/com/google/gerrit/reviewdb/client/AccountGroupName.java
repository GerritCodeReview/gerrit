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

/** Unique name of an {@link AccountGroup}. */
public class AccountGroupName {
  @Column(id = 1)
  protected AccountGroup.NameKey name;

  @Column(id = 2)
  protected AccountGroup.Id groupId;

  protected AccountGroupName() {}

  public AccountGroupName(AccountGroup.NameKey name, AccountGroup.Id groupId) {
    this.name = name;
    this.groupId = groupId;
  }

  public AccountGroupName(AccountGroup group) {
    this(group.getNameKey(), group.getId());
  }

  public String getName() {
    return getNameKey().get();
  }

  public AccountGroup.NameKey getNameKey() {
    return name;
  }

  public AccountGroup.Id getId() {
    return groupId;
  }

  public void setId(AccountGroup.Id id) {
    groupId = id;
  }
}
