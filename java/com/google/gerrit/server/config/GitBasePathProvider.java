// Copyright (C) 2022 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;

public class GitBasePathProvider implements Provider<Path> {
  public static final String SECTION = "gerrit";
  public static final String KEY = "basePath";

  private final Path basePath;

  @Inject
  public GitBasePathProvider(@GerritServerConfig Config cfg, SitePaths sitePaths) {
    String basePathStr = cfg.getString(SECTION, null, KEY);
    basePath = sitePaths.resolve(basePathStr);
    if (basePath == null) {
      throw new ProvisionException("gerrit.basePath must be configured");
    }
  }

  @Override
  public Path get() {
    return basePath;
  }
}
