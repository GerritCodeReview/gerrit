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
import com.google.gerrit.reviewdb.client.RevId;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RobotCommentsRevisionNoteMap {
  final NoteMap noteMap;
  final ImmutableMap<RevId, RobotCommentsRevisionNote> revisionNotes;

  static RobotCommentsRevisionNoteMap parse(
      ChangeNoteUtil noteUtil, ObjectReader reader, NoteMap noteMap)
          throws ConfigInvalidException, IOException {
    Map<RevId, RobotCommentsRevisionNote> result = new HashMap<>();
    for (Note note : noteMap) {
      RobotCommentsRevisionNote rn =
          new RobotCommentsRevisionNote(noteUtil, reader, note.getData());
      rn.parse();
      result.put(new RevId(note.name()), rn);
    }
    return new RobotCommentsRevisionNoteMap(noteMap,
        ImmutableMap.copyOf(result));
  }

  static RobotCommentsRevisionNoteMap emptyMap() {
    return new RobotCommentsRevisionNoteMap(NoteMap.newEmptyMap(),
        ImmutableMap.<RevId, RobotCommentsRevisionNote> of());
  }

  private RobotCommentsRevisionNoteMap(NoteMap noteMap,
      ImmutableMap<RevId, RobotCommentsRevisionNote> revisionNotes) {
    this.noteMap = noteMap;
    this.revisionNotes = revisionNotes;
  }
}
