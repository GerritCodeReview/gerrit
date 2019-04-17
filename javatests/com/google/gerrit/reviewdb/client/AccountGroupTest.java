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
import static com.google.gerrit.reviewdb.client.AccountGroup.uuid;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.Test;

public class AccountGroupTest {
  private static final String TEST_UUID = "ccab3195282a8ce4f5014efa391e82d10f884c64";
  private static final String TEST_SHARDED_UUID = TEST_UUID.substring(0, 2) + "/" + TEST_UUID;

  @Test
  public void auditCreationInstant() {
    Instant instant = LocalDateTime.of(2009, Month.JUNE, 8, 19, 31).toInstant(ZoneOffset.UTC);
    assertThat(AccountGroup.auditCreationInstantTs()).isEqualTo(Timestamp.from(instant));
  }

  @Test
  public void parseRefName() {
    assertThat(fromRef("refs/groups/" + TEST_SHARDED_UUID)).isEqualTo(uuid(TEST_UUID));
    assertThat(fromRef("refs/groups/" + TEST_SHARDED_UUID + "-2"))
        .isEqualTo(uuid(TEST_UUID + "-2"));
    assertThat(fromRef("refs/groups/7e/7ec4775d")).isEqualTo(uuid("7ec4775d"));
    assertThat(fromRef("refs/groups/fo/foo")).isEqualTo(uuid("foo"));

    assertThat(fromRef(null)).isNull();
    assertThat(fromRef("")).isNull();

    // Missing prefix.
    assertThat(fromRef(TEST_SHARDED_UUID)).isNull();

    // Invalid shards.
    assertThat(fromRef("refs/groups/c/" + TEST_UUID)).isNull();
    assertThat(fromRef("refs/groups/cca/" + TEST_UUID)).isNull();

    // Mismatched shard.
    assertThat(fromRef("refs/groups/ca/" + TEST_UUID)).isNull();
    assertThat(fromRef("refs/groups/64/" + TEST_UUID)).isNull();

    // Wrong number of segments.
    assertThat(fromRef("refs/groups/cc")).isNull();
    assertThat(fromRef("refs/groups/" + TEST_SHARDED_UUID + "/1")).isNull();
  }

  @Test
  public void parseRefNameParts() {
    assertThat(fromRefPart(TEST_SHARDED_UUID)).isEqualTo(uuid(TEST_UUID));

    // Mismatched shard.
    assertThat(fromRefPart("ab/" + TEST_UUID)).isNull();
  }

  @Test
  public void uuidToString() {
    assertThat(uuid("foo").toString()).isEqualTo("foo");
    assertThat(uuid("foo bar").toString()).isEqualTo("foo+bar");
    assertThat(uuid("foo:bar").toString()).isEqualTo("foo%3Abar");
  }

  @Test
  public void parseUuid() {
    assertThat(AccountGroup.UUID.parse("foo")).isEqualTo(uuid("foo"));
    assertThat(AccountGroup.UUID.parse("foo+bar")).isEqualTo(uuid("foo bar"));
    assertThat(AccountGroup.UUID.parse("foo%3Abar")).isEqualTo(uuid("foo:bar"));
  }

  @Test
  public void idToString() {
    assertThat(AccountGroup.id(123).toString()).isEqualTo("123");
  }

  @Test
  public void nameKeyToString() {
    assertThat(AccountGroup.nameKey("foo").toString()).isEqualTo("foo");
    assertThat(AccountGroup.nameKey("foo bar").toString()).isEqualTo("foo+bar");
    assertThat(AccountGroup.nameKey("foo:bar").toString()).isEqualTo("foo%3Abar");
  }
}
