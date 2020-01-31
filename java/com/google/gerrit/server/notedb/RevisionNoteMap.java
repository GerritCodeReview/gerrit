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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Comment;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;

/**
 * A utility class that parses a NoteMap into commit => comment list data.
 *
 * @param <T> the RevisionNote for the comment type.
 */
class RevisionNoteMap<T extends RevisionNote<? extends Comment>> {
  /** CommitID => blob ID */
  final NoteMap noteMap;

  /** CommitID => parsed data */
  final ImmutableMap<ObjectId, T> revisionNotes;

  private RevisionNoteMap(NoteMap noteMap, ImmutableMap<ObjectId, T> revisionNotes) {
    this.noteMap = noteMap;
    this.revisionNotes = revisionNotes;
  }

  static RevisionNoteMap<ChangeRevisionNote> parse(
      ChangeNoteJson noteJson, ObjectReader reader, NoteMap noteMap, Comment.Status status)
      throws ConfigInvalidException, IOException {
    ImmutableMap.Builder<ObjectId, ChangeRevisionNote> result = ImmutableMap.builder();
    for (Note note : noteMap) {
      ChangeRevisionNote rn = new ChangeRevisionNote(noteJson, reader, note.getData(), status);
      rn.parse();

      result.put(note.copy(), rn);
    }
    return new RevisionNoteMap<>(noteMap, result.build());
  }

  static RevisionNoteMap<RobotCommentsRevisionNote> parseRobotComments(
      ChangeNoteJson changeNoteJson, ObjectReader reader, NoteMap noteMap)
      throws ConfigInvalidException, IOException {
    ImmutableMap.Builder<ObjectId, RobotCommentsRevisionNote> result = ImmutableMap.builder();
    for (Note note : noteMap) {
      RobotCommentsRevisionNote rn =
          new RobotCommentsRevisionNote(changeNoteJson, reader, note.getData());
      rn.parse();
      result.put(note.copy(), rn);
    }
    return new RevisionNoteMap<>(noteMap, result.build());
  }

  static <T extends RevisionNote<? extends Comment>> RevisionNoteMap<T> emptyMap() {
    return new RevisionNoteMap<>(NoteMap.newEmptyMap(), ImmutableMap.of());
  }
}
