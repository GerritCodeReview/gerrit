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
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.change.CommentInfo;
import com.google.gerrit.server.change.CommentInfo.Side;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class ListDraftComments implements RestReadView<RevisionResource> {
  protected final Provider<ReviewDb> db;
  private final AccountInfo.Loader.Factory accountLoaderFactory;

  @Inject
  ListDraftComments(Provider<ReviewDb> db, AccountInfo.Loader.Factory alf) {
    this.db = db;
    this.accountLoaderFactory = alf;
  }

  protected Iterable<PatchLineComment> listComments(RevisionResource rsrc)
      throws OrmException {
    return db.get().patchComments()
        .draftByPatchSetAuthor(
            rsrc.getPatchSet().getId(),
            rsrc.getAccountId());
  }

  protected boolean includeAuthorInfo() {
    return false;
  }

  @Override
  public Object apply(RevisionResource rsrc) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    Map<String, List<CommentInfo>> out = Maps.newTreeMap();
    AccountInfo.Loader accountLoader =
        includeAuthorInfo() ? accountLoaderFactory.create(true) : null;
    for (PatchLineComment c : listComments(rsrc)) {
      CommentInfo o = new CommentInfo(c, accountLoader);
      List<CommentInfo> list = out.get(o.path);
      if (list == null) {
        list = Lists.newArrayList();
        out.put(o.path, list);
      }
      o.path = null;
      list.add(o);
    }
    for (List<CommentInfo> list : out.values()) {
      Collections.sort(list, new Comparator<CommentInfo>() {
        @Override
        public int compare(CommentInfo a, CommentInfo b) {
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
    if (accountLoader != null) {
      accountLoader.fill();
    }
    return out;
  }
}
