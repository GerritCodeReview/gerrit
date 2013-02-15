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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LabelTypesProvider implements Provider<LabelTypes> {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  LabelTypesProvider(final SchemaFactory<ReviewDb> sf) {
    schema = sf;
  }

  @Override
  public LabelTypes get() {
    List<LabelType> types = new ArrayList<LabelType>(2);

    try {
      final ReviewDb db = schema.open();
      try {
        for (final ApprovalCategory c : db.approvalCategories().all()) {
          final List<ApprovalCategoryValue> values =
              db.approvalCategoryValues().byCategory(c.getId()).toList();
          types.add(LabelType.fromApprovalCategory(c, values));
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot query label categories", e);
    }

    return new LabelTypes(Collections.unmodifiableList(types));
  }
}
