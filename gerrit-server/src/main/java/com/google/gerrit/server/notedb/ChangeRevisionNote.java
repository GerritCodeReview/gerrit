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

import com.google.common.primitives.Bytes;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
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
import org.eclipse.jgit.util.RawParseUtils;

class ChangeRevisionNote extends RevisionNote<Comment> {
  private static final byte[] CERT_HEADER = "certificate version ".getBytes(UTF_8);
  // See org.eclipse.jgit.transport.PushCertificateParser.END_SIGNATURE
  private static final byte[] END_SIGNATURE = "-----END PGP SIGNATURE-----\n".getBytes(UTF_8);

  private final ChangeNoteUtil noteUtil;
  private final Change.Id changeId;
  private final PatchLineComment.Status status;
  private String pushCert;

  ChangeRevisionNote(
      ChangeNoteUtil noteUtil,
      Change.Id changeId,
      ObjectReader reader,
      ObjectId noteId,
      PatchLineComment.Status status) {
    super(reader, noteId);
    this.noteUtil = noteUtil;
    this.changeId = changeId;
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

    if (isJson(raw, p.value)) {
      RevisionNoteData data = parseJson(noteUtil, raw, p.value);
      if (status == PatchLineComment.Status.PUBLISHED) {
        pushCert = data.pushCert;
      } else {
        pushCert = null;
      }
      return data.comments;
    }

    if (status == PatchLineComment.Status.PUBLISHED) {
      pushCert = parsePushCert(changeId, raw, p);
      trimLeadingEmptyLines(raw, p);
    } else {
      pushCert = null;
    }
    return noteUtil.parseNote(raw, p, changeId);
  }

  private static boolean isJson(byte[] raw, int offset) {
    return raw[offset] == '{' || raw[offset] == '[';
  }

  private RevisionNoteData parseJson(ChangeNoteUtil noteUtil, byte[] raw, int offset)
      throws IOException {
    try (InputStream is = new ByteArrayInputStream(raw, offset, raw.length - offset);
        Reader r = new InputStreamReader(is, UTF_8)) {
      return noteUtil.getGson().fromJson(r, RevisionNoteData.class);
    }
  }

  private static String parsePushCert(Change.Id changeId, byte[] bytes, MutableInteger p)
      throws ConfigInvalidException {
    if (RawParseUtils.match(bytes, p.value, CERT_HEADER) < 0) {
      return null;
    }
    int end = Bytes.indexOf(bytes, END_SIGNATURE);
    if (end < 0) {
      throw ChangeNotes.parseException(changeId, "invalid push certificate in note");
    }
    int start = p.value;
    p.value = end + END_SIGNATURE.length;
    return new String(bytes, start, p.value, UTF_8);
  }
}
