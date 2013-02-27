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

package com.google.gerrit.server.group;

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.PerformRenameGroup;
import com.google.gerrit.server.group.PutName.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

public class PutName implements RestModifyView<GroupResource, Input> {
  static class Input {
    @DefaultInput
    String name;
  }

  private final PerformRenameGroup.Factory performRenameGroupFactory;

  @Inject
  PutName(PerformRenameGroup.Factory performRenameGroupFactory) {
    this.performRenameGroupFactory = performRenameGroupFactory;
  }

  @Override
  public String apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
      ResourceNotFoundException, ResourceConflictException, OrmException {
    if (resource.toAccountGroup() == null) {
      throw new MethodNotAllowedException();
    } else if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    } else if (input == null || Strings.isNullOrEmpty(input.name)) {
      throw new BadRequestException("name is required");
    }

    final String newName = input.name.trim();
    if (resource.toAccountGroup().getName().equals(newName)) {
      return newName;
    }

    try {
      return performRenameGroupFactory.create().renameGroup(
          resource.toAccountGroup().getId(), newName).group.getName();
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException();
    } catch (InvalidNameException e) {
      throw new BadRequestException(e.getMessage());
    } catch (NameAlreadyUsedException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
