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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.PutContent.Input;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.RefUpdate;

import java.io.IOException;

public class PutContent implements RestModifyView<FileResource, Input> {
  public static class Input {
    @DefaultInput
    public String content;
  }

  private final RevisionEditCommands cmd;

  @Inject
  PutContent(RevisionEditCommands cmd) {
    this.cmd = cmd;
  }

  @Override
  public Response<?> apply(FileResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException,
      ResourceConflictException, OrmException {
    RevisionResource rev = rsrc.getRevision();
    Change change = rev.getChange();
    PatchSet ps = rev.getPatchSet();
    String file = rsrc.getPatchKey().getFileName();
    String content = input.content;
    try {
      cmd.edit(change, ps, file, content);
    } catch(InvalidChangeOperationException | IOException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch(NoSuchChangeException e) {
      throw new ResourceNotFoundException(change.getId().toString());
    }
    return Response.none();
  }
}