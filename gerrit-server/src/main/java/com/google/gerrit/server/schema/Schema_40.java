// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccessCategory;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.CodeReviewLabel;
import com.google.gerrit.reviewdb.NewRefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SubmitLabel;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class Schema_40 extends SchemaVersion {
  @Inject
  Schema_40(Provider<Schema_35> prior) {
    super(prior);
  }

  @Override
  public void initData(ReviewDb db) throws OrmException {
    initNewSchemaData(db);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    initNewSchemaData(db);
  }

  private void initNewSchemaData(ReviewDb db) throws OrmException {
    final SystemConfig cfg = db.systemConfig().get(new SystemConfig.Key());

    initAccessCategories(db, cfg);

    initCodeReviewLabels(db, cfg);
  }


  private static void initAccessCategories(final ReviewDb db,
      final SystemConfig cfg) throws OrmException {
    initForgeAuthorAccess(db, cfg);
    initForgeCommitterAccess(db);
    initForgeServerAccess(db);
    initNoAccess(db);
    initOwnAccess(db);
    initPushHeadCreateAccess(db);
    initPushHeadReplaceAccess(db);
    initPushHeadUpdateAccess(db);
    initPushTagAnnotatedAccess(db);
    initPushTagSignedAccess(db);
    initReadAccess(db, cfg);
    initSubmitAccess(db, cfg);
    initUploadPermissionAccess(db, cfg);
  }

  private static void initForgeAuthorAccess(final ReviewDb db,
      final SystemConfig cfg) throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.FORGE_AUTHOR,
            AccessCategory.FORGE_AUTHOR.get());
    db.accessCategories().insert(Collections.singleton(cat));

    final NewRefRight nrf =
        createRightWildProject(db, cfg, cat.getId(), cfg.registeredGroupId);
    db.newRefRights().insert(Collections.singleton(nrf));
  }

  private static void initForgeCommitterAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.FORGE_COMMITTER,
            AccessCategory.FORGE_COMMITTER.get());

    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initForgeServerAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.FORGE_SERVER,
            AccessCategory.FORGE_SERVER.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initNoAccess(final ReviewDb db) throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.NO_ACCESS, AccessCategory.NO_ACCESS
            .get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initOwnAccess(final ReviewDb db) throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.OWN, AccessCategory.OWN.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initPushHeadCreateAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.PUSH_HEAD_CREATE,
            AccessCategory.PUSH_HEAD_CREATE.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initPushHeadReplaceAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.PUSH_HEAD_REPLACE,
            AccessCategory.PUSH_HEAD_REPLACE.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initPushHeadUpdateAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.PUSH_HEAD_UPDATE,
            AccessCategory.PUSH_HEAD_UPDATE.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initPushTagAnnotatedAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.PUSH_TAG_ANNOTATED,
            AccessCategory.PUSH_TAG_ANNOTATED.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initPushTagSignedAccess(final ReviewDb db)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.PUSH_TAG_SIGNED,
            AccessCategory.PUSH_TAG_SIGNED.get());
    db.accessCategories().insert(Collections.singleton(cat));
  }

  private static void initReadAccess(final ReviewDb db, final SystemConfig cfg)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.READ_ACCESS,
            AccessCategory.READ_ACCESS.get());
    db.accessCategories().insert(Collections.singleton(cat));

    final Collection<NewRefRight> readRights = new ArrayList<NewRefRight>();

    readRights.add(createRightWildProject(db, cfg, cat.getId(),
        cfg.adminGroupId));
    readRights.add(createRightWildProject(db, cfg, cat.getId(),
        cfg.anonymousGroupId));

    db.newRefRights().insert(readRights);
  }

  private static void initSubmitAccess(final ReviewDb db, final SystemConfig cfg)
      throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.SUBMIT, AccessCategory.SUBMIT.get());
    db.accessCategories().insert(Collections.singleton(cat));

    // A new ref right to submit is created just in order to show how it would
    // be created by an user.

    final NewRefRight nrf =
        createRightWildProject(db, cfg, cat.getId(), cfg.registeredGroupId);
    db.newRefRights().insert(Collections.singleton(nrf));

    final Collection<SubmitLabel> sls = new ArrayList<SubmitLabel>();

    sls.add(new SubmitLabel(nrf.getId(),
        new SubmitLabel.NameKey("CodeReview+2")));
    sls.add(new SubmitLabel(nrf.getId(), new SubmitLabel.NameKey("DrNo")));
    sls.add(new SubmitLabel(nrf.getId(), new SubmitLabel.NameKey("Verified")));

    db.submitLabels().insert(sls);
  }

  private static void initUploadPermissionAccess(final ReviewDb db,
      final SystemConfig cfg) throws OrmException {
    final AccessCategory cat =
        new AccessCategory(AccessCategory.UPLOAD_PERMISSION,
            AccessCategory.UPLOAD_PERMISSION.get());
    db.accessCategories().insert(Collections.singleton(cat));

    final NewRefRight nrf =
        createRightWildProject(db, cfg, cat.getId(), cfg.registeredGroupId);
    db.newRefRights().insert(Collections.singleton(nrf));
  }

  private static NewRefRight createRightWildProject(final ReviewDb db,
      final SystemConfig cfg, final AccessCategory.Id accessCategoryId,
      final AccountGroup.Id groupId) throws OrmException {
    final NewRefRight nrf =
        new NewRefRight(new NewRefRight.Id(db.nextNewRefRightId()),
            new NewRefRight.AlternateKey(cfg.wildProjectName,
                new NewRefRight.RefPattern(NewRefRight.ALL), accessCategoryId,
                groupId));
    return nrf;
  }

  private static void initCodeReviewLabels(final ReviewDb db,
      final SystemConfig cfg) throws OrmException {
    final Collection<CodeReviewLabel> crls = new ArrayList<CodeReviewLabel>();

    // The below labels correspond to the default
    // approvalCategories/approvalCategoryValues.
    crls.add(new CodeReviewLabel(cfg.registeredGroupId,
        new CodeReviewLabel.NameKey("CodeReview-1")));
    crls.add(new CodeReviewLabel(cfg.registeredGroupId,
        new CodeReviewLabel.NameKey("CodeReview-0")));
    crls.add(new CodeReviewLabel(cfg.registeredGroupId,
        new CodeReviewLabel.NameKey("CodeReview+1")));

    // The below labels are only for demo purpose.
    crls.add(new CodeReviewLabel(cfg.adminGroupId, new CodeReviewLabel.NameKey(
        "CodeReview-2")));
    crls.add(new CodeReviewLabel(cfg.adminGroupId, new CodeReviewLabel.NameKey(
        "CodeReview+2")));
    crls.add(new CodeReviewLabel(cfg.anonymousGroupId,
        new CodeReviewLabel.NameKey("CodeReview-0")));

    db.codeReviewLabels().insert(crls);
  }
}
