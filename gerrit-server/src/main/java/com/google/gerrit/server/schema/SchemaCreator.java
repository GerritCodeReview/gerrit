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

import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/** Creates the current database schema and populates initial code rows. */
public class SchemaCreator {
  private final @SitePath
  File site_path;

  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjectsName;
  private final PersonIdent serverUser;

  private final int versionNbr;
  private final ScriptRunner index_generic;
  private final ScriptRunner index_postgres;
  private final ScriptRunner mysql_nextval;

  private AccountGroup admin;
  private AccountGroup anonymous;
  private AccountGroup registered;
  private AccountGroup owners;

  @Inject
  public SchemaCreator(SitePaths site,
      @Current SchemaVersion version,
      GitRepositoryManager mgr,
      AllProjectsName allProjectsName,
      @GerritPersonIdent PersonIdent au) {
    this(site.site_path, version, mgr, allProjectsName, au);
  }

  public SchemaCreator(@SitePath File site,
      @Current SchemaVersion version,
      GitRepositoryManager gitMgr,
      AllProjectsName ap,
      @GerritPersonIdent PersonIdent au) {
    site_path = site;
    mgr = gitMgr;
    allProjectsName = ap;
    serverUser = au;
    versionNbr = version.getVersionNbr();
    index_generic = new ScriptRunner("index_generic.sql");
    index_postgres = new ScriptRunner("index_postgres.sql");
    mysql_nextval = new ScriptRunner("mysql_nextval.sql");
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

    final SystemConfig sConfig = initSystemConfig(db);
    initVerifiedCategory(db);
    initCodeReviewCategory(db, sConfig);

    if (mgr != null) {
      // TODO This should never be null when initializing a site.
      initWildCardProject();
    }

    final SqlDialect d = jdbc.getDialect();
    if (d instanceof DialectH2) {
      index_generic.run(db);

    } else if (d instanceof DialectMySQL) {
      index_generic.run(db);
      mysql_nextval.run(db);

    } else if (d instanceof DialectPostgreSQL) {
      index_postgres.run(db);

    } else {
      throw new OrmException("Unsupported database " + d.getClass().getName());
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
    anonymous.setOwnerGroupId(admin.getId());
    anonymous.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(anonymous));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(anonymous)));

    registered =
        newGroup(c, "Registered Users", AccountGroup.REGISTERED_USERS);
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupId(admin.getId());
    registered.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(registered));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(registered)));

    final AccountGroup batchUsers = newGroup(c, "Non-Interactive Users", null);
    batchUsers.setDescription("Users who perform batch actions on Gerrit");
    batchUsers.setOwnerGroupId(admin.getId());
    batchUsers.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(batchUsers));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(batchUsers)));

    owners = newGroup(c, "Project Owners", AccountGroup.PROJECT_OWNERS);
    owners.setDescription("Any owner of the project");
    owners.setOwnerGroupId(admin.getId());
    owners.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(owners));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(owners)));

    final SystemConfig s = SystemConfig.create();
    s.registerEmailPrivateKey = SignedToken.generateRandomKey();

    s.batchUsersGroupId = batchUsers.getId();
    s.batchUsersGroupUUID = batchUsers.getGroupUUID();

    try {
      s.sitePath = site_path.getCanonicalPath();
    } catch (IOException e) {
      s.sitePath = site_path.getAbsolutePath();
    }
    c.systemConfig().insert(Collections.singleton(s));
    return s;
  }

  private void initWildCardProject() throws IOException, ConfigInvalidException {
    Repository git;
    try {
      git = mgr.openRepository(allProjectsName);
    } catch (RepositoryNotFoundException notFound) {
      // A repository may be missing if this project existed only to store
      // inheritable permissions. For example 'All-Projects'.
      try {
        git = mgr.createRepository(allProjectsName);
      } catch (RepositoryNotFoundException err) {
        final String name = allProjectsName.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    }
    try {
      MetaDataUpdate md = new MetaDataUpdate(new NoReplication(), allProjectsName, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      Project p = config.getProject();
      p.setDescription("Rights inherited by all other projects");
      p.setUseContributorAgreements(false);

      AccessSection cap = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
      AccessSection all = config.getAccessSection(AccessSection.ALL, true);
      AccessSection heads = config.getAccessSection(AccessSection.HEADS, true);
      AccessSection meta = config.getAccessSection(GitRepositoryManager.REF_CONFIG, true);

      cap.getPermission(GlobalCapability.ADMINISTRATE_SERVER, true)
        .add(rule(config, admin));

      PermissionRule review = rule(config, registered);
      review.setRange(-1, 1);
      heads.getPermission(Permission.LABEL + "Code-Review", true).add(review);

      all.getPermission(Permission.READ, true) //
          .add(rule(config, admin));
      all.getPermission(Permission.READ, true) //
          .add(rule(config, anonymous));

      config.getAccessSection("refs/for/" + AccessSection.ALL, true) //
          .getPermission(Permission.PUSH, true) //
          .add(rule(config, registered));
      all.getPermission(Permission.FORGE_AUTHOR, true) //
          .add(rule(config, registered));

      meta.getPermission(Permission.READ, true) //
          .add(rule(config, owners));

      md.setMessage("Initialized Gerrit Code Review " + Version.getVersion());
      if (!config.commit(md)) {
        throw new IOException("Cannot create " + allProjectsName.get());
      }
    } finally {
      git.close();
    }
  }

  private PermissionRule rule(ProjectConfig config, AccountGroup group) {
    return new PermissionRule(config.resolve(group));
  }

  private PermissionRule rule(ProjectConfig config, AccountGroup group,
      int min, int max) {
    PermissionRule rule = new PermissionRule(config.resolve(group));
    rule.setRange(min, max);
    return rule;
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 0);
    cat.setAbbreviatedName("V");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private void initCodeReviewCategory(final ReviewDb c,
      final SystemConfig sConfig) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    cat.setPosition((short) 1);
    cat.setAbbreviatedName("R");
    cat.setCopyMinScore(true);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
