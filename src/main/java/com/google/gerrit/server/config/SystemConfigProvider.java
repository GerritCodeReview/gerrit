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

package com.google.gerrit.server.config;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SchemaVersion;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.reviewdb.TrustedExternalId;
import com.google.gerrit.server.workflow.NoOpFunction;
import com.google.gerrit.server.workflow.SubmitFunction;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads the {@link SystemConfig} from the database. */
class SystemConfigProvider implements Provider<SystemConfig> {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  SystemConfigProvider(final SchemaFactory<ReviewDb> sf) {
    schema = sf;
  }

  @Override
  public SystemConfig get() {
    try {
      final ReviewDb db = schema.open();
      try {
        SchemaVersion sVer = getSchemaVersion(db);

        if (sVer == null) {
          // Assume the schema is empty and try to populate it.
          //
          sVer = createSchema(db);
        }

        switch (sVer.versionNbr) {
          case 2:
            initPushTagCategory(db);
            initPushUpdateBranchCategory(db);

            sVer.versionNbr = 3;
            db.schemaVersion().update(Collections.singleton(sVer));
            break;

          case 15:
            sVer.versionNbr = 16;
            db.schemaVersion().update(Collections.singleton(sVer));
            break;
        }

        if (sVer.versionNbr != ReviewDb.VERSION) {
          throw new OrmException("Unsupported schema version "
              + sVer.versionNbr + "; expected schema version "
              + ReviewDb.VERSION);
        }

        final List<SystemConfig> all = db.systemConfig().all().toList();
        switch (all.size()) {
          case 1:
            return all.get(0);
          case 0:
            throw new OrmException("system_config table is empty");
          default:
            throw new OrmException("system_config must have exactly 1 row;"
                + " found " + all.size() + " rows instead");
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot read system_config", e);
    }
  }

  private SchemaVersion createSchema(final ReviewDb db) throws OrmException {
    db.createSchema();

    final SchemaVersion sVer = SchemaVersion.create();
    sVer.versionNbr = ReviewDb.VERSION;
    db.schemaVersion().insert(Collections.singleton(sVer));

    final SystemConfig sConfig = initSystemConfig(db);
    initTrustedExternalIds(db);
    initOwnerCategory(db);
    initReadCategory(db, sConfig);
    initVerifiedCategory(db);
    initCodeReviewCategory(db, sConfig);
    initSubmitCategory(db);
    initPushTagCategory(db);
    initPushUpdateBranchCategory(db);
    initWildCardProject(db);

    return sVer;
  }

  private SchemaVersion getSchemaVersion(final ReviewDb db) {
    try {
      return db.schemaVersion().get(new SchemaVersion.Key());
    } catch (OrmException e) {
      return null;
    }
  }

  private SystemConfig initSystemConfig(final ReviewDb c) throws OrmException {
    final AccountGroup admin =
        new AccountGroup(new AccountGroup.NameKey("Administrators"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    admin.setDescription("Gerrit Site Administrators");
    c.accountGroups().insert(Collections.singleton(admin));

    final AccountGroup anonymous =
        new AccountGroup(new AccountGroup.NameKey("Anonymous Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupId(admin.getId());
    anonymous.setAutomaticMembership(true);
    c.accountGroups().insert(Collections.singleton(anonymous));

    final AccountGroup registered =
        new AccountGroup(new AccountGroup.NameKey("Registered Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupId(admin.getId());
    registered.setAutomaticMembership(true);
    c.accountGroups().insert(Collections.singleton(registered));

    File sitePath = new File(".").getAbsoluteFile();
    if (".".equals(sitePath.getName())) {
      sitePath = sitePath.getParentFile();
    }

    final SystemConfig s = SystemConfig.create();
    s.xsrfPrivateKey = SignedToken.generateRandomKey();
    s.accountPrivateKey = SignedToken.generateRandomKey();
    s.adminGroupId = admin.getId();
    s.anonymousGroupId = anonymous.getId();
    s.registeredGroupId = registered.getId();
    s.sitePath = sitePath.getAbsolutePath();
    c.systemConfig().insert(Collections.singleton(s));
    return s;
  }

  private void initTrustedExternalIds(final ReviewDb c) throws OrmException {
    // By default with OpenID trust any http:// or https:// provider
    //
    initTrustedExternalId(c, "http://");
    initTrustedExternalId(c, "https://");
    initTrustedExternalId(c, "https://www.google.com/accounts/o8/id?id=");
  }

  private void initTrustedExternalId(final ReviewDb c, final String re)
      throws OrmException {
    c.trustedExternalIds().insert(
        Collections.singleton(new TrustedExternalId(new TrustedExternalId.Key(
            re))));
  }

  private void initWildCardProject(final ReviewDb c) throws OrmException {
    final Project proj;

    proj =
        new Project(new Project.NameKey("-- All Projects --"),
            ProjectRight.WILD_PROJECT);
    proj.setDescription("Rights inherited by all other projects");
    proj.setUseContributorAgreements(false);
    c.projects().insert(Collections.singleton(proj));
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 0);
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
        new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
            .getId(), sConfig.registeredGroupId));
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
    vals.add(value(cat, 1, "Read access"));
    vals.add(value(cat, -1, "No access"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
              .getId(), sConfig.anonymousGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 1);
      c.projectRights().insert(Collections.singleton(read));
    }
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
              .getId(), sConfig.adminGroupId));
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
