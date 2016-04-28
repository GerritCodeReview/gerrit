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
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;

import java.io.IOException;

class RevisionNote {
  static final int MAX_NOTE_SZ = 25 << 20;

  private static final byte[] CERT_HEADER =
      "certificate version ".getBytes(UTF_8);
  // See org.eclipse.jgit.transport.PushCertificateParser.END_SIGNATURE
  private static final byte[] END_SIGNATURE =
      "-----END PGP SIGNATURE-----\n".getBytes(UTF_8);

  private static void trimLeadingEmptyLines(byte[] bytes, MutableInteger p) {
    while (p.value < bytes.length && bytes[p.value] == '\n') {
      p.value++;
    }
  }

  private static String parsePushCert(Change.Id changeId, byte[] bytes,
      MutableInteger p) throws ConfigInvalidException {
    if (RawParseUtils.match(bytes, p.value, CERT_HEADER) < 0) {
      return null;
    }
    int end = Bytes.indexOf(bytes, END_SIGNATURE);
    if (end < 0) {
      throw ChangeNotes.parseException(
          changeId, "invalid push certificate in note");
    }
    int start = p.value;
    p.value = end + END_SIGNATURE.length;
    return new String(bytes, start, p.value);
  }

  final byte[] raw;
  final ImmutableList<PatchLineComment> comments;
  final String pushCert;

  RevisionNote(ChangeNoteUtil noteUtil, Change.Id changeId,
      ObjectReader reader, ObjectId noteId, boolean draftsOnly)
      throws ConfigInvalidException, IOException {
    raw = reader.open(noteId, OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    MutableInteger p = new MutableInteger();
    trimLeadingEmptyLines(raw, p);
    if (!draftsOnly) {
      pushCert = parsePushCert(changeId, raw, p);
      trimLeadingEmptyLines(raw, p);
    } else {
      pushCert = null;
    }
    PatchLineComment.Status status = draftsOnly
        ? PatchLineComment.Status.DRAFT
        : PatchLineComment.Status.PUBLISHED;
    comments = ImmutableList.copyOf(
        noteUtil.parseNote(raw, p, changeId, status));
  }
}
