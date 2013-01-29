// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Objects.firstNonNull;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.GetDraft.Comment;
import com.google.gerrit.server.change.GetDraft.Side;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class ListDrafts implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> db;

  @Inject
  ListDrafts(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public Object apply(RevisionResource rsrc) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    Map<String, List<Comment>> out = Maps.newTreeMap();
    for (PatchLineComment c : db.get().patchComments()
        .draftByPatchSetAuthor(
            rsrc.getPatchSet().getId(),
            rsrc.getAccountId())) {
      Comment o = new Comment(c);
      List<Comment> list = out.get(o.path);
      if (list == null) {
        list = Lists.newArrayList();
        out.put(o.path, list);
      }
      o.path = null;
      list.add(o);
    }
    for (List<Comment> list : out.values()) {
      Collections.sort(list, new Comparator<Comment>() {
        @Override
        public int compare(Comment a, Comment b) {
          int c = firstNonNull(a.side, Side.REVISION).ordinal()
                - firstNonNull(b.side, Side.REVISION).ordinal();
          if (c == 0) {
            c = firstNonNull(a.line, 0) - firstNonNull(b.line, 0);
          }
          if (c == 0) {
            c = a.id.compareTo(b.id);
          }
          return c;
        }
      });
    }
    return out;
  }
}
