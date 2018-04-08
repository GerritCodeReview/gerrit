// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm.init.api;

import com.google.gerrit.config.SitePaths;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;

@Singleton
public class GitRepositoryManagerOnInit implements GitRepositoryManager {
  private final InitFlags flags;
  private final SitePaths site;

  @Inject
  GitRepositoryManagerOnInit(InitFlags flags, SitePaths site) {
    this.flags = flags;
    this.site = site;
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    return new FileRepository(getPath(name));
  }

  @Override
  public Repository createRepository(Project.NameKey name) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public SortedSet<Project.NameKey> list() {
    throw new UnsupportedOperationException("not implemented");
  }

  private File getPath(Project.NameKey name) {
    Path basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
    return FileKey.resolve(basePath.resolve(name.get()).toFile(), FS.DETECTED);
  }
}
