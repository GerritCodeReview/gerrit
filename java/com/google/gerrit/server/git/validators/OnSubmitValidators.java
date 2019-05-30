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

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener.Arguments;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevWalk;

public class OnSubmitValidators {
  public interface Factory {
    OnSubmitValidators create();
  }

  private final PluginSetContext<OnSubmitValidationListener> listeners;

  @Inject
  OnSubmitValidators(PluginSetContext<OnSubmitValidationListener> listeners) {
    this.listeners = listeners;
  }

  public void validate(
      Project.NameKey project, ObjectReader objectReader, ChainedReceiveCommands commands)
      throws IntegrationException {
    try (RevWalk rw = new RevWalk(objectReader)) {
      Arguments args = new Arguments(project, rw, commands);
      listeners.runEach(l -> l.preBranchUpdate(args), ValidationException.class);
    } catch (ValidationException e) {
      throw new IntegrationException(e.getMessage(), e);
    }
  }
}
