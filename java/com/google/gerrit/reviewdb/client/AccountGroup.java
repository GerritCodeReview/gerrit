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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;
import java.sql.Timestamp;

/** Named group of one or more accounts, typically used for access controls. */
public final class AccountGroup {
  /**
   * Time when the audit subsystem was implemented, used as the default value for {@link #createdOn}
   * when one couldn't be determined from the audit log.
   */
  // Can't use Instant here because GWT. This is verified against a readable time in the tests,
  // which don't need to compile under GWT.
  private static final long AUDIT_CREATION_INSTANT_MS = 1244489460000L;

  public static Timestamp auditCreationInstantTs() {
    return new Timestamp(AUDIT_CREATION_INSTANT_MS);
  }

  /** Group name key */
  public static class NameKey extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String name;

    protected NameKey() {}

    public NameKey(String n) {
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

  /** Globally unique identifier. */
  public static class UUID extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String uuid;

    protected UUID() {}

    public UUID(String n) {
      uuid = n;
    }

    @Override
    public String get() {
      return uuid;
    }

    @Override
    protected void set(String newValue) {
      uuid = newValue;
    }

    /** Parse an AccountGroup.UUID out of a string representation. */
    public static UUID parse(String str) {
      final UUID r = new UUID();
      r.fromString(str);
      return r;
    }
  }

  /** @return true if the UUID is for a group managed within Gerrit. */
  public static boolean isInternalGroup(AccountGroup.UUID uuid) {
    return uuid.get().matches("^[0-9a-f]{40}$");
  }

  /** Synthetic key to link to within the database */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected int id;

    protected Id() {}

    public Id(int id) {
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
    public static Id parse(String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }
  }

  /** Unique name of this group within the system. */
  @Column(id = 1)
  protected NameKey name;

  /** Unique identity, to link entities as {@link #name} can change. */
  @Column(id = 2)
  protected Id groupId;

  // DELETED: id = 3 (ownerGroupId)

  /** A textual description of the group's purpose. */
  @Column(id = 4, length = Integer.MAX_VALUE, notNull = false)
  protected String description;

  // DELETED: id = 5 (groupType)
  // DELETED: id = 6 (externalName)

  @Column(id = 7)
  protected boolean visibleToAll;

  // DELETED: id = 8 (emailOnlyAuthors)

  /** Globally unique identifier name for this group. */
  @Column(id = 9)
  protected UUID groupUUID;

  /**
   * Identity of the group whose members can manage this group.
   *
   * <p>This can be a self-reference to indicate the group's members manage itself.
   */
  @Column(id = 10)
  protected UUID ownerGroupUUID;

  @Column(id = 11, notNull = false)
  protected Timestamp createdOn;

  protected AccountGroup() {}

  public AccountGroup(
      AccountGroup.NameKey newName,
      AccountGroup.Id newId,
      AccountGroup.UUID uuid,
      Timestamp createdOn) {
    name = newName;
    groupId = newId;
    visibleToAll = false;
    groupUUID = uuid;
    ownerGroupUUID = groupUUID;
    this.createdOn = createdOn;
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

  public void setNameKey(AccountGroup.NameKey nameKey) {
    name = nameKey;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String d) {
    description = d;
  }

  public AccountGroup.UUID getOwnerGroupUUID() {
    return ownerGroupUUID;
  }

  public void setOwnerGroupUUID(AccountGroup.UUID uuid) {
    ownerGroupUUID = uuid;
  }

  public void setVisibleToAll(boolean visibleToAll) {
    this.visibleToAll = visibleToAll;
  }

  public boolean isVisibleToAll() {
    return visibleToAll;
  }

  public AccountGroup.UUID getGroupUUID() {
    return groupUUID;
  }

  public void setGroupUUID(AccountGroup.UUID uuid) {
    groupUUID = uuid;
  }

  public Timestamp getCreatedOn() {
    return createdOn != null ? createdOn : auditCreationInstantTs();
  }

  public void setCreatedOn(Timestamp createdOn) {
    this.createdOn = createdOn;
  }
}
