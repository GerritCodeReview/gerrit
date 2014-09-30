// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.cache.h2.H2CacheFactory;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.change.MergeabilityCache.EntryKey;
import com.google.gerrit.server.change.MergeabilityCache.EntryVal;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.SqlBooleanTypeInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class Schema_100 extends SchemaVersion {
  private final Config cfg;
  private final SitePaths site;

  @Inject
  Schema_100(Provider<Schema_99> prior,
      @GerritServerConfig Config cfg,
      SitePaths site) {
    super(prior);
    this.cfg = cfg;
    this.site = site;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    File cacheDir = DefaultCacheFactory.getCacheDir(cfg, site);
    if (cacheDir == null) {
      // TODO(dborowitz): Provide a way to recheck mergeability only for open
      // changes without reindexing everything.
      ui.message("\nCan't create mergeability cache.\n"
          + "To recheck mergeability for all changes:\n"
          + "  java -jar gerrit.war reindex --recheck-mergeable -d site_path");
      return;
    }

    String trueValue = new SqlBooleanTypeInfo().getTrueValue();
    try (SqlStore<EntryKey, EntryVal> store = newSqlStore(cacheDir);
        Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT change_id, current_patch_set_id, last_sha1_merge_tested, " +
            "       mergeable " +
            "FROM changes " +
            "WHERE status != '" + Change.STATUS_MERGED + "'")) {
      while (rs.next()) {
        store.put(new EntryKey(newChange(rs.getInt(1), rs.getInt(2))),
            new EntryVal(
                ObjectId.fromString(rs.getString(3)),
                rs.getString(4).equals(trueValue)));
      }
    }
  }

  private SqlStore<EntryKey, EntryVal> newSqlStore(File cacheDir) {
    String name = MergeabilityCache.CACHE_NAME;
    return H2CacheFactory.newSqlStore(
        cacheDir, name, new TypeLiteral<EntryKey>() {},
        cfg.getInt("cache", name, "diskLimit", 128 << 10),
        null);

  }

  private static Change newChange(int id, int psId) {
    Change c = new Change(
        new Change.Key("INVALID"),
        new Change.Id(id),
        new Account.Id(0),
        new Branch.NameKey(new Project.NameKey("INVALID"), "INVALID"),
        new Timestamp(0));
    c.setCurrentPatchSet(new PatchSetInfo(new PatchSet.Id(c.getId(), psId)));
    return c;
  }
}
