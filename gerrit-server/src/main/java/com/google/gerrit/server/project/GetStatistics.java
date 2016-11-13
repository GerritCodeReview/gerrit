// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;

@RequiresCapability(GlobalCapability.RUN_GC)
@Singleton
public class GetStatistics implements RestReadView<ProjectResource> {

  private final GitRepositoryManager repoManager;

  @Inject
  GetStatistics(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public RepositoryStatistics apply(ProjectResource rsrc)
      throws ResourceNotFoundException, ResourceConflictException {
    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      GarbageCollectCommand gc = Git.wrap(repo).gc();
      return new RepositoryStatistics(gc.getStatistics());
    } catch (GitAPIException | JGitInternalException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (IOException e) {
      throw new ResourceNotFoundException(rsrc.getName());
    }
  }
}
