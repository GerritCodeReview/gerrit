// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.Optional;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public class FileBasedAllProjectsConfigProvider implements AllProjectsConfigProvider {
  private final SitePaths sitePaths;

  @VisibleForTesting
  @Inject
  public FileBasedAllProjectsConfigProvider(SitePaths sitePaths) {
    this.sitePaths = sitePaths;
  }

  @Override
  public Optional<StoredConfig> get(AllProjectsName allProjectsName) {
    return Optional.of(
        new FileBasedConfig(
            sitePaths
                .etc_dir
                .resolve(allProjectsName.get())
                .resolve(ProjectConfig.PROJECT_CONFIG)
                .toFile(),
            FS.DETECTED));
  }
}
