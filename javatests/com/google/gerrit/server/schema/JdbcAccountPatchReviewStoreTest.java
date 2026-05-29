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

import java.nio.file.Path;
import org.junit.Test;

public class JdbcAccountPatchReviewStoreTest {
  @Test
  public void checkCreateH2Url() {
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test")))
        .isEqualTo("jdbc:h2:file:" + Path.of("test").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db")))
        .isEqualTo("jdbc:h2:file:" + Path.of("test.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db")))
        .isEqualTo("jdbc:h2:file:" + Path.of("test.db.mv.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db.trace.db")))
        .isEqualTo("jdbc:h2:file:" + Path.of("test.db.mv.db.trace.db").toAbsolutePath());
  }

  @Test
  public void checkCreateH2UrlWithPath() {
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test")))
        .isEqualTo("jdbc:h2:file:" + Path.of("path/to/test").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test.db")))
        .isEqualTo("jdbc:h2:file:" + Path.of("path/to/test.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test.db.mv.db")))
        .isEqualTo("jdbc:h2:file:" + Path.of("path/to/test.db.mv.db").toAbsolutePath());
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("path/to/test.db.mv.db.trace.db")))
        .isEqualTo("jdbc:h2:file:" + Path.of("path/to/test.db.mv.db.trace.db").toAbsolutePath());
  }

  @Test
  public void checkCreateH2UrlWithSemicolon() {
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test;test")))
        .isEqualTo(
            "jdbc:h2:file:" + Path.of("test;test").toAbsolutePath().toString().replace(";", "\\;"));
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db;test")))
        .isEqualTo(
            "jdbc:h2:file:"
                + Path.of("test.db;test").toAbsolutePath().toString().replace(";", "\\;"));
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db;test")))
        .isEqualTo(
            "jdbc:h2:file:"
                + Path.of("test.db.mv.db;test").toAbsolutePath().toString().replace(";", "\\;"));
    assertThat(JdbcAccountPatchReviewStore.createH2Url(Path.of("test.db.mv.db.trace.db;test")))
        .isEqualTo(
            "jdbc:h2:file:"
                + Path.of("test.db.mv.db.trace.db;test")
                    .toAbsolutePath()
                    .toString()
                    .replace(";", "\\;"));
  }
}
