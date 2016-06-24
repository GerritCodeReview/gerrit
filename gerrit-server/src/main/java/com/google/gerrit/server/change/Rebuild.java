// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.Rebuild.Input;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;

@Singleton
public class Rebuild implements RestModifyView<ChangeResource, Input> {
  public static class Input {
  }

  private final Provider<ReviewDb> db;
  private final NotesMigration migration;
  private final ChangeRebuilder rebuilder;

  @Inject
  Rebuild(Provider<ReviewDb> db,
      NotesMigration migration,
      ChangeRebuilder rebuilder) {
    this.db = db;
    this.migration = migration;
    this.rebuilder = rebuilder;
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, Input input)
      throws ResourceNotFoundException, IOException, OrmException,
      ConfigInvalidException {
    if (!migration.commitChangeWrites()) {
      throw new ResourceNotFoundException();
    }
    try {
      rebuilder.rebuild(db.get(), rsrc.getId());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(
          IdString.fromDecoded(rsrc.getId().toString()));
    }
    return Response.none();
  }
}
