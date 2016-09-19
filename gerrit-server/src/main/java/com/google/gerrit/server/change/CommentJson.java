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

import static com.google.gerrit.server.CommentsUtil.COMMENT_INFO_ORDER;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class CommentJson {

  private final AccountLoader.Factory accountLoaderFactory;

  private boolean fillAccounts = true;
  private boolean fillPatchSet;

  @Inject
  CommentJson(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  CommentJson setFillAccounts(boolean fillAccounts) {
    this.fillAccounts = fillAccounts;
    return this;
  }

  CommentJson setFillPatchSet(boolean fillPatchSet) {
    this.fillPatchSet = fillPatchSet;
    return this;
  }

  CommentInfo format(Comment c) throws OrmException {
    AccountLoader loader = null;
    if (fillAccounts) {
      loader = accountLoaderFactory.create(true);
    }
    CommentInfo commentInfo = toCommentInfo(c, loader);
    if (fillAccounts) {
      loader.fill();
    }
    return commentInfo;
  }

  Map<String, List<CommentInfo>> format(Iterable<Comment> l)
      throws OrmException {
    Map<String, List<CommentInfo>> out = new TreeMap<>();
    AccountLoader accountLoader = fillAccounts
        ? accountLoaderFactory.create(true)
        : null;

    for (Comment c : l) {
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
      Collections.sort(list, COMMENT_INFO_ORDER);
    }

    if (accountLoader != null) {
      accountLoader.fill();
    }

    return out;
  }

  List<CommentInfo> formatAsList(Iterable<Comment> l)
      throws OrmException {
    AccountLoader accountLoader = fillAccounts
        ? accountLoaderFactory.create(true)
        : null;
    List<CommentInfo> out = FluentIterable
        .from(l)
        .transform(c -> toCommentInfo(c, accountLoader))
        .toSortedList(COMMENT_INFO_ORDER);

    if (accountLoader != null) {
      accountLoader.fill();
    }

    return out;
  }

  private CommentInfo toCommentInfo(Comment c, AccountLoader loader) {
    CommentInfo r = new CommentInfo();
    if (fillPatchSet) {
      r.patchSet = c.key.patchSetId;
    }
    r.id = Url.encode(c.key.uuid);
    r.path = c.key.filename;
    if (c.side <= 0) {
      r.side = Side.PARENT;
      if (c.side < 0) {
        r.parent = -c.side;
      }
    }
    if (c.lineNbr > 0) {
      r.line = c.lineNbr;
    }
    r.inReplyTo = Url.encode(c.parentUuid);
    r.message = Strings.emptyToNull(c.message);
    r.updated = c.writtenOn;
    r.range = toRange(c.range);
    r.tag = c.tag;
    if (loader != null) {
      r.author = loader.get(c.author.getId());
    }
    return r;
  }

  private Range toRange(Comment.Range commentRange) {
    Range range = null;
    if (commentRange != null) {
      range = new Range();
      range.startLine = commentRange.startLine;
      range.startCharacter = commentRange.startChar;
      range.endLine = commentRange.endLine;
      range.endCharacter = commentRange.endChar;
    }
    return range;
  }
}
