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
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

/** Named group of one or more accounts, typically used for access controls. */
public final class AccountGroup {
  /** Group name key */
  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    @Column(length = 40)
    protected String name;

    protected NameKey() {
    }

    public NameKey(final String n) {
      name = n;
    }

    @Override
    public String get() {
      return name;
    }
  }

  /** Synthetic key to link to within the database */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    @Column
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }
  }

  @Column
  protected NameKey name;

  @Column
  protected Id groupId;

  @Column(length = Integer.MAX_VALUE, notNull = false)
  protected String description;

  protected AccountGroup() {
  }

  public AccountGroup(final AccountGroup.NameKey newName,
      final AccountGroup.Id newId) {
    name = newName;
    groupId = newId;
  }

  public AccountGroup.Id getId() {
    return groupId;
  }

  public String getName() {
    return name.get();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String d) {
    description = d;
  }
}
