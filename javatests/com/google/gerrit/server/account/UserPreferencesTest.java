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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.UserPreferences.overlayDefaults;
import static com.google.gerrit.server.account.UserPreferences.subtractDefaults;

import com.google.gerrit.server.cache.proto.Cache;
import java.util.Arrays;
import org.junit.Test;

/** Test for {@link UserPreferences}. */
public class UserPreferencesTest {
  @Test
  public void overlay() {
    Cache.UserPreferences defaults =
        builder()
            .putGeneral("key1", repeated("foo1", "foo2"))
            .putGeneral("key2", repeated("bar1"))
            .build();
    Cache.UserPreferences userPreferences =
        builder().putGeneral("key1", repeated("user1", "user2")).build();
    Cache.UserPreferences result =
        builder()
            .putGeneral("key1", repeated("user1", "user2"))
            .putGeneral("key2", repeated("bar1"))
            .build();

    assertThat(overlayDefaults(defaults, userPreferences)).isEqualTo(result);
  }

  @Test
  public void subtract() {
    Cache.UserPreferences defaults = builder().putGeneral("key1", repeated("foo1", "foo2")).build();
    Cache.UserPreferences userPreferences =
        builder()
            .putGeneral("key1", repeated("foo1", "foo2"))
            .putGeneral("key2", repeated("bar1"))
            .build();
    Cache.UserPreferences result = builder().putGeneral("key2", repeated("bar1")).build();

    assertThat(subtractDefaults(defaults, userPreferences)).isEqualTo(result);
  }

  private static Cache.UserPreferences.Builder builder() {
    return Cache.UserPreferences.newBuilder();
  }

  private static Cache.UserPreferences.RepeatedPreference repeated(String... val) {
    Cache.UserPreferences.RepeatedPreference.Builder b =
        Cache.UserPreferences.RepeatedPreference.newBuilder();
    Arrays.stream(val).forEach(e -> b.addValue(e));
    return b.build();
  }
}
