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
import static com.google.gerrit.server.CommentsUtil.COMMENT_ORDER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.RevId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class RevisionNoteBuilder {
  static class Cache {
    private final RevisionNoteMap<? extends RevisionNote<? extends Comment>> revisionNoteMap;
    private final Map<RevId, RevisionNoteBuilder> builders;

    Cache(RevisionNoteMap<? extends RevisionNote<? extends Comment>> revisionNoteMap) {
      this.revisionNoteMap = revisionNoteMap;
      this.builders = new HashMap<>();
    }

    RevisionNoteBuilder get(RevId revId) {
      RevisionNoteBuilder b = builders.get(revId);
      if (b == null) {
        b = new RevisionNoteBuilder(revisionNoteMap.revisionNotes.get(revId));
        builders.put(revId, b);
      }
      return b;
    }

    Map<RevId, RevisionNoteBuilder> getBuilders() {
      return Collections.unmodifiableMap(builders);
    }
  }

  final byte[] baseRaw;
  final List<? extends Comment> baseComments;
  final Map<Comment.Key, Comment> put;
  final Set<Comment.Key> delete;

  private String pushCert;

  RevisionNoteBuilder(RevisionNote<? extends Comment> base) {
    if (base != null) {
      baseRaw = base.getRaw();
      baseComments = base.getComments();
      put = Maps.newHashMapWithExpectedSize(baseComments.size());
      if (base instanceof ChangeRevisionNote) {
        pushCert = ((ChangeRevisionNote) base).getPushCert();
      }
    } else {
      baseRaw = new byte[0];
      baseComments = Collections.emptyList();
      put = new HashMap<>();
      pushCert = null;
    }
    delete = new HashSet<>();
  }

  public byte[] build(ChangeNoteUtil noteUtil) throws IOException {
    return build(noteUtil.getChangeNoteJson());
  }

  public byte[] build(ChangeNoteJson changeNoteJson) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    buildNoteJson(changeNoteJson, out);
    return out.toByteArray();
  }

  void putComment(Comment comment) {
    checkArgument(!delete.contains(comment.key), "cannot both delete and put %s", comment.key);
    put.put(comment.key, comment);
  }

  void deleteComment(Comment.Key key) {
    checkArgument(!put.containsKey(key), "cannot both delete and put %s", key);
    delete.add(key);
  }

  void setPushCertificate(String pushCert) {
    this.pushCert = pushCert;
  }

  private ListMultimap<Integer, Comment> buildCommentMap() {
    ListMultimap<Integer, Comment> all = MultimapBuilder.hashKeys().arrayListValues().build();

    for (Comment c : baseComments) {
      if (!delete.contains(c.key) && !put.containsKey(c.key)) {
        all.put(c.key.patchSetId, c);
      }
    }
    for (Comment c : put.values()) {
      if (!delete.contains(c.key)) {
        all.put(c.key.patchSetId, c);
      }
    }
    return all;
  }

  private void buildNoteJson(ChangeNoteJson noteUtil, OutputStream out) throws IOException {
    ListMultimap<Integer, Comment> comments = buildCommentMap();
    if (comments.isEmpty() && pushCert == null) {
      return;
    }

    RevisionNoteData data = new RevisionNoteData();
    data.comments = COMMENT_ORDER.sortedCopy(comments.values());
    data.pushCert = pushCert;

    try (OutputStreamWriter osw = new OutputStreamWriter(out, UTF_8)) {
      noteUtil.getGson().toJson(data, osw);
    }
  }
}
