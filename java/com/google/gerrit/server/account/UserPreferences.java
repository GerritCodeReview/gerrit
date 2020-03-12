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
import com.google.gerrit.server.cache.proto.Cache;
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

  @AutoValue
  abstract class User implements UserPreferences {
    @Override
    public abstract Cache.UserPreferences preferences();
  }

  @AutoValue
  abstract class Default implements UserPreferences {
    @Override
    public abstract Cache.UserPreferences preferences();
  }

  @AutoValue
  abstract class Mixed implements UserPreferences {
    @Override
    public abstract Cache.UserPreferences preferences();

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

      public <T> Builder add(UserPreferenceFields.Field<T> field, T value) {
        switch (field.type()) {
          case UserPreferenceSection.GENERAL:
            field.setDefault(defaults.getGeneralMap());
            field.set(values.getGeneralMap(), value);
        }

        return this;
      }

      public Mixed build() {
        return new AutoValue_UserPreferences_Mixed(builder.build());
      }
    }
  }

  /** Returns an overlay of {@code userPreferences} over {@code defaults}. */
  static UserPreferences.Mixed overlayDefaults(
      UserPreferences.Default defaults, UserPreferences.User user) {}

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
