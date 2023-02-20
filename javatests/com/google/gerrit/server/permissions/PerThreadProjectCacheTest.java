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

package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.cache.PerThreadCache;
import org.junit.Test;
import org.mockito.Mockito;

public class PerThreadProjectCacheTest {
  @Test
  public void testValueIsCachedWithinSizeLimit() {
    try (PerThreadCache cache = PerThreadCache.create()) {
      ProjectControl projectControlCached = Mockito.mock(ProjectControl.class);
      ProjectControl projectControlDirectlyServed = Mockito.mock(ProjectControl.class);
      PerThreadCache.Key<ProjectControl> key =
          PerThreadCache.Key.create(ProjectControl.class, "Project1");
      PerThreadProjectCache.getOrCompute(key, () -> projectControlCached);
      ProjectControl value =
          PerThreadProjectCache.getOrCompute(key, () -> projectControlDirectlyServed);
      assertThat(value).isEqualTo(projectControlCached);
    }
  }

  @Test
  public void testEnforceMaxSize() {
    try (PerThreadCache cache = PerThreadCache.create()) {
      ProjectControl projectControlCached = Mockito.mock(ProjectControl.class);
      ProjectControl projectControlNewValue = Mockito.mock(ProjectControl.class);
      ProjectControl projectControlDirectlyServed = Mockito.mock(ProjectControl.class);
      // Fill the cache
      for (int i = 0; i < 50; i++) {
        PerThreadCache.Key<ProjectControl> key =
            PerThreadCache.Key.create(ProjectControl.class, "Project" + i);
        PerThreadProjectCache.getOrCompute(key, () -> projectControlCached);
      }
      // Assert that the value was not persisted
      PerThreadCache.Key<ProjectControl> key =
          PerThreadCache.Key.create(ProjectControl.class, "Project" + 1000);
      PerThreadProjectCache.getOrCompute(key, () -> projectControlNewValue);
      ProjectControl value =
          PerThreadProjectCache.getOrCompute(key, () -> projectControlDirectlyServed);
      assertThat(value).isEqualTo(projectControlDirectlyServed);
    }
  }
}
