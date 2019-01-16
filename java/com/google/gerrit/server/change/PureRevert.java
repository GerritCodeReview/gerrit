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

package com.google.gerrit.server.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;

/** Can check if a change is a pure revert (= a revert with no further modifications). */
@Singleton
public class PureRevert {
  private final PureRevertCache pureRevertCache;

  @Inject
  PureRevert(PureRevertCache pureRevertCache) {
    this.pureRevertCache = pureRevertCache;
  }

  public boolean get(ChangeNotes notes, Optional<String> claimedOriginal)
      throws StorageException, IOException, BadRequestException, ResourceConflictException {
    PatchSet currentPatchSet = notes.getCurrentPatchSet();
    if (currentPatchSet == null) {
      throw new ResourceConflictException("current revision is missing");
    }
    if (!claimedOriginal.isPresent()) {
      return pureRevertCache.isPureRevert(notes);
    }

    ObjectId claimedOriginalObjectId;
    try {
      claimedOriginalObjectId = ObjectId.fromString(claimedOriginal.get());
    } catch (InvalidObjectIdException e) {
      throw new BadRequestException("invalid object ID");
    }

    return pureRevertCache.isPureRevert(
        notes.getProjectName(),
        ObjectId.fromString(notes.getCurrentPatchSet().getRevision().get()),
        claimedOriginalObjectId);
  }
}
