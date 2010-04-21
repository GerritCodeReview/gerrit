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

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.workflow.NoOpFunction;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;

class Schema_28 extends SchemaVersion {
  @Inject
  Schema_28(Provider<Schema_27> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    final SystemConfig cfg = db.systemConfig().get(new SystemConfig.Key());
    ApprovalCategory cat;

    initForgeIdentityCategory(db, cfg);

    // Don't grant FORGE_COMMITTER to existing PUSH_HEAD rights. That
    // is considered a bug that we are fixing with this schema upgrade.
    // Administrators might need to relax permissions manually after the
    // upgrade if that forgery is critical to their workflow.

    cat = db.approvalCategories().get(ApprovalCategory.PUSH_TAG);
    if (cat != null && "Push Annotated Tag".equals(cat.getName())) {
      cat.setName("Push Tag");
      db.approvalCategories().update(Collections.singleton(cat));
    }

    // Since we deleted Push Tags +3, drop anything using +3 down to +2.
    //
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("UPDATE ref_rights SET max_value = "
          + ApprovalCategory.PUSH_TAG_ANNOTATED + " WHERE max_value >= 3");
      stmt.execute("UPDATE ref_rights SET min_value = "
          + ApprovalCategory.PUSH_TAG_ANNOTATED + " WHERE min_value >= 3");
    } finally {
      stmt.close();
    }
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
