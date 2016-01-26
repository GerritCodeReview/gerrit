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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

import java.io.IOException;

class RevisionNote {
  static final int MAX_NOTE_SZ = 25 << 20;

  final ImmutableList<PatchLineComment> comments;

  RevisionNote(Change.Id changeId, ObjectReader reader, ObjectId noteId)
      throws ConfigInvalidException, IOException {
    byte[] bytes = reader.open(noteId, OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    comments = ImmutableList.copyOf(CommentsInNotesUtil.parseNote(
        bytes, changeId, PatchLineComment.Status.PUBLISHED));
  }
}
