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

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Comment;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.MutableInteger;

abstract class RevisionNote<T extends Comment> {
  static final int MAX_NOTE_SZ = 25 << 20;

  protected static void trimLeadingEmptyLines(byte[] bytes, MutableInteger p) {
    while (p.value < bytes.length && bytes[p.value] == '\n') {
      p.value++;
    }
  }

  private final ObjectReader reader;
  private final ObjectId noteId;

  private byte[] raw;
  private ImmutableList<T> comments;

  RevisionNote(ObjectReader reader, ObjectId noteId) {
    this.reader = reader;
    this.noteId = noteId;
  }

  public byte[] getRaw() {
    checkParsed();
    return raw;
  }

  public ImmutableList<T> getComments() {
    checkParsed();
    return comments;
  }

  public void parse() throws IOException, ConfigInvalidException {
    raw = reader.open(noteId, OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    MutableInteger p = new MutableInteger();
    trimLeadingEmptyLines(raw, p);
    if (p.value >= raw.length) {
      comments = null;
      return;
    }

    comments = ImmutableList.copyOf(parse(raw, p.value));
  }

  protected abstract List<T> parse(byte[] raw, int offset)
      throws IOException, ConfigInvalidException;

  protected void checkParsed() {
    checkState(raw != null, "revision note not parsed yet");
  }
}
