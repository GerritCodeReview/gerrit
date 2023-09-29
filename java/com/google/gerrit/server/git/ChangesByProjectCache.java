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

package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;
import java.io.IOException;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

public interface ChangesByProjectCache {
  public enum UseIndex {
    TRUE,
    FALSE;
  }

  public static class Module extends AbstractModule {
    private UseIndex useIndex;
    private @GerritServerConfig Config config;

    public Module(UseIndex useIndex, @GerritServerConfig Config config) {
      this.useIndex = useIndex;
      this.config = config;
    }

    @Override
    protected void configure() {
      boolean searchingCacheEnabled =
          config.getLong("cache", SearchingChangeCacheImpl.ID_CACHE, "memoryLimit", 0) > 0;
      if (searchingCacheEnabled && UseIndex.TRUE.equals(useIndex)) {
        install(new SearchingChangeCacheImpl.SearchingChangeCacheImplModule());
      } else {
        bind(UseIndex.class).toInstance(useIndex);
        install(new ChangesByProjectCacheImpl.Module());
      }
    }
  }

  /**
   * Stream changeDatas for the project
   *
   * @param project project to read.
   * @param repository repository for the project to read.
   * @return Stream of known changes; empty if no changes.
   */
  Stream<ChangeData> streamChangeDatas(Project.NameKey project, Repository repository)
      throws IOException;
}
