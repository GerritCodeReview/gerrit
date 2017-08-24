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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.RevId;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;

class RevisionNoteMap<T extends RevisionNote<? extends Comment>> {
  final NoteMap noteMap;
  final ImmutableMap<RevId, T> revisionNotes;

  static RevisionNoteMap<ChangeRevisionNote> parse(
      ChangeNoteUtil noteUtil,
      Change.Id changeId,
      ObjectReader reader,
      NoteMap noteMap,
      PatchLineComment.Status status)
      throws ConfigInvalidException, IOException {
    Map<RevId, ChangeRevisionNote> result = new HashMap<>();
    for (Note note : noteMap) {
      ChangeRevisionNote rn =
          new ChangeRevisionNote(noteUtil, changeId, reader, note.getData(), status);
      rn.parse();
      result.put(new RevId(note.name()), rn);
    }
    return new RevisionNoteMap<>(noteMap, ImmutableMap.copyOf(result));
  }

  static RevisionNoteMap<RobotCommentsRevisionNote> parseRobotComments(
      ChangeNoteUtil noteUtil, ObjectReader reader, NoteMap noteMap)
      throws ConfigInvalidException, IOException {
    Map<RevId, RobotCommentsRevisionNote> result = new HashMap<>();
    for (Note note : noteMap) {
      RobotCommentsRevisionNote rn =
          new RobotCommentsRevisionNote(noteUtil, reader, note.getData());
      rn.parse();
      result.put(new RevId(note.name()), rn);
    }
    return new RevisionNoteMap<>(noteMap, ImmutableMap.copyOf(result));
  }

  static <T extends RevisionNote<? extends Comment>> RevisionNoteMap<T> emptyMap() {
    return new RevisionNoteMap<>(NoteMap.newEmptyMap(), ImmutableMap.<RevId, T>of());
  }

  private RevisionNoteMap(NoteMap noteMap, ImmutableMap<RevId, T> revisionNotes) {
    this.noteMap = noteMap;
    this.revisionNotes = revisionNotes;
  }
}
