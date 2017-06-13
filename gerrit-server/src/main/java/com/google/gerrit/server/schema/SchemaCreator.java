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

import com.google.gerrit.common.TimeUtil;
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
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;

/** Creates the current database schema and populates initial code rows. */
public class SchemaCreator {
  @SitePath private final Path site_path;

  private final AllProjectsCreator allProjectsCreator;
  private final AllUsersCreator allUsersCreator;
  private final PersonIdent serverUser;
  private final DataSourceType dataSourceType;
  private final GroupIndexCollection indexCollection;

  private AccountGroup admin;
  private AccountGroup batch;

  @Inject
  public SchemaCreator(
      SitePaths site,
      AllProjectsCreator ap,
      AllUsersCreator auc,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst,
      GroupIndexCollection ic) {
    this(site.site_path, ap, auc, au, dst, ic);
  }

  public SchemaCreator(
      @SitePath Path site,
      AllProjectsCreator ap,
      AllUsersCreator auc,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst,
      GroupIndexCollection ic) {
    site_path = site;
    allProjectsCreator = ap;
    allUsersCreator = auc;
    serverUser = au;
    dataSourceType = dst;
    indexCollection = ic;
  }

  public void create(ReviewDb db) throws OrmException, IOException, ConfigInvalidException {
    final JdbcSchema jdbc = (JdbcSchema) db;
    try (JdbcExecutor e = new JdbcExecutor(jdbc)) {
      jdbc.updateSchema(e);
    }

    final CurrentSchemaVersion sVer = CurrentSchemaVersion.create();
    sVer.versionNbr = SchemaVersion.getBinaryVersion();
    db.schemaVersion().insert(Collections.singleton(sVer));

    createDefaultGroups(db);
    initSystemConfig(db);
    allProjectsCreator
        .setAdministrators(GroupReference.forGroup(admin))
        .setBatchUsers(GroupReference.forGroup(batch))
        .create();
    allUsersCreator.setAdministrators(GroupReference.forGroup(admin)).create();
    dataSourceType.getIndexScript().run(db);
  }

  private void createDefaultGroups(ReviewDb db) throws OrmException, IOException {
    admin = newGroup(db, "Administrators", null);
    admin.setDescription("Gerrit Site Administrators");
    db.accountGroups().insert(Collections.singleton(admin));
    db.accountGroupNames().insert(Collections.singleton(new AccountGroupName(admin)));
    index(admin);

    batch = newGroup(db, "Non-Interactive Users", null);
    batch.setDescription("Users who perform batch actions on Gerrit");
    batch.setOwnerGroupUUID(admin.getGroupUUID());
    db.accountGroups().insert(Collections.singleton(batch));
    db.accountGroupNames().insert(Collections.singleton(new AccountGroupName(batch)));
    index(batch);
  }

  private void index(AccountGroup group) throws IOException {
    for (GroupIndex groupIndex : indexCollection.getWriteIndexes()) {
      groupIndex.replace(group);
    }
  }

  private AccountGroup newGroup(ReviewDb c, String name, AccountGroup.UUID uuid)
      throws OrmException {
    if (uuid == null) {
      uuid = GroupUUID.make(name, serverUser);
    }
    return new AccountGroup( //
        new AccountGroup.NameKey(name), //
        new AccountGroup.Id(c.nextAccountGroupId()), //
        uuid,
        TimeUtil.nowTs());
  }

  private SystemConfig initSystemConfig(ReviewDb db) throws OrmException {
    SystemConfig s = SystemConfig.create();
    try {
      s.sitePath = site_path.toRealPath().normalize().toString();
    } catch (IOException e) {
      s.sitePath = site_path.toAbsolutePath().normalize().toString();
    }
    db.systemConfig().insert(Collections.singleton(s));
    return s;
  }
}
