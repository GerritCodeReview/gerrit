// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Singleton
class CommentJson {

  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  CommentJson(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  CommentInfo format(PatchLineComment c) throws OrmException {
    return format(c, true);
  }

  CommentInfo format(PatchLineComment c, boolean fill) throws OrmException {
    AccountLoader loader = null;
    if (fill) {
      loader = accountLoaderFactory.create(true);
    }
    CommentInfo commentInfo = toCommentInfo(c, loader);
    if (fill) {
      loader.fill();
    }
    return commentInfo;
  }

  Map<String, List<CommentInfo>> format(Iterable<PatchLineComment> l,
      boolean fill) throws OrmException {
    Map<String, List<CommentInfo>> out = new TreeMap<>();
    AccountLoader accountLoader = fill
        ? accountLoaderFactory.create(true)
        : null;

    for (PatchLineComment c : l) {
      CommentInfo o = toCommentInfo(c, accountLoader);
      List<CommentInfo> list = out.get(o.path);
      if (list == null) {
        list = new ArrayList<>();
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

  private CommentInfo toCommentInfo(PatchLineComment c, AccountLoader loader) {
    CommentInfo r = new CommentInfo();
    r.id = Url.encode(c.getKey().get());
    r.path = c.getKey().getParentKey().getFileName();
    if (c.getSide() == 0) {
      r.side = Side.PARENT;
    }
    if (c.getLine() > 0) {
      r.line = c.getLine();
    }
    r.inReplyTo = Url.encode(c.getParentUuid());
    r.message = Strings.emptyToNull(c.getMessage());
    r.updated = c.getWrittenOn();
    r.range = toRange(c.getRange());
    if (loader != null) {
      r.author = loader.get(c.getAuthor());
    }
    return r;
  }

  private Range toRange(CommentRange commentRange) {
    Range range = null;
    if (commentRange != null) {
      range = new Range();
      range.startLine = commentRange.getStartLine();
      range.startCharacter = commentRange.getStartCharacter();
      range.endLine = commentRange.getEndLine();
      range.endCharacter = commentRange.getEndCharacter();
    }
    return range;
  }
}
