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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApprovalTypesProvider implements Provider<ApprovalTypes> {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  ApprovalTypesProvider(final SchemaFactory<ReviewDb> sf) {
    schema = sf;
  }

  @Override
  public ApprovalTypes get() {
    List<ApprovalType> approvalTypes = new ArrayList<ApprovalType>(2);
    List<ApprovalType> actionTypes = new ArrayList<ApprovalType>(2);

    try {
      final ReviewDb db = schema.open();
      try {
        for (final ApprovalCategory c : db.approvalCategories().all()) {
          final List<ApprovalCategoryValue> values =
              db.approvalCategoryValues().byCategory(c.getId()).toList();
          final ApprovalType type = new ApprovalType(c, values);
          if (type.getCategory().isAction()) {
            actionTypes.add(type);
          } else {
            approvalTypes.add(type);
          }
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot query approval categories", e);
    }

    approvalTypes = Collections.unmodifiableList(approvalTypes);
    actionTypes = Collections.unmodifiableList(actionTypes);
    return new ApprovalTypes(approvalTypes, actionTypes);
  }
}
