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

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.PatchLineComment;

import java.util.HashMap;
import java.util.Map;

class RevisionNoteBuilder {
  private final Map<PatchLineComment.Key, PatchLineComment> comments;

  RevisionNoteBuilder(RevisionNote base) {
    if (base != null) {
      comments = Maps.newHashMapWithExpectedSize(base.comments.size());
      for (PatchLineComment c : base.comments) {
        addComment(c);
      }
    } else {
      comments = new HashMap<>();
    }
  }

  void addComment(PatchLineComment comment) {
    comments.put(comment.getKey(), comment);
  }

  byte[] build(CommentsInNotesUtil commentsUtil) {
    return commentsUtil.buildNote(PLC_ORDER.sortedCopy(comments.values()));
  }
}
