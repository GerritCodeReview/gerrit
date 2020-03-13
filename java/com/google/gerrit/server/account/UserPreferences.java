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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.Arrays;
import java.util.Map;

/** Helpers for combining user preferences with defaults. */
public interface UserPreferences {

  Cache.UserPreferences preferences();

  static User create(Cache.UserPreferences preferences) {
    return new AutoValue_UserPreferences_User(preferences);
  }

  static Default defaults(Cache.UserPreferences preferences) {
    return new AutoValue_UserPreferences_Default(preferences);
  }

  /** Settings of a single user. Does not contain any defaults. */
  @AutoValue
  abstract class User implements UserPreferences {
    @Override
    public abstract Cache.UserPreferences preferences();

    public static User empty() {
      return new AutoValue_UserPreferences_User(Cache.UserPreferences.newBuilder().build());
    }
  }

  /**
   * Default settings for the Gerrit instance. These come from {@code refs/users/defaults}. Does not
   * contain any programmatic defaults.
   */
  @AutoValue
  abstract class Default implements UserPreferences {
    @Override
    public abstract Cache.UserPreferences preferences();
  }

  /** A result of overlaying user preferences. */
  @AutoValue
  abstract class Overlay implements UserPreferences {
    @Override
    public abstract Cache.UserPreferences preferences();
  }

  /**
   * Settings intended to be used for updating stored values. Might be overlayed with defaults. The
   * storage must guarantee to only store settings that are different from the defaults.
   */
  @AutoValue
  abstract class ForUpdate implements UserPreferences {
    @Override
    public Cache.UserPreferences preferences() {
      return values();
    }

    abstract Cache.UserPreferences defaults();

    abstract Cache.UserPreferences values();

    public static Builder newBuilder() {
      return new Builder();
    }

    public static class Builder {
      private final Cache.UserPreferences.Builder defaults;
      private final Cache.UserPreferences.Builder values;

      Builder() {
        this.defaults = Cache.UserPreferences.newBuilder();
        this.values = Cache.UserPreferences.newBuilder();
      }

      public <T> Builder add(UserPreferenceFields.Field<T> field, @Nullable T value) {
        switch (field.type()) {
          case GENERAL:
            defaults.putGeneral(field.key(), field.protoValue(field.defaultValue()));
            if (value != null) {
              values.putGeneral(field.key(), field.protoValue(value));
            }
          case DIFF:
            defaults.putDiff(field.key(), field.protoValue(field.defaultValue()));
            if (value != null) {
              values.putDiff(field.key(), field.protoValue(value));
            }
          case EDIT:
            defaults.putEdit(field.key(), field.protoValue(field.defaultValue()));
            if (value != null) {
              values.putEdit(field.key(), field.protoValue(value));
            }
        }

        return this;
      }

      public ForUpdate build() {
        return new AutoValue_UserPreferences_ForUpdate(defaults.build(), values.build());
      }
    }
  }

  /** Returns an overlay of {@code userPreferences} over {@code defaults}. */
  static UserPreferences overlayDefaults(UserPreferences... preferences) {
    return Arrays.stream(preferences)
        .reduce(
            (p1, p2) ->
                new AutoValue_UserPreferences_Overlay(
                    overlayDefaults(p1.preferences(), p2.preferences())))
        .get();
  }

  /** Returns an overlay of {@code userPreferences} over {@code defaults}. */
  static Cache.UserPreferences overlayDefaults(
      Cache.UserPreferences defaults, Cache.UserPreferences userPreferences) {
    return Cache.UserPreferences.newBuilder()
        .putAllGeneral(defaults.getGeneralMap())
        .putAllGeneral(userPreferences.getGeneralMap())
        .putAllEdit(defaults.getEditMap())
        .putAllEdit(userPreferences.getEditMap())
        .putAllDiff(defaults.getDiffMap())
        .putAllDiff(userPreferences.getDiffMap())
        .build();
  }

  /**
   * Returns entries contained in {@code userSettings} if the value is not contained in {@code
   * defaults}. This is the inverse of {@linke #overlayDefaults}.
   */
  static Cache.UserPreferences subtractDefaults(
      Cache.UserPreferences defaults, Cache.UserPreferences userSettings) {
    Cache.UserPreferences.Builder subtracted = Cache.UserPreferences.newBuilder();
    for (Map.Entry<String, Cache.UserPreferences.RepeatedPreference> entry :
        userSettings.getGeneralMap().entrySet()) {
      if (!entry.getValue().equals(defaults.getGeneralMap().get(entry.getKey()))) {
        subtracted.putGeneral(entry.getKey(), entry.getValue());
      }
    }
    return subtracted.build();
  }
}
