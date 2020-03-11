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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/**
 * Parses/writes preferences from/to a {@link Config} file.
 *
 * <p>This is a low-level API. Read/write of preferences in a user branch should be done through
 * {@link AccountsUpdate} or {@link AccountConfig}.
 *
 * <p>The config file has separate sections for general, diff and edit preferences:
 *
 * <pre>
 *   [diff]
 *     hideTopMenu = true
 *   [edit]
 *     lineLength = 80
 * </pre>
 */
public class VersionedPreferences extends VersionedMetaData {
  public static final String PREFERENCES_CONFIG = "preferences.config";

  private final String ref;

  private Cache.UserPreferences preferences;

  public static VersionedPreferences forUser(Account.Id accountId) {
    return new VersionedPreferences(RefNames.refsUsers(accountId));
  }

  public static VersionedPreferences defaults() {
    return new VersionedPreferences(RefNames.REFS_USERS_DEFAULT);
  }

  private VersionedPreferences(String ref) {
    this.ref = ref;
  }

  public Cache.UserPreferences getPreferences() {
    checkState(preferences != null, "Preferences not loaded yet.");
    return preferences;
  }

  public void setPreferences(Cache.UserPreferences preferences) {
    this.preferences = preferences;
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    preferences = readFromConfig(readConfig(PREFERENCES_CONFIG));
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Update preferences\n");
    }
    saveConfig(PREFERENCES_CONFIG, writeToConfig(preferences));
    return true;
  }

  @VisibleForTesting
  static Cache.UserPreferences readFromConfig(Config cfg) {
    Cache.UserPreferences.Builder preferencesBuilder = Cache.UserPreferences.newBuilder();
    for (UserPreferenceSection section : UserPreferenceSection.values()) {
      String sectionName = section.key();
      for (String key : cfg.getNames(sectionName)) {
        String[] values = cfg.getStringList(sectionName, null, key);
        Cache.UserPreferences.RepeatedPreference repeated =
            Cache.UserPreferences.RepeatedPreference.newBuilder()
                .addAllValue(Arrays.asList(values))
                .build();
        put(section, preferencesBuilder, key, repeated);
      }
    }
    return preferencesBuilder.build();
  }

  @VisibleForTesting
  static Config writeToConfig(Cache.UserPreferences preferences) {
    Config cfg = new Config();
    for (UserPreferenceSection section : UserPreferenceSection.values()) {
      String sectionName = section.key();
      for (Map.Entry<String, Cache.UserPreferences.RepeatedPreference> entry :
          mapFor(section, preferences).entrySet()) {
        cfg.setStringList(sectionName, null, entry.getKey(), entry.getValue().getValueList());
      }
    }
    return cfg;
  }

  private static Map<String, Cache.UserPreferences.RepeatedPreference> mapFor(
      UserPreferenceSection section, Cache.UserPreferences prefs) {
    switch (section) {
      case GENERAL:
        return prefs.getGeneralMap();
      case DIFF:
        return prefs.getDiffMap();
      case EDIT:
        return prefs.getEditMap();
      default:
        throw new IllegalStateException("unknown key " + section);
    }
  }

  private static void put(
      UserPreferenceSection section,
      Cache.UserPreferences.Builder prefs,
      String key,
      Cache.UserPreferences.RepeatedPreference val) {
    switch (section) {
      case GENERAL:
        prefs.putGeneral(key, val);
        break;
      case DIFF:
        prefs.putDiff(key, val);
        break;
      case EDIT:
        prefs.putEdit(key, val);
        break;
      default:
        throw new IllegalStateException("unknown key " + section);
    }
  }
}
