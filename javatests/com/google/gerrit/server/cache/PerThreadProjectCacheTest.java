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

package com.google.gerrit.server.cache;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import org.junit.Test;

public class PerThreadProjectCacheTest {
  @Test
  public void testValueIsCachedWithinSizeLimit() {
    try (PerThreadCache cache = PerThreadCache.create()) {
      PerThreadCache.Key<Project.NameKey> key =
          PerThreadCache.Key.create(Project.NameKey.class, Project.nameKey("test-project"));
      String unused = PerThreadProjectCache.getOrCompute(key, () -> "cached");
      String value = PerThreadProjectCache.getOrCompute(key, () -> "directly served");
      assertThat(value).isEqualTo("cached");
    }
  }

  @Test
  public void testEnforceMaxSize() {
    try (PerThreadCache cache = PerThreadCache.create()) {
      // Fill the cache
      for (int i = 0; i < 50; i++) {
        PerThreadCache.Key<Project.NameKey> key =
            PerThreadCache.Key.create(Project.NameKey.class, Project.nameKey("test-project" + i));
        String unused = PerThreadProjectCache.getOrCompute(key, () -> "cached");
      }
      // Assert that the value was not persisted
      PerThreadCache.Key<Project.NameKey> key =
          PerThreadCache.Key.create(Project.NameKey.class, "Project" + 1000);
      String unused = PerThreadProjectCache.getOrCompute(key, () -> "new value");
      String value = PerThreadProjectCache.getOrCompute(key, () -> "directly served");
      assertThat(value).isEqualTo("directly served");
    }
  }
}
