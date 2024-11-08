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

import com.google.gerrit.exceptions.StorageException;
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

    CachedPreferences pref = CachedPreferences.fromLegacyConfig(originalCfg);
    Config res = pref.asConfig();

    assertThat(res.toText()).isEqualTo(originalCfg.toText());
  }

  @Test
  public void gitConfig_getGeneralPreferences() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[general]\n\tchangesPerPage = 2");

    CachedPreferences pref = CachedPreferences.fromLegacyConfig(originalCfg);
    GeneralPreferencesInfo general = CachedPreferences.general(Optional.empty(), pref);

    GeneralPreferencesInfo expected = GeneralPreferencesInfo.defaults();
    expected.changesPerPage = 2;
    assertThat(cleanGeneralPreferences(general)).isEqualTo(expected);
  }

  @Test
  public void gitConfig_getDiffPreferences() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[diff]\n\tcontext = 3");

    CachedPreferences pref = CachedPreferences.fromLegacyConfig(originalCfg);
    DiffPreferencesInfo diff = CachedPreferences.diff(Optional.empty(), pref);

    DiffPreferencesInfo expected = DiffPreferencesInfo.defaults();
    expected.context = 3;
    assertThat(diff).isEqualTo(expected);
  }

  @Test
  public void gitConfig_getEditPreferences() throws Exception {
    Config originalCfg = new Config();
    originalCfg.fromText("[edit]\n\ttabSize = 5");

    CachedPreferences pref = CachedPreferences.fromLegacyConfig(originalCfg);
    EditPreferencesInfo edit = CachedPreferences.edit(Optional.empty(), pref);

    EditPreferencesInfo expected = EditPreferencesInfo.defaults();
    expected.tabSize = 5;
    assertThat(edit).isEqualTo(expected);
  }

  @Test
  public void userPreferencesProto_roundTrip() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setGeneralPreferencesInfo(
                UserPreferences.GeneralPreferencesInfo.newBuilder().setChangesPerPage(7))
            .build();

    CachedPreferences pref = CachedPreferences.fromUserPreferencesProto(originalProto);
    UserPreferences res = pref.asUserPreferencesProto();

    assertThat(res).isEqualTo(originalProto);
  }

  @Test
  public void userPreferencesProto_getGeneralPreferences() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setGeneralPreferencesInfo(
                UserPreferences.GeneralPreferencesInfo.newBuilder().setChangesPerPage(11))
            .build();

    CachedPreferences pref = CachedPreferences.fromUserPreferencesProto(originalProto);
    GeneralPreferencesInfo general = CachedPreferences.general(Optional.empty(), pref);

    GeneralPreferencesInfo expected = GeneralPreferencesInfo.defaults();
    expected.changesPerPage = 11;
    assertThat(cleanGeneralPreferences(general)).isEqualTo(expected);
  }

  @Test
  public void userPreferencesProto_getDiffPreferences() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setDiffPreferencesInfo(UserPreferences.DiffPreferencesInfo.newBuilder().setContext(13))
            .build();

    CachedPreferences pref = CachedPreferences.fromUserPreferencesProto(originalProto);
    DiffPreferencesInfo diff = CachedPreferences.diff(Optional.empty(), pref);

    DiffPreferencesInfo expected = DiffPreferencesInfo.defaults();
    expected.context = 13;
    assertThat(diff).isEqualTo(expected);
  }

  @Test
  public void userPreferencesProto_getEditPreferences() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setEditPreferencesInfo(UserPreferences.EditPreferencesInfo.newBuilder().setTabSize(17))
            .build();

    CachedPreferences pref = CachedPreferences.fromUserPreferencesProto(originalProto);
    EditPreferencesInfo edit = CachedPreferences.edit(Optional.empty(), pref);

    EditPreferencesInfo expected = EditPreferencesInfo.defaults();
    expected.tabSize = 17;
    assertThat(edit).isEqualTo(expected);
  }

  @Test
  public void userPreferencesProto_falseValueReturnsAsNull() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setEditPreferencesInfo(
                UserPreferences.EditPreferencesInfo.newBuilder()
                    .setTabSize(17)
                    .setHideTopMenu(false)
                    .setHideLineNumbers(false)
                    .setAutoCloseBrackets(true))
            .build();

    CachedPreferences pref = CachedPreferences.fromUserPreferencesProto(originalProto);
    EditPreferencesInfo edit = CachedPreferences.edit(Optional.empty(), pref);

    assertThat(edit.tabSize).isEqualTo(17);
    assertThat(edit.hideTopMenu).isNull();
    assertThat(edit.hideLineNumbers).isNull();
    assertThat(edit.autoCloseBrackets).isTrue();
  }

  @Test
  public void bothPreferencesTypes_getGeneralPreferencesAreEqual() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setGeneralPreferencesInfo(
                UserPreferences.GeneralPreferencesInfo.newBuilder()
                    .setChangesPerPage(19)
                    .setAllowAutocompletingComments(false))
            .build();
    Config originalCfg = new Config();
    originalCfg.fromText("[general]\n\tchangesPerPage = 19\n\tallowAutocompletingComments = false");

    CachedPreferences protoPref = CachedPreferences.fromUserPreferencesProto(originalProto);
    GeneralPreferencesInfo protoGeneral = CachedPreferences.general(Optional.empty(), protoPref);
    CachedPreferences cfgPref = CachedPreferences.fromLegacyConfig(originalCfg);
    GeneralPreferencesInfo cfgGeneral = CachedPreferences.general(Optional.empty(), cfgPref);

    assertThat(protoGeneral).isEqualTo(cfgGeneral);
  }

  @Test
  public void bothPreferencesTypes_getDiffPreferencesAreEqual() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setDiffPreferencesInfo(
                UserPreferences.DiffPreferencesInfo.newBuilder()
                    .setContext(23)
                    .setHideTopMenu(false))
            .build();
    Config originalCfg = new Config();
    originalCfg.fromText("[diff]\n\tcontext = 23\n\thideTopMenu = false");

    CachedPreferences protoPref = CachedPreferences.fromUserPreferencesProto(originalProto);
    DiffPreferencesInfo protoDiff = CachedPreferences.diff(Optional.empty(), protoPref);
    CachedPreferences cfgPref = CachedPreferences.fromLegacyConfig(originalCfg);
    DiffPreferencesInfo cfgDiff = CachedPreferences.diff(Optional.empty(), cfgPref);

    assertThat(protoDiff).isEqualTo(cfgDiff);
  }

  @Test
  public void bothPreferencesTypes_getEditPreferencesAreEqual() throws Exception {
    UserPreferences originalProto =
        UserPreferences.newBuilder()
            .setEditPreferencesInfo(
                UserPreferences.EditPreferencesInfo.newBuilder()
                    .setTabSize(27)
                    .setAutoCloseBrackets(true))
            .build();
    Config originalCfg = new Config();
    originalCfg.fromText("[edit]\n\ttabSize = 27\n\tautoCloseBrackets = true");

    CachedPreferences protoPref = CachedPreferences.fromUserPreferencesProto(originalProto);
    EditPreferencesInfo protoEdit = CachedPreferences.edit(Optional.empty(), protoPref);
    CachedPreferences cfgPref = CachedPreferences.fromLegacyConfig(originalCfg);
    EditPreferencesInfo cfgEdit = CachedPreferences.edit(Optional.empty(), cfgPref);

    assertThat(protoEdit).isEqualTo(cfgEdit);
  }

  @Test
  public void defaultPreferences_acceptingGitConfig() throws Exception {
    Config cfg = new Config();
    cfg.fromText("[general]\n\tchangesPerPage = 19");
    CachedPreferences defaults = CachedPreferences.fromLegacyConfig(cfg);
    CachedPreferences userPreferences =
        CachedPreferences.fromUserPreferencesProto(UserPreferences.getDefaultInstance());

    assertThat(CachedPreferences.general(Optional.of(defaults), userPreferences)).isNotNull();
    assertThat(CachedPreferences.diff(Optional.of(defaults), userPreferences)).isNotNull();
    assertThat(CachedPreferences.edit(Optional.of(defaults), userPreferences)).isNotNull();
  }

  @Test
  public void defaultPreferences_throwingForProto() throws Exception {
    CachedPreferences defaults =
        CachedPreferences.fromUserPreferencesProto(UserPreferences.getDefaultInstance());
    CachedPreferences userPreferences =
        CachedPreferences.fromUserPreferencesProto(UserPreferences.getDefaultInstance());
    assertThrows(
        StorageException.class,
        () -> CachedPreferences.general(Optional.of(defaults), userPreferences));
    assertThrows(
        StorageException.class,
        () -> CachedPreferences.diff(Optional.of(defaults), userPreferences));
    assertThrows(
        StorageException.class,
        () -> CachedPreferences.edit(Optional.of(defaults), userPreferences));
  }

  /**
   * {@link PreferencesParserUtil#parseGeneralPreferences} sets explicit values to {@link
   * GeneralPreferencesInfo#my} and {@link GeneralPreferencesInfo#changeTable} in case of null
   * defaults. Set these back to {@code null} for comparing with the defaults.
   */
  private static GeneralPreferencesInfo cleanGeneralPreferences(GeneralPreferencesInfo pref) {
    pref.my = null;
    pref.changeTable = null;
    return pref;
  }
}
