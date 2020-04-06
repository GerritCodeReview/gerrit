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

package com.google.gerrit.server.config;

import com.google.auto.value.AutoValue;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.server.account.StoredPreferences;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Container class for preferences serialized as Git-style config files. Keeps the values as {@link
 * String}s as they are immutable and thread-safe.
 */
@AutoValue
public abstract class CachedPreferences {

  public abstract String config();

  /** Returns a cache-able representation of the config. */
  public static CachedPreferences fromConfig(Config cfg) {
    return new AutoValue_CachedPreferences(cfg.toText());
  }

  /**
   * Returns a cache-able representation of the config. To be used only when constructing a {@link
   * CachedPreferences} from a serialized, cached value.
   */
  public static CachedPreferences fromString(String cfg) {
    return new AutoValue_CachedPreferences(cfg);
  }

  public static GeneralPreferencesInfo general(
      CachedPreferences defaultPreferences, CachedPreferences userPreferences) {
    try {
      return StoredPreferences.parseGeneralPreferences(
          userPreferences.asConfig(), defaultPreferences.asConfig(), null);
    } catch (ConfigInvalidException e) {
      return GeneralPreferencesInfo.defaults();
    }
  }

  public static EditPreferencesInfo edit(
      CachedPreferences defaultPreferences, CachedPreferences userPreferences) {
    try {
      return StoredPreferences.parseEditPreferences(
          userPreferences.asConfig(), defaultPreferences.asConfig(), null);
    } catch (ConfigInvalidException e) {
      return EditPreferencesInfo.defaults();
    }
  }

  public static DiffPreferencesInfo diff(
      CachedPreferences defaultPreferences, CachedPreferences userPreferences) {
    try {
      return StoredPreferences.parseDiffPreferences(
          userPreferences.asConfig(), defaultPreferences.asConfig(), null);
    } catch (ConfigInvalidException e) {
      return DiffPreferencesInfo.defaults();
    }
  }

  public Config asConfig() {
    Config cfg = new Config();
    try {
      cfg.fromText(config());
    } catch (ConfigInvalidException e) {
      // Programmer error: We have parsed this config before and are unable to parse it now.
      throw new StorageException(e);
    }
    return cfg;
  }
}
