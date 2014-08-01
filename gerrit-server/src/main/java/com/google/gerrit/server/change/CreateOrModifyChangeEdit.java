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

package com.google.gerrit.server.change;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.CreateOrModifyChangeEdit.Input;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

public class CreateOrModifyChangeEdit implements
    RestModifyView<ChangeResource, Input> {

  interface Factory {
    CreateOrModifyChangeEdit create(Change change, String path);
  }

  public static class Input {
    public RawInput content;
  }

  private final Provider<ReviewDb> db;
  private final ChangeEditUtil editUtil;
  private final ChangeEditModifier editModifier;
  private final Change change;
  private final String path;

  @Inject
  CreateOrModifyChangeEdit(Provider<ReviewDb> db,
      ChangeEditUtil editUtil,
      ChangeEditModifier editModifier,
      @Assisted Change change,
      @Assisted String path) {
    this.db = db;
    this.editUtil = editUtil;
    this.editModifier = editModifier;
    this.change = change;
    this.path = path;
  }

  public Response<?> apply(ChangeResource resource, Input input)
      throws AuthException, IOException, ResourceConflictException,
      OrmException {
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    if (!edit.isPresent()) {
      editModifier.createEdit(change,
          db.get().patchSets().get(change.currentPatchSetId()));
      edit = editUtil.byChange(change);
    }
    if (!Strings.isNullOrEmpty(path)) {
      modifyExistingEdit(edit, input);
    }
    return Response.none();
  }

  private void modifyExistingEdit(Optional<ChangeEdit> edit, Input input)
      throws IOException, AuthException, ResourceConflictException {
    byte[] content = null;
    if (input.content != null) {
      content = ByteStreams.toByteArray(input.content.getInputStream());
    }
    try {
      editModifier.modifyFile(edit.get(), path, content);
    } catch(InvalidChangeOperationException | IOException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
