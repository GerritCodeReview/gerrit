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
import com.google.protobuf.TextFormat;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Container class for preferences serialized as Git-style config files. Keeps the values as {@link
 * String}s as they are immutable and thread-safe.
 *
 * <p>The config string wrapped by this class might represent different structures. See {@link
 * CachedPreferencesProto} for more details.
 */
@AutoValue
public abstract class CachedPreferences {
  public static final CachedPreferences EMPTY = fromString("");

  public abstract String config();

  /** Returns a cache-able representation of the git config. */
  public static CachedPreferences fromConfig(Config cfg) {
    return fromProto(CachedPreferencesProto.newBuilder().setLegacyGitConfig(cfg.toText()).build());
  }

  /** Returns a cache-able representation of the preferences proto. */
  public static CachedPreferences fromUserPreferencesProto(UserPreferences proto) {
    return fromProto(CachedPreferencesProto.newBuilder().setUserPreferences(proto).build());
  }

  /**
   * Returns a cache-able representation of the config. To be used only when constructing a {@link
   * CachedPreferences} from a serialized, cached value.
   */
  public static CachedPreferences fromString(String cfg) {
    return new AutoValue_CachedPreferences(cfg);
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
      CachedPreferencesProto proto = asProto();
      if (proto.hasLegacyGitConfig()) {
        Config cfg = new Config();
        cfg.fromText(proto.getLegacyGitConfig());
        return cfg;
      }
    } catch (ConfigInvalidException e) {
      throw new StorageException(e);
    }
    throw new StorageException(
        String.format(
            "Cannot parse the given config as a CachedPreferencesProto proto. Got [%s]", config()));
  }

  public UserPreferences asUserPreferencesProto() {
    CachedPreferencesProto proto = asProto();
    if (proto.hasUserPreferences()) {
      return proto.getUserPreferences();
    }
    throw new StorageException(
        String.format("Cannot parse the given config as a UserPreferences proto. Got [%s]", proto));
  }

  private static CachedPreferences fromProto(CachedPreferencesProto proto) {
    return new AutoValue_CachedPreferences(proto.toString());
  }

  private CachedPreferencesProto asProto() {
    try {
      CachedPreferencesProto.Builder builder = CachedPreferencesProto.newBuilder();
      TextFormat.merge(config(), builder);
      if (builder
          .getPreferencesCase()
          .equals(CachedPreferencesProto.PreferencesCase.PREFERENCES_NOT_SET)) {
        // In case of an empty config, TextFormat will create an empty proto instead of throwing.
        builder.setLegacyGitConfig(config());
      }
      return builder.build();
    } catch (TextFormat.ParseException e) {
      return CachedPreferencesProto.newBuilder().setLegacyGitConfig(config()).build();
    }
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
      CachedPreferencesProto userPreferencesProto = userPreferences.asProto();
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
