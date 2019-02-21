// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.transport.ReceiveCommand;

@Singleton
public class CheckerRefOperationValidator implements RefOperationValidationListener {

  private final AllProjectsName allProjectsName;

  @Inject
  CheckerRefOperationValidator(AllProjectsName allProjects) {
    this.allProjectsName = allProjects;
  }

  @Override
  public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
      throws ValidationException {
    if (refEvent.project.getNameKey().equals(allProjectsName)) {
      if (CheckerRef.isRefsCheckers(refEvent.command.getRefName())) {
        if (refEvent.command.getType().equals(ReceiveCommand.Type.CREATE)) {
          throw new ValidationException("Not allowed to create checker ref.");
        } else if (refEvent.command.getType().equals(ReceiveCommand.Type.DELETE)) {
          throw new ValidationException("Not allowed to delete checker ref.");
        }
      }
    }
    return ImmutableList.of();
  }
}
