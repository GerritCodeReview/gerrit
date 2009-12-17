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
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SchemaVersion;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.WildProjectNameProvider;
import com.google.gerrit.server.workflow.NoOpFunction;
import com.google.gerrit.server.workflow.SubmitFunction;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;

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

  private final ScriptRunner index_generic;
  private final ScriptRunner index_postgres;
  private final ScriptRunner mysql_nextval;

  @Inject
  public SchemaCreator(final SitePaths site) {
    this(site.site_path);
  }

  public SchemaCreator(final @SitePath File site) {
    site_path = site;
    index_generic = new ScriptRunner("index_generic.sql");
    index_postgres = new ScriptRunner("index_postgres.sql");
    mysql_nextval = new ScriptRunner("mysql_nextval.sql");
  }

  public void create(final ReviewDb db) throws OrmException {
    final JdbcSchema jdbc = (JdbcSchema) db;

    jdbc.createSchema();

    final SchemaVersion sVer = SchemaVersion.create();
    sVer.versionNbr = ReviewDb.VERSION;
    db.schemaVersion().insert(Collections.singleton(sVer));

    final SystemConfig sConfig = initSystemConfig(db);
    initOwnerCategory(db);
    initReadCategory(db, sConfig);
    initVerifiedCategory(db);
    initCodeReviewCategory(db, sConfig);
    initSubmitCategory(db);
    initPushTagCategory(db);
    initPushUpdateBranchCategory(db);
    initWildCardProject(db);

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

    final AccountGroup anonymous =
        new AccountGroup(new AccountGroup.NameKey("Anonymous Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupId(admin.getId());
    anonymous.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(anonymous));

    final AccountGroup registered =
        new AccountGroup(new AccountGroup.NameKey("Registered Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupId(admin.getId());
    registered.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(registered));

    final SystemConfig s = SystemConfig.create();
    s.registerEmailPrivateKey = SignedToken.generateRandomKey();
    s.adminGroupId = admin.getId();
    s.anonymousGroupId = anonymous.getId();
    s.registeredGroupId = registered.getId();
    try {
      s.sitePath = site_path.getCanonicalPath();
    } catch (IOException e) {
      s.sitePath = site_path.getAbsolutePath();
    }
    c.systemConfig().insert(Collections.singleton(s));
    return s;
  }

  private void initWildCardProject(final ReviewDb c) throws OrmException {
    final Project p;

    p = new Project(DEFAULT_WILD_NAME, WildProjectNameProvider.WILD_PROJECT_ID);
    p.setDescription("Rights inherited by all other projects");
    p.setUseContributorAgreements(false);
    c.projects().insert(Collections.singleton(p));
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 0);
    cat.setAbbreviatedName("V");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initCodeReviewCategory(final ReviewDb c,
      final SystemConfig sConfig) throws OrmException {
    final Transaction txn = c.beginTransaction();
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
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();

    final ProjectRight approve =
        new ProjectRight(new ProjectRight.Key(DEFAULT_WILD_NAME, cat.getId(),
            sConfig.registeredGroupId));
    approve.setMaxValue((short) 1);
    approve.setMinValue((short) -1);
    c.projectRights().insert(Collections.singleton(approve));
  }

  private void initOwnerCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.OWN, "Owner");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Administer All Settings"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initReadCategory(final ReviewDb c, final SystemConfig sConfig)
      throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.READ, "Read Access");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Upload permission"));
    vals.add(value(cat, 1, "Read access"));
    vals.add(value(cat, -1, "No access"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(DEFAULT_WILD_NAME, cat.getId(),
              sConfig.anonymousGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 1);
      c.projectRights().insert(Collections.singleton(read));
    }
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(DEFAULT_WILD_NAME, cat.getId(),
              sConfig.registeredGroupId));
      read.setMaxValue((short) 2);
      read.setMinValue((short) 1);
      c.projectRights().insert(Collections.singleton(read));
    }
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(DEFAULT_WILD_NAME, cat.getId(),
              sConfig.adminGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 1);
      c.projectRights().insert(Collections.singleton(read));
    }
  }

  private void initSubmitCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.SUBMIT, "Submit");
    cat.setPosition((short) -1);
    cat.setFunctionName(SubmitFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Submit"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initPushTagCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.PUSH_TAG, "Push Annotated Tag");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, ApprovalCategory.PUSH_TAG_SIGNED, "Create Signed Tag"));
    vals.add(value(cat, ApprovalCategory.PUSH_TAG_ANNOTATED,
        "Create Annotated Tag"));
    vals.add(value(cat, ApprovalCategory.PUSH_TAG_ANY, "Create Any Tag"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initPushUpdateBranchCategory(final ReviewDb c)
      throws OrmException {
    final Transaction txn = c.beginTransaction();
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
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
