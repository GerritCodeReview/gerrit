// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.config.SitePaths;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JdbcAccountPatchReviewStoreTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void checkCreateH2Url() {
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test")))
        .isEqualTo("jdbc:h2:async:" + Path.of("test").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db")))
        .isEqualTo("jdbc:h2:async:" + Path.of("test.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db")))
        .isEqualTo("jdbc:h2:async:" + Path.of("test.db.mv.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db.trace.db")))
        .isEqualTo("jdbc:h2:async:" + Path.of("test.db.mv.db.trace.db").toAbsolutePath());
  }

  @Test
  public void checkCreateH2UrlWithPath() {
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test")))
        .isEqualTo("jdbc:h2:async:" + Path.of("path/to/test").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test.db")))
        .isEqualTo("jdbc:h2:async:" + Path.of("path/to/test.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test.db.mv.db")))
        .isEqualTo("jdbc:h2:async:" + Path.of("path/to/test.db.mv.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test.db.mv.db.trace.db")))
        .isEqualTo("jdbc:h2:async:" + Path.of("path/to/test.db.mv.db.trace.db").toAbsolutePath());
  }

  @Test
  public void checkCreateH2UrlWithSemicolon() {
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test;test")))
        .isEqualTo(
            "jdbc:h2:async:"
                + Path.of("test;test").toAbsolutePath().toString().replace(";", "\\;"));
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db;test")))
        .isEqualTo(
            "jdbc:h2:async:"
                + Path.of("test.db;test").toAbsolutePath().toString().replace(";", "\\;"));
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db;test")))
        .isEqualTo(
            "jdbc:h2:async:"
                + Path.of("test.db.mv.db;test").toAbsolutePath().toString().replace(";", "\\;"));
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db.trace.db;test")))
        .isEqualTo(
            "jdbc:h2:async:"
                + Path.of("test.db.mv.db.trace.db;test")
                    .toAbsolutePath()
                    .toString()
                    .replace(";", "\\;"));
  }

  @Test
  public void normalizeH2Url_preservesAsyncUrl() {
    assertThat(JdbcAccountPatchReviewStore.normalizeH2Url("jdbc:h2:async:/path/to/db;FILE_LOCK=NO"))
        .isEqualTo("jdbc:h2:async:/path/to/db;FILE_LOCK=NO");
  }

  @Test
  public void normalizeH2Url_convertsLegacyFileUrlToAsyncUrl() {
    assertThat(JdbcAccountPatchReviewStore.normalizeH2Url("jdbc:h2:file:/path/to/db;FILE_LOCK=NO"))
        .isEqualTo("jdbc:h2:async:/path/to/db;FILE_LOCK=NO");
  }

  @Test
  public void getUrl_normalizesLegacyFileUrlFromConfig() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        JdbcAccountPatchReviewStore.ACCOUNT_PATCH_REVIEW_DB,
        null,
        "url",
        "jdbc:h2:file:/path/to/db;FILE_LOCK=NO");

    assertThat(
            JdbcAccountPatchReviewStore.getUrl(
                cfg, new SitePaths(temporaryFolder.getRoot().toPath())))
        .isEqualTo("jdbc:h2:async:/path/to/db;FILE_LOCK=NO");
  }

  @Test
  public void normalizeH2Url_preservesNonFileH2Url() {
    assertThat(JdbcAccountPatchReviewStore.normalizeH2Url("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"))
        .isEqualTo("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
  }

  @Test
  public void createH2Url_canCreateAndReadAsyncH2Database() throws Exception {
    String url =
        JdbcAccountPatchReviewStore.createH2Url(
            temporaryFolder.newFolder("db").toPath().resolve("account_patch_reviews"));

    try (Connection con = DriverManager.getConnection(url);
        Statement stmt = con.createStatement()) {
      stmt.executeUpdate("CREATE TABLE account_patch_reviews (id INT PRIMARY KEY, path VARCHAR)");
      stmt.executeUpdate("INSERT INTO account_patch_reviews VALUES (1, 'README.md')");
    }

    try (Connection con = DriverManager.getConnection(url);
        Statement stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT path FROM account_patch_reviews WHERE id = 1")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("README.md");
      assertThat(rs.next()).isFalse();
    }
  }
}
