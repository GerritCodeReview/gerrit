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
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.inject.Inject;
import com.google.gerrit.server.project.SetName.Input;

import org.eclipse.jgit.errors.RepositoryNotFoundException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
class SetName implements RestModifyView<ProjectResource, Input> {
  static class Input {
    @DefaultInput
    String name;
  }

  private final RenameProject.Factory renameProjectFactory;

  @Inject
  SetName(final RenameProject.Factory renameProjectFactory) {
    this.renameProjectFactory = renameProjectFactory;
  }

  @Override
  public Object apply(ProjectResource resource, Input input)
      throws AuthException, NameAlreadyUsedException, NoSuchProjectException,
      PermissionDeniedException, ProjectRenamingFailedException,
      RepositoryNotFoundException {
    final String sourceName = resource.getName();
    final String destinationName = input.name;
    return renameProjectFactory.create(sourceName, destinationName)
        .renameProject();
  }
}
