// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.ExternalIdsConfig.SCHEME_EXTERNAL;
import static com.google.gerrit.server.account.ExternalIdsConfig.SCHEME_GERRIT;
import static com.google.gerrit.server.account.ExternalIdsConfig.SCHEME_MAILTO;
import static com.google.gerrit.server.account.ExternalIdsConfig.SCHEME_USERNAME;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.server.account.ExternalIdsConfig.ExternalId;
import com.google.gerrit.server.account.ExternalIdsConfig.ExternalIdValue;

import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ExternalIdsConfigTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void parseExternalIdsConfig() throws Exception {
    Config cfg = new Config();
    cfg.fromText("[scheme \"" + SCHEME_GERRIT + "\"]\n"
        + "  id = jdoe\n"
        + "[scheme \"" + SCHEME_MAILTO + "\"]\n"
        + "  id = jdoe@example.com <jdoe@example.com>\n"
        + "  id = john.doe@example.com <john.doe@example.com>\n"
        + "[scheme \"" + SCHEME_USERNAME + "\"]\n"
        + "  id = jdoe : my-secret-password\n"
        + "[scheme \"" + SCHEME_EXTERNAL + "\"]\n"
        + "  id = john.doe <john.doe@example.com> : sekret\n"
        + "[scheme \"unknown\"]\n"
        + "  id = jdoe\n");
    Multimap<String, ExternalId> externalIds =
        ExternalIdsConfig.parse(cfg);

    Multimap<String, ExternalId> expectedExternalIds =
        ArrayListMultimap.create();
    expectedExternalIds.put(SCHEME_GERRIT,
        ExternalId.create(SCHEME_GERRIT, "jdoe", null, null));
    expectedExternalIds.put(SCHEME_MAILTO, ExternalId.create(SCHEME_MAILTO,
        "jdoe@example.com", "jdoe@example.com", null));
    expectedExternalIds.put(SCHEME_MAILTO, ExternalId.create(SCHEME_MAILTO,
        "john.doe@example.com", "john.doe@example.com", null));
    expectedExternalIds.put(SCHEME_USERNAME,
        ExternalId.create(SCHEME_USERNAME, "jdoe", null, "my-secret-password"));
    expectedExternalIds.put(SCHEME_EXTERNAL, ExternalId.create(SCHEME_EXTERNAL,
        "john.doe", "john.doe@example.com", "sekret"));
    expectedExternalIds.put("unknown",
        ExternalId.create("unknown", "jdoe", null, null));
    assertThat(externalIds).containsExactlyEntriesIn(expectedExternalIds);
  }

  @Test
  public void parseExternalIdsConfigWithMixedCaseSchemes() throws Exception {
    Config cfg = new Config();
    cfg.fromText("[scheme \"gerrit\"]\n"
        + "  id = jdoe\n"
        + "[scheme \"GERRIT\"]\n"
        + "  id = jroe\n"
        + "[scheme \"GeRRiT\"]\n"
        + "  id = jnoe\n");
    Multimap<String, ExternalId> externalIds = ExternalIdsConfig.parse(cfg);

    Multimap<String, ExternalId> expectedExternalIds =
        ArrayListMultimap.create();
    expectedExternalIds.put(SCHEME_GERRIT,
        ExternalId.create(SCHEME_GERRIT, "jdoe", null, null));
    expectedExternalIds.put(SCHEME_GERRIT,
        ExternalId.create(SCHEME_GERRIT, "jroe", null, null));
    expectedExternalIds.put(SCHEME_GERRIT,
        ExternalId.create(SCHEME_GERRIT, "jnoe", null, null));
    assertThat(externalIds).containsExactlyEntriesIn(expectedExternalIds);
  }


  @Test
  public void parseExternalIdValue() throws Exception {
    assertParseExternalIdValue("jdoe", "jdoe", null, null);
    assertParseExternalIdValue("jdoe <jdoe@example.com>", "jdoe",
        "jdoe@example.com", null);
    assertParseExternalIdValue("jdoe : super-secret-password", "jdoe", null,
        "super-secret-password");
    assertParseExternalIdValue(
        "jdoe <jdoe@example.com> : super-secret-password", "jdoe",
        "jdoe@example.com", "super-secret-password");
    assertParseExternalIdValue("jdoe <jdoe@example.com> : password with spaces",
        "jdoe", "jdoe@example.com", "password with spaces");
  }

  @Test
  public void toExternalIdValue() throws Exception {
    assertToExternalIdValue("jdoe", null, null, "jdoe");
    assertToExternalIdValue("jdoe", "jdoe@example.com", null,
        "jdoe <jdoe@example.com>");
    assertToExternalIdValue("jdoe", null, "super-secret-password",
        "jdoe : super-secret-password");
    assertToExternalIdValue("jdoe", "jdoe@example.com", "super-secret-password",
        "jdoe <jdoe@example.com> : super-secret-password");
  }

  private static void assertParseExternalIdValue(String externalIdValue,
      String expectedId, String expectedEmail, String expectedPassword) {
    ExternalIdValue v = ExternalIdValue.parse(externalIdValue);
    assertThat(v.id()).isEqualTo(expectedId);
    assertThat(v.email()).isEqualTo(expectedEmail);
    assertThat(v.password()).isEqualTo(expectedPassword);
  }

  private static void assertToExternalIdValue(String id, String email,
      String password, String expectedExternalIdValue) {
    ExternalIdValue v = ExternalIdValue.create(id, email, password);
    assertThat(v.toString()).isEqualTo(expectedExternalIdValue);
  }
}
