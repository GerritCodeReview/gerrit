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
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.workflow.NoOpFunction;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;

public class Schema_53 extends SchemaVersion {
  @Inject
  Schema_53(Provider<Schema_52> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    initCreateProjectCategory(db);
  }

  private void initCreateProjectCategory(final ReviewDb c) throws OrmException {
    final SystemConfig cfg = c.systemConfig().get(new SystemConfig.Key());
    final Project.NameKey wildProject = cfg.wildProjectName;
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat =
        new ApprovalCategory(ApprovalCategory.CREATE_PROJECT, "Create Project");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Create Projects under this"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);

    final RefRight.RefPattern pattern = new RefRight.RefPattern(RefRight.ALL);
    final RefRight approve =
        new RefRight(new RefRight.Key(wildProject, pattern, cat.getId(),
            cfg.adminGroupId));
    approve.setMaxValue((short) 1);
    approve.setMinValue((short) 1);
    c.refRights().insert(Collections.singleton(approve));
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
