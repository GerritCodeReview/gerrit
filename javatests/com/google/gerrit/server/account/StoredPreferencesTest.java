// Copyright (C) 2019 The Android Open Source Project
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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.server.git.ValidationError;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for parsing user preferences from Git. */
public class StoredPreferencesTest {

  enum Unknown {
    STATE
  }

  @Test
  public void ignoreUnknownAccountPreferencesWhenParsing() {
    ValidationError.Sink errorSink = Mockito.mock(ValidationError.Sink.class);
    StoredPreferences preferences =
        new StoredPreferences(Account.id(1), configWithUnknownEntries(), new Config(), errorSink);
    GeneralPreferencesInfo parsedPreferences = preferences.getGeneralPreferences();

    assertThat(parsedPreferences).isNotNull();
    assertThat(parsedPreferences.expandInlineDiffs).isTrue();
    verifyNoMoreInteractions(errorSink);
  }

  @Test
  public void ignoreUnknownDefaultAccountPreferencesWhenParsing() {
    ValidationError.Sink errorSink = Mockito.mock(ValidationError.Sink.class);
    StoredPreferences preferences =
        new StoredPreferences(Account.id(1), new Config(), configWithUnknownEntries(), errorSink);
    GeneralPreferencesInfo parsedPreferences = preferences.getGeneralPreferences();

    assertThat(parsedPreferences).isNotNull();
    assertThat(parsedPreferences.expandInlineDiffs).isTrue();
    verifyNoMoreInteractions(errorSink);
  }

  private static Config configWithUnknownEntries() {
    Config cfg = new Config();
    cfg.setBoolean("general", null, "expandInlineDiffs", true);
    cfg.setBoolean("general", null, "unknown", true);
    cfg.setEnum("general", null, "unknownenum", Unknown.STATE);
    cfg.setString("general", null, "unknownstring", "bla");
    return cfg;
  }
}
