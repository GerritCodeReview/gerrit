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

package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.server.config.CachedPreferences;
import com.google.gerrit.server.config.DefaultPreferencesCache;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class DefaultConfigCacheIT extends AbstractDaemonTest {
  @Inject DefaultPreferencesCache defaultPreferenceCache;

  @Test
  public void invalidatesOldValue() throws Exception {
    CachedPreferences before = defaultPreferenceCache.get();
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = 123;
    gApi.config().server().setDefaultDiffPreferences(update);
    assertThat(before).isNotEqualTo(defaultPreferenceCache.get());
  }

  @Test
  public void subsequentCallsReturnSameInstance() {
    assertThat(defaultPreferenceCache.get()).isSameInstanceAs(defaultPreferenceCache.get());
  }

  @Test
  public void canLoadAtSpecificRev() throws Exception {
    // Set a value to make sure we have custom preferences set
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = 1337;
    gApi.config().server().setDefaultDiffPreferences(update);

    ObjectId oldRev = currentRev();
    CachedPreferences before = defaultPreferenceCache.get();

    // Mutate the preferences
    DiffPreferencesInfo update2 = new DiffPreferencesInfo();
    update2.lineLength = 815;
    gApi.config().server().setDefaultDiffPreferences(update2);

    assertThat(oldRev).isNotEqualTo(currentRev());
    assertThat(defaultPreferenceCache.get()).isNotEqualTo(before);
    assertThat(defaultPreferenceCache.get(oldRev)).isEqualTo(before);
  }

  private ObjectId currentRev() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.exactRef(RefNames.REFS_USERS_DEFAULT).getObjectId();
    }
  }
}
