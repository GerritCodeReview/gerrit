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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.RobotComment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

public class RobotCommentsRevisionNote extends RevisionNote<RobotComment> {
  private final ChangeNoteUtil noteUtil;

  RobotCommentsRevisionNote(ChangeNoteUtil noteUtil, ObjectReader reader, ObjectId noteId) {
    super(reader, noteId);
    this.noteUtil = noteUtil;
  }

  @Override
  protected List<RobotComment> parse(byte[] raw, int offset) throws IOException {
    try (InputStream is = new ByteArrayInputStream(raw, offset, raw.length - offset);
        Reader r = new InputStreamReader(is, UTF_8)) {
      return noteUtil.getGson().fromJson(r, RobotCommentsRevisionNoteData.class).comments;
    }
  }
}
