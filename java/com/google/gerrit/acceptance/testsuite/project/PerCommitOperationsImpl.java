// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.project;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class PerCommitOperationsImpl implements PerCommitOperations {

  public interface Factory {
    PerCommitOperationsImpl create(Project.NameKey projectNameKey, ObjectId commitId);
  }

  private final GitRepositoryManager repositoryManager;
  private final Project.NameKey projectNameKey;
  private final ObjectId commitId;

  @Inject
  private PerCommitOperationsImpl(
      GitRepositoryManager repositoryManager,
      @Assisted Project.NameKey projectNameKey,
      @Assisted ObjectId commitId) {
    this.repositoryManager = repositoryManager;
    this.projectNameKey = projectNameKey;
    this.commitId = commitId;
  }

  @Override
  public RevCommit get() {
    try (TestRepository<Repository> repo =
        new TestRepository<>(repositoryManager.openRepository(projectNameKey))) {
      return repo.getRepository().parseCommit(commitId);
    } catch (Exception e) {
      throw new IllegalStateException(
          String.format("getting commit of project %s failed", projectNameKey), e);
    }
  }
}
