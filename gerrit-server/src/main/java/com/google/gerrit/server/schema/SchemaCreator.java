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

package com.google.gerrit.server.schema;

import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** Creates the current database schema and populates initial code rows. */
public class SchemaCreator {
  private final @SitePath
  File site_path;

  private final AllProjectsCreator allProjectsCreator;
  private final PersonIdent serverUser;
  private final DataSourceType dataSourceType;

  private final int versionNbr;

  private AccountGroup admin;
  private AccountGroup anonymous;
  private AccountGroup registered;
  private AccountGroup owners;

  @Inject
  public SchemaCreator(SitePaths site,
      @Current SchemaVersion version,
      AllProjectsCreator ap,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst) {
    this(site.site_path, version, ap, au, dst);
  }

  public SchemaCreator(@SitePath File site,
      @Current SchemaVersion version,
      AllProjectsCreator ap,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst) {
    site_path = site;
    allProjectsCreator = ap;
    serverUser = au;
    dataSourceType = dst;
    versionNbr = version.getVersionNbr();
  }

  public void create(final ReviewDb db) throws OrmException, IOException,
      ConfigInvalidException {
    final JdbcSchema jdbc = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(jdbc);
    try {
      jdbc.updateSchema(e);
    } finally {
      e.close();
    }

    final CurrentSchemaVersion sVer = CurrentSchemaVersion.create();
    sVer.versionNbr = versionNbr;
    db.schemaVersion().insert(Collections.singleton(sVer));

    initSystemConfig(db);
    allProjectsCreator
      .setAdministrators(GroupReference.forGroup(admin))
      .create();
    dataSourceType.getIndexScript().run(db);
  }

  private AccountGroup newGroup(ReviewDb c, String name, AccountGroup.UUID uuid)
      throws OrmException {
    if (uuid == null) {
      uuid = GroupUUID.make(name, serverUser);
    }
    return new AccountGroup( //
        new AccountGroup.NameKey(name), //
        new AccountGroup.Id(c.nextAccountGroupId()), //
        uuid);
  }

  private SystemConfig initSystemConfig(final ReviewDb c) throws OrmException {
    admin = newGroup(c, "Administrators", null);
    admin.setDescription("Gerrit Site Administrators");
    admin.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(admin));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(admin)));

    anonymous =
        newGroup(c, "Anonymous Users", AccountGroup.ANONYMOUS_USERS);
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupUUID(admin.getGroupUUID());
    anonymous.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(anonymous));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(anonymous)));

    registered =
        newGroup(c, "Registered Users", AccountGroup.REGISTERED_USERS);
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupUUID(admin.getGroupUUID());
    registered.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(registered));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(registered)));

    final AccountGroup batchUsers = newGroup(c, "Non-Interactive Users", null);
    batchUsers.setDescription("Users who perform batch actions on Gerrit");
    batchUsers.setOwnerGroupUUID(admin.getGroupUUID());
    batchUsers.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(batchUsers));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(batchUsers)));

    owners = newGroup(c, "Project Owners", AccountGroup.PROJECT_OWNERS);
    owners.setDescription("Any owner of the project");
    owners.setOwnerGroupUUID(admin.getGroupUUID());
    owners.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(owners));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(owners)));

    final SystemConfig s = SystemConfig.create();
    try {
      s.sitePath = site_path.getCanonicalPath();
    } catch (IOException e) {
      s.sitePath = site_path.getAbsolutePath();
    }
    c.systemConfig().insert(Collections.singleton(s));
    return s;
  }
}
