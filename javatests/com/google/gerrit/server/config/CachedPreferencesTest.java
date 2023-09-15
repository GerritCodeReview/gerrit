// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.proto.Entities.UserPreferences;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CachedPreferencesTest {
  @Test
  public void gitConfig_roundTrip() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[general]\n\tfoo = bar");

    CachedPreferences pref = CachedPreferences.fromConfig(originalCfg);
    Config res = pref.asConfig();

    assertThat(res.toText()).isEqualTo(originalCfg.toText());
  }

  @Test
  public void gitConfig_getGeneralPreferences() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[general]\n\tchangesPerPage = 2");

    CachedPreferences pref = CachedPreferences.fromConfig(originalCfg);
    GeneralPreferencesInfo general = CachedPreferences.general(Optional.empty(), pref);

    assertThat(general.changesPerPage).isEqualTo(2);
  }

  @Test
  public void gitConfig_getDiffPreferences() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[diff]\n\tcontext = 3");

    CachedPreferences pref = CachedPreferences.fromConfig(originalCfg);
    DiffPreferencesInfo diff = CachedPreferences.diff(Optional.empty(), pref);

    assertThat(diff.context).isEqualTo(3);
  }

  @Test
  public void gitConfig_getEditPreferences() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[edit]\n\ttabSize = 5");

    CachedPreferences pref = CachedPreferences.fromConfig(originalCfg);
    EditPreferencesInfo edit = CachedPreferences.edit(Optional.empty(), pref);

    assertThat(edit.tabSize).isEqualTo(5);
  }

  @Test
  public void userPreferencesProto_roundTrip() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setGeneralPreferencesInfo(
                UserPreferences.GeneralPreferencesInfo.newBuilder().setChangesPerPage(7))
            .build();

    CachedPreferences pref = CachedPreferences.fromProto(originalProto);
    UserPreferences res = pref.asProto();

    assertThat(res).isEqualTo(originalProto);
  }

  @Test
  public void userPreferencesProto_getGeneralPreferences() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setGeneralPreferencesInfo(
                UserPreferences.GeneralPreferencesInfo.newBuilder().setChangesPerPage(11))
            .build();

    CachedPreferences pref = CachedPreferences.fromProto(originalProto);
    GeneralPreferencesInfo general = CachedPreferences.general(Optional.empty(), pref);

    assertThat(general.changesPerPage).isEqualTo(11);
  }

  @Test
  public void userPreferencesProto_getDiffPreferences() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setDiffPreferencesInfo(UserPreferences.DiffPreferencesInfo.newBuilder().setContext(13))
            .build();

    CachedPreferences pref = CachedPreferences.fromProto(originalProto);
    DiffPreferencesInfo diff = CachedPreferences.diff(Optional.empty(), pref);

    assertThat(diff.context).isEqualTo(13);
  }

  @Test
  public void userPreferencesProto_getEditPreferences() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setEditPreferencesInfo(UserPreferences.EditPreferencesInfo.newBuilder().setTabSize(17))
            .build();

    CachedPreferences pref = CachedPreferences.fromProto(originalProto);
    EditPreferencesInfo edit = CachedPreferences.edit(Optional.empty(), pref);

    assertThat(edit.tabSize).isEqualTo(17);
  }

  @Test
  public void defaultPreferences_acceptingGitConfig() throws Exception {
    Config cfg = new Config();
    cfg.fromText("[general]\n\tchangesPerPage = 19");
    CachedPreferences defaults = CachedPreferences.fromConfig(cfg);
    CachedPreferences userPreferences =
        CachedPreferences.fromProto(UserPreferences.getDefaultInstance());

    assertThat(CachedPreferences.general(Optional.of(defaults), userPreferences)).isNotNull();
    assertThat(CachedPreferences.diff(Optional.of(defaults), userPreferences)).isNotNull();
    assertThat(CachedPreferences.edit(Optional.of(defaults), userPreferences)).isNotNull();
  }

  @Test
  public void defaultPreferences_throwingForProto() throws Exception {
    CachedPreferences defaults = CachedPreferences.fromProto(UserPreferences.getDefaultInstance());
    CachedPreferences userPreferences =
        CachedPreferences.fromProto(UserPreferences.getDefaultInstance());
    assertThrows(
        IllegalStateException.class,
        () -> CachedPreferences.general(Optional.of(defaults), userPreferences));
    assertThrows(
        IllegalStateException.class,
        () -> CachedPreferences.diff(Optional.of(defaults), userPreferences));
    assertThrows(
        IllegalStateException.class,
        () -> CachedPreferences.edit(Optional.of(defaults), userPreferences));
  }
}
