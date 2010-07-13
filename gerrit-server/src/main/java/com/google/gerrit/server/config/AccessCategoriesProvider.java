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

import com.google.gerrit.reviewdb.AccessCategory;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.HashMap;
import java.util.Map;

class AccessCategoriesProvider implements Provider<Map<AccessCategory.Id, AccessCategory>> {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  AccessCategoriesProvider(final SchemaFactory<ReviewDb> sf) {
    schema = sf;
  }

  @Override
  public Map<AccessCategory.Id, AccessCategory> get() {
    Map<AccessCategory.Id, AccessCategory> accessCategories =
        new HashMap<AccessCategory.Id, AccessCategory>();

    try {
      final ReviewDb db = schema.open();
      try {
        for (AccessCategory ac : db.accessCategories().all()) {
          accessCategories.put(ac.getId(), ac);
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot query approval categories", e);
    }

    return accessCategories;
  }
}
