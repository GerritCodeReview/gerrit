// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.reviewdb.client.AccountGroup.UUID.fromRef;
import static com.google.gerrit.reviewdb.client.AccountGroup.UUID.fromRefPart;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.Test;

public class AccountGroupTest {
  @Test
  public void auditCreationInstant() {
    Instant instant = LocalDateTime.of(2009, Month.JUNE, 8, 19, 31).toInstant(ZoneOffset.UTC);
    assertThat(AccountGroup.auditCreationInstantTs()).isEqualTo(Timestamp.from(instant));
  }

  @Test
  public void parseRefName() {
    assertThat(fromRef("refs/groups/cc/ccab3195282a8ce4f5014efa391e82d10f884c64"))
        .isEqualTo(uuid("ccab3195282a8ce4f5014efa391e82d10f884c64"));
    assertThat(fromRef("refs/groups/cc/ccab3195282a8ce4f5014efa391e82d10f884c64-2"))
        .isEqualTo(uuid("ccab3195282a8ce4f5014efa391e82d10f884c64-2"));
    assertThat(fromRef("refs/groups/7e/7ec4775d")).isEqualTo(uuid("7ec4775d"));
    assertThat(fromRef("refs/groups/fo/foo")).isEqualTo(uuid("foo"));

    assertThat(fromRef(null)).isNull();
    assertThat(fromRef("")).isNull();

    // Missing prefix.
    assertThat(fromRef("cc/ccab3195282a8ce4f5014efa391e82d10f884c64")).isNull();

    // Invalid shards.
    assertThat(fromRef("refs/groups/c/ccab3195282a8ce4f5014efa391e82d10f884c64")).isNull();
    assertThat(fromRef("refs/groups/cca/ccab3195282a8ce4f5014efa391e82d10f884c64")).isNull();

    // Mismatched shard.
    assertThat(fromRef("refs/groups/ca/ccab3195282a8ce4f5014efa391e82d10f884c64")).isNull();
    assertThat(fromRef("refs/groups/64/ccab3195282a8ce4f5014efa391e82d10f884c64")).isNull();

    // Wrong number of segments.
    assertThat(fromRef("refs/groups/cc")).isNull();
    assertThat(fromRef("refs/groups/cc/ccab3195282a8ce4f5014efa391e82d10f884c64/1")).isNull();
  }

  @Test
  public void parseRefNameParts() {
    assertThat(fromRefPart("cc/ccab3195282a8ce4f5014efa391e82d10f884c64"))
        .isEqualTo(uuid("ccab3195282a8ce4f5014efa391e82d10f884c64"));
    assertThat(fromRefPart("ab/ccab3195282a8ce4f5014efa391e82d10f884c64")).isNull();
  }

  private AccountGroup.UUID uuid(String uuid) {
    return new AccountGroup.UUID(uuid);
  }
}
