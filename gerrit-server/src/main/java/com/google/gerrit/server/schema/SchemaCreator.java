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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.workflow.NoOpFunction;
import com.google.gerrit.server.workflow.SubmitFunction;
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
  private static final Project.NameKey DEFAULT_WILD_NAME =
      new Project.NameKey("-- All Projects --");

  private final @SitePath
  File site_path;

  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  private final int versionNbr;
  private final ScriptRunner index_generic;
  private final ScriptRunner index_postgres;
  private final ScriptRunner mysql_nextval;

  @Inject
  public SchemaCreator(final SitePaths site,
      @Current final SchemaVersion version, final GitRepositoryManager mgr,
      @GerritPersonIdent final PersonIdent au) {
    this(site.site_path, version, mgr, au);
  }

  public SchemaCreator(final @SitePath File site,
      @Current final SchemaVersion version, final GitRepositoryManager gitMgr,
      final @GerritPersonIdent PersonIdent au) {
    site_path = site;
    mgr = gitMgr;
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
    initOwnerCategory(db);
    initReadCategory(db, sConfig);
    initVerifiedCategory(db);
    initCodeReviewCategory(db, sConfig);
    initSubmitCategory(db);
    initPushTagCategory(db);
    initPushUpdateBranchCategory(db);
    initForgeIdentityCategory(db, sConfig);

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

  private SystemConfig initSystemConfig(final ReviewDb c) throws OrmException {
    final AccountGroup admin =
        new AccountGroup(new AccountGroup.NameKey("Administrators"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    admin.setDescription("Gerrit Site Administrators");
    admin.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(admin));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(admin)));

    final AccountGroup anonymous =
        new AccountGroup(new AccountGroup.NameKey("Anonymous Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupId(admin.getId());
    anonymous.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(anonymous));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(anonymous)));

    final AccountGroup registered =
        new AccountGroup(new AccountGroup.NameKey("Registered Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupId(admin.getId());
    registered.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(registered));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(registered)));

    final AccountGroup batchUsers =
      new AccountGroup(new AccountGroup.NameKey("Non-Interactive Users"),
          new AccountGroup.Id(c.nextAccountGroupId()));
    batchUsers.setDescription("Users who perform batch actions on Gerrit");
    batchUsers.setOwnerGroupId(admin.getId());
    batchUsers.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(batchUsers));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(batchUsers)));

    final AccountGroup owners =
        new AccountGroup(new AccountGroup.NameKey("Project Owners"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    owners.setDescription("Any owner of the project");
    owners.setOwnerGroupId(admin.getId());
    owners.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(owners));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(owners)));

    final SystemConfig s = SystemConfig.create();
    s.registerEmailPrivateKey = SignedToken.generateRandomKey();
    s.adminGroupId = admin.getId();
    s.anonymousGroupId = anonymous.getId();
    s.registeredGroupId = registered.getId();
    s.batchUsersGroupId = batchUsers.getId();
    s.ownerGroupId = owners.getId();
    s.wildProjectName = DEFAULT_WILD_NAME;
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
      git = mgr.openRepository(DEFAULT_WILD_NAME);
    } catch (RepositoryNotFoundException notFound) {
      // A repository may be missing if this project existed only to store
      // inheritable permissions. For example '-- All Projects --'.
      try {
        git = mgr.createRepository(DEFAULT_WILD_NAME);
      } catch (RepositoryNotFoundException err) {
        final String name = DEFAULT_WILD_NAME.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    }
    try {
      MetaDataUpdate md =
          new MetaDataUpdate(new NoReplication(), DEFAULT_WILD_NAME, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      Project p = config.getProject();
      p.setDescription("Rights inherited by all other projects");
      p.setUseContributorAgreements(false);

      md.setMessage("Created project\n");
      if (!config.commit(md)) {
        throw new IOException("Cannot create " + DEFAULT_WILD_NAME.get());
      }
    } finally {
      git.close();
    }
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

    final RefRight approve =
        new RefRight(new RefRight.Key(DEFAULT_WILD_NAME,
            new RefRight.RefPattern("refs/heads/*"), cat.getId(),
            sConfig.registeredGroupId));
    approve.setMaxValue((short) 1);
    approve.setMinValue((short) -1);
    c.refRights().insert(Collections.singleton(approve));
  }

  private void initOwnerCategory(final ReviewDb c) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.OWN, "Owner");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Administer All Settings"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private void initReadCategory(final ReviewDb c, final SystemConfig sConfig)
      throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.READ, "Read Access");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 3, "Upload merges permission"));
    vals.add(value(cat, 2, "Upload permission"));
    vals.add(value(cat, 1, "Read access"));
    vals.add(value(cat, -1, "No access"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);

    final RefRight.RefPattern pattern = new RefRight.RefPattern(RefRight.ALL);
    {
      final RefRight read =
          new RefRight(new RefRight.Key(DEFAULT_WILD_NAME, pattern,
              cat.getId(), sConfig.anonymousGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 1);
      c.refRights().insert(Collections.singleton(read));
    }
    {
      final RefRight read =
          new RefRight(new RefRight.Key(DEFAULT_WILD_NAME, pattern,
              cat.getId(), sConfig.registeredGroupId));
      read.setMaxValue((short) 2);
      read.setMinValue((short) 1);
      c.refRights().insert(Collections.singleton(read));
    }
    {
      final RefRight read =
          new RefRight(new RefRight.Key(DEFAULT_WILD_NAME, pattern,
              cat.getId(), sConfig.adminGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 1);
      c.refRights().insert(Collections.singleton(read));
    }
  }

  private void initSubmitCategory(final ReviewDb c) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.SUBMIT, "Submit");
    cat.setPosition((short) -1);
    cat.setFunctionName(SubmitFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Submit"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private void initPushTagCategory(final ReviewDb c) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.PUSH_TAG, "Push Tag");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, ApprovalCategory.PUSH_TAG_SIGNED, "Create Signed Tag"));
    vals.add(value(cat, ApprovalCategory.PUSH_TAG_ANNOTATED,
        "Create Annotated Tag"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private void initPushUpdateBranchCategory(final ReviewDb c)
      throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.PUSH_HEAD, "Push Branch");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, ApprovalCategory.PUSH_HEAD_UPDATE, "Update Branch"));
    vals.add(value(cat, ApprovalCategory.PUSH_HEAD_CREATE, "Create Branch"));
    vals.add(value(cat, ApprovalCategory.PUSH_HEAD_REPLACE,
        "Force Push Branch; Delete Branch"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private void initForgeIdentityCategory(final ReviewDb c,
      final SystemConfig sConfig) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> values;

    cat =
        new ApprovalCategory(ApprovalCategory.FORGE_IDENTITY, "Forge Identity");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    values = new ArrayList<ApprovalCategoryValue>();
    values.add(value(cat, ApprovalCategory.FORGE_AUTHOR,
        "Forge Author Identity"));
    values.add(value(cat, ApprovalCategory.FORGE_COMMITTER,
        "Forge Committer or Tagger Identity"));
    values.add(value(cat, ApprovalCategory.FORGE_SERVER,
        "Forge Gerrit Code Review Server Identity"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(values);

    RefRight right =
        new RefRight(new RefRight.Key(sConfig.wildProjectName,
            new RefRight.RefPattern(RefRight.ALL),
            ApprovalCategory.FORGE_IDENTITY, sConfig.registeredGroupId));
    right.setMinValue(ApprovalCategory.FORGE_AUTHOR);
    right.setMaxValue(ApprovalCategory.FORGE_AUTHOR);
    c.refRights().insert(Collections.singleton(right));
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
