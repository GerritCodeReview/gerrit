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
package com.google.gerrit.server.git.validators;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener.Arguments;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class OnSubmitValidators {
  public interface Factory {
    OnSubmitValidators create();
  }

  private final DynamicSet<OnSubmitValidationListener> listeners;

  @Inject
  OnSubmitValidators(DynamicSet<OnSubmitValidationListener> listeners) {
    this.listeners = listeners;
  }

  public void validate(
      Project.NameKey project,
      Repository repo,
      ObjectReader objectReader,
      Map<String, ReceiveCommand> commands)
      throws IntegrationException {
    try {
      for (OnSubmitValidationListener listener : this.listeners) {
        listener.preBranchUpdate(new Arguments(project, repo, objectReader, commands));
      }
    } catch (ValidationException e) {
      throw new IntegrationException(e.getMessage());
    }
  }
}
