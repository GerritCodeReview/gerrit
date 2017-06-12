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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.DeleteChangeEdit.Input;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class DeleteChangeEdit implements RestModifyView<ChangeResource, Input> {
  public static class Input {}

  private final ChangeEditUtil editUtil;

  @Inject
  DeleteChangeEdit(ChangeEditUtil editUtil) {
    this.editUtil = editUtil;
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException, IOException, OrmException {
    Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
    if (edit.isPresent()) {
      editUtil.delete(edit.get());
    } else {
      throw new ResourceNotFoundException();
    }

    return Response.none();
  }
}
