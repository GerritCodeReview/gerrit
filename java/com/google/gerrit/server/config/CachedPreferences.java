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

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.proto.Entities.UserPreferences;
import com.google.gerrit.server.config.CachedPreferences.RepresentedPreferences.CachedPreferencesType;
import com.google.gerrit.server.config.UserPreferencesConverter.DiffPreferencesInfoConverter;
import com.google.gerrit.server.config.UserPreferencesConverter.EditPreferencesInfoConverter;
import com.google.gerrit.server.config.UserPreferencesConverter.GeneralPreferencesInfoConverter;
import com.google.protobuf.TextFormat;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Container class for preferences serialized as Git-style config files. Keeps the values as {@link
 * String}s as they are immutable and thread-safe.
 *
 * <p>The config string wrapped by this class might represent different structures. See {@link
 * RepresentedPreferences} for more details.
 */
@AutoValue
public abstract class CachedPreferences {
  /**
   * A structural representation of the CachedPreferences.
   *
   * <p>NOTE default preferences are assumed to always be of GIT_CONFIG type.
   */
  @AutoOneOf(RepresentedPreferences.CachedPreferencesType.class)
  abstract static class RepresentedPreferences {
    private static final String TYPE_DELIMITER = "|";

    public enum CachedPreferencesType {
      GIT_CONFIG,
      USER_PREFERENCES_PROTO
    }

    abstract CachedPreferencesType getCachedPreferencesType();

    abstract Config gitConfig();

    abstract UserPreferences userPreferencesProto();

    String asTypedString() {
      String str;
      switch (getCachedPreferencesType()) {
        case GIT_CONFIG:
          str = gitConfig().toText();
          break;
        case USER_PREFERENCES_PROTO:
          str = userPreferencesProto().toString();
          break;
        default:
          throw new IllegalStateException(
              "RepresentedPreferences must have a known type. Got " + getCachedPreferencesType());
      }
      return Joiner.on(TYPE_DELIMITER).join(getCachedPreferencesType().toString(), str);
    }

    static RepresentedPreferences fromTypedString(String str) throws ConfigInvalidException {
      try {
        List<String> parts = Splitter.on(TYPE_DELIMITER).limit(2).splitToList(str);
        CachedPreferencesType prefType = CachedPreferencesType.valueOf(parts.get(0));
        switch (prefType) {
          case GIT_CONFIG:
            Config cfg = new Config();
            cfg.fromText(parts.get(1));
            return AutoOneOf_CachedPreferences_RepresentedPreferences.gitConfig(cfg);
          case USER_PREFERENCES_PROTO:
            UserPreferences.Builder builder = UserPreferences.newBuilder();
            TextFormat.merge(parts.get(1), builder);
            return AutoOneOf_CachedPreferences_RepresentedPreferences.userPreferencesProto(
                builder.build());
        }
        throw new IllegalStateException(
            "RepresentedPreferences must have a known type. Got " + prefType);
      } catch (Exception e) {
        throw new ConfigInvalidException(
            String.format("Cannot parse serialized config [%s]", str), e);
      }
    }

    static GeneralPreferencesInfo general(
        Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
      return getPreferences(
          defaultPreferences,
          userPreferences,
          PreferencesParserUtil::parseGeneralPreferences,
          p -> GeneralPreferencesInfoConverter.fromProto(p.getGeneralPreferencesInfo()),
          GeneralPreferencesInfo.defaults());
    }

    static DiffPreferencesInfo diff(
        Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
      return getPreferences(
          defaultPreferences,
          userPreferences,
          PreferencesParserUtil::parseDiffPreferences,
          p -> DiffPreferencesInfoConverter.fromProto(p.getDiffPreferencesInfo()),
          DiffPreferencesInfo.defaults());
    }

    static EditPreferencesInfo edit(
        Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
      return getPreferences(
          defaultPreferences,
          userPreferences,
          PreferencesParserUtil::parseEditPreferences,
          p -> EditPreferencesInfoConverter.fromProto(p.getEditPreferencesInfo()),
          EditPreferencesInfo.defaults());
    }

