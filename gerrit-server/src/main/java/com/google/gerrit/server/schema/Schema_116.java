// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.reviewdb.client.CoreDownloadSchemes.ANON_GIT;
import static com.google.gerrit.reviewdb.client.CoreDownloadSchemes.ANON_HTTP;
import static com.google.gerrit.reviewdb.client.CoreDownloadSchemes.HTTP;
import static com.google.gerrit.reviewdb.client.CoreDownloadSchemes.REPO_DOWNLOAD;
import static com.google.gerrit.reviewdb.client.CoreDownloadSchemes.SSH;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class Schema_116 extends SchemaVersion {
  private final Map<String, String> legacyDisplayNameMap =
      ImmutableMap.<String, String> of(
          ANON_GIT, "ANON_GIT",
          ANON_HTTP, "ANON_HTTP",
          HTTP, "HTTP",
          SSH, "SSH",
          REPO_DOWNLOAD, "REPO_DOWNLOAD");

  @Inject
  Schema_116(Provider<Schema_115> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    try (Statement stmt =
        ((JdbcSchema) db).getConnection().createStatement()) {
      for (Map.Entry<String, String> e : legacyDisplayNameMap.entrySet()) {
        stmt.executeUpdate(String.format(
            "update accounts set download_url = '%s' " +
                "where download_url = '%s'",
            e.getKey(), e.getValue()));
      }
    }
  }
}
