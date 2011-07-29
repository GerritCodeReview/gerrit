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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

/** Named group of one or more accounts, typically used for access controls. */
public final class AccountGroup {
  /** Group name key */
  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
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

  /** Globally unique identifier. */
  public static class UUID extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = 40)
    protected String uuid;

    protected UUID() {
    }

    public UUID(final String n) {
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
    public static UUID parse(final String str) {
      final UUID r = new UUID();
      r.fromString(str);
      return r;
    }
  }

  /** Distinguished name, within organization directory server. */
  public static class ExternalNameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String name;

    protected ExternalNameKey() {
    }

    public ExternalNameKey(final String n) {
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

    @Column(id = 1)
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

  public static enum Type {
    /**
     * System defined and managed group, e.g. anonymous users.
     * <p>
     * These groups must be explicitly named by {@link SystemConfig} and are
     * specially handled throughout the code. In UI contexts their membership is
     * not displayed. When computing effective group membership for any given
     * user account, these groups are automatically handled using specialized
     * branch conditions.
     */
    SYSTEM(false),

    /**
     * Group defined within our database.
     * <p>
     * An internal group has its membership fully enumerated in the database.
     * The membership can be viewed and edited through the web UI by any user
     * who is a member of the owner group. These groups are not treated special
     * in the code.
     */
    INTERNAL(false),

    /**
     * Group defined by external LDAP database.
     * <p>
     * A group whose membership is determined by the LDAP directory that we
     * connect to for user and group information. In UI contexts the membership
     * of the group is not displayed, as it may be exceedingly large, or might
     * contain users who have never logged into this server before (and thus
     * have no matching account record). Adding or removing users from an LDAP
     * group requires making edits through the LDAP directory, and cannot be
     * done through our UI.
     */
    LDAP(true),

    /**
     * Group defined by Atlassian Crowd
     * <p>
     * A group whose membership is determined by the external Crowd
     * Server that we connect to for user and group information. In UI contexts,
     * the membership of the group is not displayed, as it may be exceedingly large,
     * or might contain users who have never logged into this server before (and
     * thus have no matching account record). Adding or removing users from an
     * LDAP group requires making edits through Atlassian Crowd, and cannot be
     * done through our UI.
     */
    CROWD(true);

    private final boolean externalGroupCapable;

    private Type(boolean externalGroupCapable) {
      this.externalGroupCapable = externalGroupCapable;
    }

    public boolean isExternalGroupCapable() {
      return externalGroupCapable;
    }
  }

  /** Common UUID assigned to the "Project Owners" placeholder group. */
  public static final AccountGroup.UUID PROJECT_OWNERS =
      new AccountGroup.UUID("global:Project-Owners");

  /** Common UUID assigned to the "Anonymous Users" group. */
  public static final AccountGroup.UUID ANONYMOUS_USERS =
      new AccountGroup.UUID("global:Anonymous-Users");

  /** Common UUID assigned to the "Registered Users" group. */
  public static final AccountGroup.UUID REGISTERED_USERS =
      new AccountGroup.UUID("global:Registered-Users");

  /** Unique name of this group within the system. */
  @Column(id = 1)
  protected NameKey name;

  /** Unique identity, to link entities as {@link #name} can change. */
  @Column(id = 2)
  protected Id groupId;

  /**
   * Identity of the group whose members can manage this group.
   * <p>
   * This can be a self-reference to indicate the group's members manage itself.
   */
  @Column(id = 3)
  protected Id ownerGroupId;

  /** A textual description of the group's purpose. */
  @Column(id = 4, length = Integer.MAX_VALUE, notNull = false)
  protected String description;

  /** Is the membership managed by some external means? */
  @Column(id = 5, length = 8)
  protected String groupType;

  /** Distinguished name in the directory server. */
  @Column(id = 6, notNull = false)
  protected ExternalNameKey externalName;

  @Column(id = 7)
  protected boolean visibleToAll;

  /** Comment and action email notifications by users in this group are only
   *  sent to change authors. */
  @Column(id = 8)
  protected boolean emailOnlyAuthors;

  /** Globally unique identifier name for this group. */
  @Column(id = 9)
  protected UUID groupUUID;

  protected AccountGroup() {
  }

  public AccountGroup(final AccountGroup.NameKey newName,
      final AccountGroup.Id newId, final AccountGroup.UUID uuid) {
    name = newName;
    groupId = newId;
    ownerGroupId = groupId;
    visibleToAll = false;
    groupUUID = uuid;
    setType(Type.INTERNAL);
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

  public Type getType() {
    return Type.valueOf(groupType);
  }

  public void setType(final Type t) {
    groupType = t.name();
  }

  public ExternalNameKey getExternalNameKey() {
    return externalName;
  }

  public void setExternalNameKey(final ExternalNameKey k) {
    externalName = k;
  }

  public void setVisibleToAll(final boolean visibleToAll) {
    this.visibleToAll = visibleToAll;
  }

  public boolean isVisibleToAll() {
    return visibleToAll;
  }

  public boolean isEmailOnlyAuthors() {
    return emailOnlyAuthors;
  }

  public void setEmailOnlyAuthors(boolean emailOnlyAuthors) {
    this.emailOnlyAuthors = emailOnlyAuthors;
  }

  public AccountGroup.UUID getGroupUUID() {
    return groupUUID;
  }

  public void setGroupUUID(AccountGroup.UUID uuid) {
    groupUUID = uuid;
  }
}
