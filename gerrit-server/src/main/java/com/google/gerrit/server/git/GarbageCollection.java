// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.common.data.GarbageCollectionProgressMonitor;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.GC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class GarbageCollection {
  private static final Logger log = LoggerFactory
      .getLogger(GarbageCollection.class);

  private final GitRepositoryManager repoManager;
  private final IdentifiedUser currentUser;

  public interface Factory {
    GarbageCollection create();
  }

  @Inject
  GarbageCollection(final GitRepositoryManager repoManager,
      final IdentifiedUser currentUser) {
    this.repoManager = repoManager;
    this.currentUser = currentUser;
  }

  public GarbageCollectionResult run(final List<Project.NameKey> projectNames,
      final GarbageCollectionProgressMonitor pm) {
    final GarbageCollectionResult result = new GarbageCollectionResult();
    if (!currentUser.getCapabilities().canRunGC()) {
      result.addError(new GarbageCollectionResult.Error(
          GarbageCollectionResult.Error.Type.GC_NOT_PERMITTED, currentUser
              .getUserName()));
      return result;
    }

    for (final Project.NameKey projectName : projectNames) {
      pm.startGarbageCollection(projectName);
      try {
        final Repository repo = repoManager.openRepository(projectName);
        try {
          if (!(repo instanceof FileRepository)) {
            result.addError(new GarbageCollectionResult.Error(
                GarbageCollectionResult.Error.Type.GC_NOT_SUPPORTED, projectName));
            continue;
          }

          GC.gc(pm, (FileRepository) repo);
        } catch (IOException e) {
          log.error("garbage collection for project \"" + projectName + "\" failed", e);
          result.addError(new GarbageCollectionResult.Error(
              GarbageCollectionResult.Error.Type.GC_FAILED, projectName));
        } finally {
          repo.close();
        }
      } catch (RepositoryNotFoundException e) {
        result.addError(new GarbageCollectionResult.Error(
            GarbageCollectionResult.Error.Type.REPOSITORY_NOT_FOUND,
            projectName));
      } finally {
        pm.endGarbageCollection();
      }
    }
    return result;
  }
}
