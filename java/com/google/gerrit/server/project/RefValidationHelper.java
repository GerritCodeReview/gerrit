// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.SignedCommitPolicy;
import com.google.gerrit.server.git.validators.RefOperationValidators;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class RefValidationHelper {
  public interface Factory {
    RefValidationHelper create(ReceiveCommand.Type operationType);
  }

  private final RefOperationValidators.Factory refValidatorsFactory;
  private final ReceiveCommand.Type operationType;
  private final SignedCommitPolicy signedCommitPolicy;
  private final GitRepositoryManager repoManager;

  @Inject
  RefValidationHelper(
      RefOperationValidators.Factory refValidatorsFactory,
      SignedCommitPolicy signedCommitPolicy,
      GitRepositoryManager repoManager,
      @Assisted ReceiveCommand.Type operationType) {
    this.refValidatorsFactory = refValidatorsFactory;
    this.operationType = operationType;
    this.signedCommitPolicy = signedCommitPolicy;
    this.repoManager = repoManager;
  }

  public void validateRefOperation(
      String projectName,
      IdentifiedUser user,
      RefUpdate update,
      ImmutableListMultimap<String, String> pushOptions)
      throws ResourceConflictException, IOException {
    Project.NameKey project = Project.nameKey(projectName);
    ReceiveCommand command = RefOperationValidators.getCommand(update, operationType);
    RefOperationValidators refValidators =
        refValidatorsFactory.create(Project.builder(project).build(), user, command, pushOptions);
    try {
      refValidators.validateForRefOperation();
    } catch (ValidationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    if (signedCommitPolicy.isRequired(BranchNameKey.create(project, command.getRefName()))) {
      try (Repository repo = repoManager.openRepository(project);
          ObjectReader reader = repo.newObjectReader()) {
        signedCommitPolicy.checkBranchUpdate(project, reader, command);
      }
    }
  }
}
