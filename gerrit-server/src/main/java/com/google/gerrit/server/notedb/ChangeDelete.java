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

package com.google.gerrit.server.notedb;

import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

public class ChangeDelete {
  private final PatchLineCommentsUtil plcUtil;
  private final Repository repo;
  private final ChangeNotes notes;

  public ChangeDelete(PatchLineCommentsUtil plcUtil, Repository repo,
      ChangeNotes notes) {
    this.plcUtil = plcUtil;
    this.repo = repo;
    this.notes = notes;
  }

  public void delete() throws OrmException, IOException {
    plcUtil.deleteAllDraftsFromAllUsers(notes.getChangeId());

    RefUpdate ru = repo.updateRef(notes.getRefName());
    ru.setExpectedOldObjectId(notes.load().getRevision());
    ru.setNewObjectId(ObjectId.zeroId());
    ru.setForceUpdate(true);
    ru.setRefLogMessage("Delete change from NoteDb", false);
    RefUpdate.Result result = ru.delete();
    switch (result) {
      case FAST_FORWARD:
      case FORCED:
      case NO_CHANGE:
        break;

      case IO_FAILURE:
      case LOCK_FAILURE:
      case NEW:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      default:
        throw new IOException(String.format(
            "Failed to delete change ref %s at %s: %s",
            notes.getRefName(), notes.getRevision(), result));
    }
  }
}