    private static void verifyDefaultConfigIsGitConfig(
        Optional<CachedPreferences> defaultPreferences) {
      try {
        if (defaultPreferences.isEmpty()) {
          return;
        }
        RepresentedPreferences pref = fromTypedString(defaultPreferences.get().config());
        if (pref.getCachedPreferencesType() != CachedPreferencesType.GIT_CONFIG) {
          throw new IllegalStateException(
              "Default preferences must be of type GIT_CONFIG. If you are changing this assumption,"
                  + " please refactor CachedPreferences class.");
        }
      } catch (ConfigInvalidException e) {
        // Do nothing as we don't care here if the Config object is valid.
      }
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
      verifyDefaultConfigIsGitConfig(defaultPreferences);
      try {
        RepresentedPreferences representedUserPreferences =
            fromTypedString(userPreferences.config());
        switch (representedUserPreferences.getCachedPreferencesType()) {
          case GIT_CONFIG:
            return computePreferencesFn.apply(
                representedUserPreferences.gitConfig(), configOrNull(defaultPreferences), null);
          case USER_PREFERENCES_PROTO:
            PreferencesT pref =
                fromUserPreferencesFn.apply(representedUserPreferences.userPreferencesProto());
            return computePreferencesFn.apply(new Config(), configOrNull(defaultPreferences), pref);
        }
        throw new ConfigInvalidException(
            "Invalid config type " + representedUserPreferences.getCachedPreferencesType());
      } catch (ConfigInvalidException e) {
        return javaDefaults;
      }
    }
  }

  public static CachedPreferences EMPTY = fromString("");

  public abstract String config();

  /** Returns a cache-able representation of the git config. */
  public static CachedPreferences fromConfig(Config cfg) {
    return new AutoValue_CachedPreferences(
        AutoOneOf_CachedPreferences_RepresentedPreferences.gitConfig(cfg).asTypedString());
  }

  /** Returns a cache-able representation of the preferences proto. */
  public static CachedPreferences fromProto(UserPreferences proto) {
    return new AutoValue_CachedPreferences(
        AutoOneOf_CachedPreferences_RepresentedPreferences.userPreferencesProto(proto)
            .asTypedString());
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
    return RepresentedPreferences.general(defaultPreferences, userPreferences);
  }

  public static DiffPreferencesInfo diff(
      Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
    return RepresentedPreferences.diff(defaultPreferences, userPreferences);
  }

  public static EditPreferencesInfo edit(
      Optional<CachedPreferences> defaultPreferences, CachedPreferences userPreferences) {
    return RepresentedPreferences.edit(defaultPreferences, userPreferences);
  }

  public Config asConfig() {
    try {
      RepresentedPreferences representedPreferences =
          RepresentedPreferences.fromTypedString(config());
      if (representedPreferences.getCachedPreferencesType() != CachedPreferencesType.GIT_CONFIG) {
        throw new ConfigInvalidException(
            "`asConfig` can only be called for Git Config based preferences. Called with: "
                + representedPreferences.getCachedPreferencesType());
      }
      return representedPreferences.gitConfig();
    } catch (ConfigInvalidException e) {
      // Programmer error: We have parsed this config before and are unable to parse it now.
      throw new StorageException(e);
    }
  }

  public UserPreferences asProto() {
    try {
      RepresentedPreferences representedPreferences =
          RepresentedPreferences.fromTypedString(config());
      if (representedPreferences.getCachedPreferencesType()
          != CachedPreferencesType.USER_PREFERENCES_PROTO) {
        throw new ConfigInvalidException(
            "`asProto` can only be called for UserPreferences proto based preferences. Called with:"
                + " "
                + representedPreferences.getCachedPreferencesType());
      }
      return representedPreferences.userPreferencesProto();
    } catch (ConfigInvalidException e) {
      // Programmer error: We have parsed this config before and are unable to parse it now.
      throw new StorageException(e);
    }
  }

  @Nullable
  private static Config configOrNull(Optional<CachedPreferences> cachedPreferences) {
    return cachedPreferences.map(CachedPreferences::asConfig).orElse(null);
  }
}
