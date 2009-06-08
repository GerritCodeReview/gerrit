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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

/** Named group of one or more accounts, typically used for access controls. */
public final class AccountGroup {
  /** Group name key */
  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

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

    @Override
    protected void set(String newValue) {
      name = newValue;
    }
  }

  /** Synthetic key to link to within the database */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

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

    @Override
    protected void set(int newValue) {
      id = newValue;
    }

    /** Parse an AccountGroup.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }
  }

  /** Unique name of this group within the system. */
  @Column
  protected NameKey name;

  /** Unique identity, to link entities as {@link #name} can change. */
  @Column
  protected Id groupId;

  /**
   * Identity of the group whose members can manage this group.
   * <p>
   * This can be a self-reference to indicate the group's members manage itself.
   */
  @Column
  protected Id ownerGroupId;

  /** A textual description of the group's purpose. */
  @Column(length = Integer.MAX_VALUE, notNull = false)
  protected String description;

  /** Is the membership managed by some external means? */
  @Column
  protected boolean automaticMembership;

  protected AccountGroup() {
  }

  public AccountGroup(final AccountGroup.NameKey newName,
      final AccountGroup.Id newId) {
    name = newName;
    groupId = newId;
    ownerGroupId = groupId;
  }

  public AccountGroup.Id getId() {
    return groupId;
  }

  public String getName() {
    return name.get();
  }

  public AccountGroup.NameKey getNameKey() {
    return name;
  }

  public void setNameKey(final AccountGroup.NameKey nameKey) {
    name = nameKey;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String d) {
    description = d;
  }

  public AccountGroup.Id getOwnerGroupId() {
    return ownerGroupId;
  }

  public void setOwnerGroupId(final AccountGroup.Id id) {
    ownerGroupId = id;
  }

  public boolean isAutomaticMembership() {
    return automaticMembership;
  }

  public void setAutomaticMembership(final boolean auto) {
    automaticMembership = auto;
  }
}
