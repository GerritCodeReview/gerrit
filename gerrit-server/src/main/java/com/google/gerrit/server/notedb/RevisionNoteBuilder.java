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

import static com.google.gerrit.server.PatchLineCommentsUtil.PLC_ORDER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.PatchLineComment;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

class RevisionNoteBuilder {
  private final Map<PatchLineComment.Key, PatchLineComment> comments;
  private String pushCert;

  RevisionNoteBuilder(RevisionNote base) {
    if (base != null) {
      comments = Maps.newHashMapWithExpectedSize(base.comments.size());
      for (PatchLineComment c : base.comments) {
        addComment(c);
      }
      pushCert = base.pushCert;
    } else {
      comments = new HashMap<>();
      pushCert = null;
    }
  }

  void addComment(PatchLineComment comment) {
    comments.put(comment.getKey(), comment);
  }

  void setPushCertificate(String pushCert) {
    this.pushCert = pushCert;
  }

  byte[] build(CommentsInNotesUtil commentsUtil) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (pushCert != null) {
      byte[] certBytes = pushCert.getBytes(UTF_8);
      out.write(certBytes, 0, trimTrailingNewlines(certBytes));
      out.write('\n');
    }
    commentsUtil.buildNote(PLC_ORDER.sortedCopy(comments.values()), out);
    return out.toByteArray();
  }

  private static int trimTrailingNewlines(byte[] bytes) {
    int p = bytes.length;
    while (p > 1 && bytes[p - 1] == '\n') {
      p--;
    }
    return p;
  }
}
