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

import com.google.gerrit.entities.Comment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.MutableInteger;

class ChangeRevisionNote extends RevisionNote<Comment> {
  private final ChangeNoteJson noteJson;
  private final Comment.Status status;
  private String pushCert;

  ChangeRevisionNote(
      ChangeNoteJson noteJson, ObjectReader reader, ObjectId noteId, Comment.Status status) {
    super(reader, noteId);
    this.noteJson = noteJson;
    this.status = status;
  }

  public String getPushCert() {
    checkParsed();
    return pushCert;
  }

  @Override
  protected List<Comment> parse(byte[] raw, int offset) throws IOException, ConfigInvalidException {
    MutableInteger p = new MutableInteger();
    p.value = offset;

    RevisionNoteData data = parseJson(noteJson, raw, p.value);
    if (status == Comment.Status.PUBLISHED) {
      pushCert = data.pushCert;
    } else {
      pushCert = null;
    }
    return data.comments;
  }

  private RevisionNoteData parseJson(ChangeNoteJson noteUtil, byte[] raw, int offset)
      throws IOException {
    try (InputStream is = new ByteArrayInputStream(raw, offset, raw.length - offset);
        Reader r = new InputStreamReader(is, UTF_8)) {
      return noteUtil.getGson().fromJson(r, RevisionNoteData.class);
    }
  }
}
