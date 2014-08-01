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

import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.PutContent.Input;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class PutContent implements RestModifyView<EditFileResource, Input> {
  public static class Input {
    public RawInput content;
    public boolean restore;
  }

  private final ChangeEditModifier editModifier;

  @Inject
  PutContent(ChangeEditModifier editModifier) {
    this.editModifier = editModifier;
  }

  @Override
  public Response<?> apply(EditFileResource rsrc, Input input)
      throws AuthException, ResourceConflictException, IOException {
    String path = rsrc.getPath();
    byte[] content = null;
    if (input.content != null) {
      content = ByteStreams.toByteArray(input.content.getInputStream());
    }
    boolean restore = input.restore;
    try {
      if (restore) {
        editModifier.restoreFile(rsrc.getChangeEdit(), path);
      } else {
        editModifier.modifyFile(rsrc.getChangeEdit(), path, content);
      }
    } catch(InvalidChangeOperationException | IOException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.none();
  }
}
