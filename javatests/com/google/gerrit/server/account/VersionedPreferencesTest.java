// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.account.VersionedPreferences.readFromConfig;
import static com.google.gerrit.server.account.VersionedPreferences.writeToConfig;

import com.google.common.collect.ImmutableList;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/** Test for {@link VersionedPreferences}. */
public class VersionedPreferencesTest {
  @Test
  public void singlePreferenceRoundTrip() {
    for (String section : ImmutableList.of("general", "edit", "diff")) {
      Config cfg = new Config();
      cfg.setString(section, null, "prefName", "1");
      assertWithMessage("mismatch in " + section)
          .that(writeToConfig(readFromConfig(cfg)).toText())
          .isEqualTo(cfg.toText());
    }
  }

  @Test
  public void repeatedPreferenceRoundTrip() {
    for (String section : ImmutableList.of("general", "edit", "diff")) {
      Config cfg = new Config();
      cfg.setStringList(section, null, "prefName", ImmutableList.of("1", "foo"));
      assertWithMessage("mismatch in " + section)
          .that(writeToConfig(readFromConfig(cfg)).toText())
          .isEqualTo(cfg.toText());
    }
  }

  @Test
  public void multipleKeysPreferenceRoundTrip() {
    for (String section : ImmutableList.of("general", "edit", "diff")) {
      Config cfg = new Config();
      cfg.setStringList(section, null, "prefName", ImmutableList.of("1", "foo"));
      cfg.setStringList(section, null, "prefName2", ImmutableList.of("1", "foo"));
      assertWithMessage("mismatch in " + section)
          .that(writeToConfig(readFromConfig(cfg)).toText())
          .isEqualTo(cfg.toText());
    }
  }
}
