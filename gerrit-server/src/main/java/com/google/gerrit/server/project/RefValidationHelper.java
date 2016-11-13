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

import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.validators.RefOperationValidators;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand.Type;

public class RefValidationHelper {
  public interface Factory {
    RefValidationHelper create(Type operationType);
  }

  private final RefOperationValidators.Factory refValidatorsFactory;
  private final Type operationType;

  @Inject
  RefValidationHelper(
      RefOperationValidators.Factory refValidatorsFactory, @Assisted Type operationType) {
    this.refValidatorsFactory = refValidatorsFactory;
    this.operationType = operationType;
  }

  public void validateRefOperation(String projectName, IdentifiedUser user, RefUpdate update)
      throws ResourceConflictException {
    RefOperationValidators refValidators =
        refValidatorsFactory.create(
            new Project(new Project.NameKey(projectName)),
            user,
            RefOperationValidators.getCommand(update, operationType));
    try {
      refValidators.validateForRefOperation();
    } catch (ValidationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
