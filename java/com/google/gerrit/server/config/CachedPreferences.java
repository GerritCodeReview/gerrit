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
import com.google.common.base.Function;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.proto.Entities.UserPreferences;
import com.google.gerrit.server.cache.proto.Cache.CachedPreferencesProto;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Container class for preferences serialized as Git-style config files. Keeps the values as {@link
 * CachedPreferencesProto}s as they are immutable and thread-safe.
 *
 * <p>The config string wrapped by this class might represent different structures. See {@link
 * CachedPreferencesProto} for more details.
 */
@AutoValue
public abstract class CachedPreferences {
  public static final CachedPreferences EMPTY =
      fromCachedPreferencesProto(CachedPreferencesProto.getDefaultInstance());

  protected abstract CachedPreferencesProto config();

  public Optional<CachedPreferencesProto> nonEmptyConfig() {
    return config().equals(EMPTY.config()) ? Optional.empty() : Optional.of(config());
  }

  /** Returns a cache-able representation of the preferences proto. */
  public static CachedPreferences fromUserPreferencesProto(UserPreferences proto) {
    return fromCachedPreferencesProto(
        CachedPreferencesProto.newBuilder().setUserPreferences(proto).build());
  }

  /** Returns a cache-able representation of the git config. */
  public static CachedPreferences fromLegacyConfig(Config cfg) {
    return fromCachedPreferencesProto(
        CachedPreferencesProto.newBuilder().setLegacyGitConfig(cfg.toText()).build());
  }

  /** Returns a cache-able representation of the preferences proto. */
  public static CachedPreferences fromCachedPreferencesProto(
      @Nullable CachedPreferencesProto proto) {
    if (proto != null) {
      return new AutoValue_CachedPreferences(proto);
    }
    return EMPTY;
  }

  public static GeneralPreferencesInfo general(
      Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
    return getPreferences(
        defaultPreferences,
        userPreferences,
        PreferencesParserUtil::parseGeneralPreferences,
        p ->
            UserPreferencesConverter.GeneralPreferencesInfoConverter.fromProto(
                p.getGeneralPreferencesInfo()),
        GeneralPreferencesInfo.defaults());
  }

  public static DiffPreferencesInfo diff(
      Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
    return getPreferences(
        defaultPreferences,
        userPreferences,
        PreferencesParserUtil::parseDiffPreferences,
        p ->
            UserPreferencesConverter.DiffPreferencesInfoConverter.fromProto(
                p.getDiffPreferencesInfo()),
        DiffPreferencesInfo.defaults());
  }

  public static EditPreferencesInfo edit(
      Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
    return getPreferences(
        defaultPreferences,
        userPreferences,
        PreferencesParserUtil::parseEditPreferences,
        p ->
            UserPreferencesConverter.EditPreferencesInfoConverter.fromProto(
                p.getEditPreferencesInfo()),
        EditPreferencesInfo.defaults());
  }

  public Config asConfig() {
    try {
      switch (config().getPreferencesCase()) {
        case LEGACY_GIT_CONFIG:
        // continue below
        case PREFERENCES_NOT_SET:
          Config cfg = new Config();
          cfg.fromText(config().getLegacyGitConfig());
          return cfg;
        case USER_PREFERENCES:
          break;
      }
    } catch (ConfigInvalidException e) {
      throw new StorageException(e);
    }
    throw new StorageException(
        String.format(
            "Cannot parse the given config as a CachedPreferencesProto proto. Got [%s]", config()));
  }

  public UserPreferences asUserPreferencesProto() {
    if (config().hasUserPreferences()) {
      return config().getUserPreferences();
    }
    throw new StorageException(
        String.format(
            "Cannot parse the given config as a UserPreferences proto. Got [%s]", config()));
  }

  @Nullable
  private static Config configOrNull(Optional<CachedPreferences> cachedPreferences) {
    return cachedPreferences.map(CachedPreferences::asConfig).orElse(null);
  }

  @FunctionalInterface
  private interface ComputePreferencesFn<PreferencesT> {
    PreferencesT apply(Config cfg, @Nullable Config defaultCfg, @Nullable PreferencesT input)
        throws ConfigInvalidException;
  }

  private static <PreferencesT> PreferencesT getPreferences(
      Optional<CachedPreferences> defaultPreferences,
      CachedPreferences userPreferences,
      ComputePreferencesFn<PreferencesT> computePreferencesFn,
      Function<UserPreferences, PreferencesT> fromUserPreferencesFn,
      PreferencesT javaDefaults) {
    try {
      CachedPreferencesProto userPreferencesProto = userPreferences.config();
      switch (userPreferencesProto.getPreferencesCase()) {
        case USER_PREFERENCES:
          PreferencesT pref =
              fromUserPreferencesFn.apply(userPreferencesProto.getUserPreferences());
          return computePreferencesFn.apply(new Config(), configOrNull(defaultPreferences), pref);
        case LEGACY_GIT_CONFIG:
          return computePreferencesFn.apply(
              userPreferences.asConfig(), configOrNull(defaultPreferences), null);
        case PREFERENCES_NOT_SET:
          throw new ConfigInvalidException("Invalid config " + userPreferences);
      }
    } catch (ConfigInvalidException e) {
      return javaDefaults;
    }
    return javaDefaults;
  }
}
