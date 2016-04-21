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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class RevisionNoteBuilder {
  static class Cache {
    private final RevisionNoteMap revisionNoteMap;
    private final Map<RevId, RevisionNoteBuilder> builders;

    Cache(RevisionNoteMap revisionNoteMap) {
      this.revisionNoteMap = revisionNoteMap;
      this.builders = new HashMap<>();
    }

    RevisionNoteBuilder get(RevId revId) {
      RevisionNoteBuilder b = builders.get(revId);
      if (b == null) {
        b = new RevisionNoteBuilder(
            revisionNoteMap.revisionNotes.get(revId));
        builders.put(revId, b);
      }
      return b;
    }

    Map<RevId, RevisionNoteBuilder> getBuilders() {
      return Collections.unmodifiableMap(builders);
    }
  }

  final byte[] baseRaw;
  final List<PatchLineComment> baseComments;
  final Map<PatchLineComment.Key, PatchLineComment> put;
  final Set<PatchLineComment.Key> delete;

  private String pushCert;

  RevisionNoteBuilder(RevisionNote base) {
    if (base != null) {
      baseRaw = base.raw;
      baseComments = base.comments;
      put = Maps.newHashMapWithExpectedSize(base.comments.size());
      pushCert = base.pushCert;
    } else {
      baseRaw = new byte[0];
      baseComments = Collections.emptyList();
      put = new HashMap<>();
      pushCert = null;
    }
    delete = new HashSet<>();
  }

  void putComment(PatchLineComment comment) {
    checkArgument(!delete.contains(comment.getKey()),
        "cannot both delete and put %s", comment.getKey());
    put.put(comment.getKey(), comment);
  }

  void deleteComment(PatchLineComment.Key key) {
    checkArgument(!put.containsKey(key), "cannot both delete and put %s", key);
    delete.add(key);
  }

  void setPushCertificate(String pushCert) {
    this.pushCert = pushCert;
  }

  byte[] build(ChangeNoteUtil noteUtil) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (pushCert != null) {
      byte[] certBytes = pushCert.getBytes(UTF_8);
      out.write(certBytes, 0, trimTrailingNewlines(certBytes));
      out.write('\n');
    }

    Multimap<PatchSet.Id, PatchLineComment> all = ArrayListMultimap.create();
    for (PatchLineComment c : baseComments) {
      if (!delete.contains(c.getKey()) && !put.containsKey(c.getKey())) {
        all.put(c.getPatchSetId(), c);
      }
    }
    for (PatchLineComment c : put.values()) {
      if (!delete.contains(c.getKey())) {
        all.put(c.getPatchSetId(), c);
      }
    }
    noteUtil.buildNote(all, out);
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
