// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;

public class Schema_80 extends SchemaVersion {

  @Inject
  Schema_80(Provider<Schema_79> prior) {
    super(prior);
  }

  @Override
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {
    final JdbcSchema s = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(s);
    //rename table ACCOUNT_GROUP_INCLUDES_BY_UUID
    s.renameTable(e, "ACCOUNT_GROUP_INCLUDES_BY_UUID", "ACCOUNT_GROUP_BY_ID");
    //rename table ACCOUNT_GROUP_INCLUDES_BY_UUID_AUDIT
    s.renameTable(e, "ACCOUNT_GROUP_INCLUDES_BY_UUID_AUDIT", "ACCOUNT_GROUP_BY_ID_AUD");
    //rename column in table ACCOUNTS
    s.renameColumn(e, "ACCOUNTS", "show_username_in_review_category", "show_user_in_review");
  }
}
