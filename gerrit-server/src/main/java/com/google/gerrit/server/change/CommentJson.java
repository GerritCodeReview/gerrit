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
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.lang.reflect.InvocationTargetException;
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
    return format(c, CommentInfo.class);
  }

  RobotCommentInfo format(RobotComment c) throws OrmException {
    return format(c, RobotCommentInfo.class);
  }

  private <IN extends Comment, OUT extends CommentInfo> OUT format(IN c,
      Class<OUT> clazz) throws OrmException {
    AccountLoader loader = null;
    if (fillAccounts) {
      loader = accountLoaderFactory.create(true);
    }
    OUT commentInfo = toCommentInfo(c, clazz, loader);
    if (fillAccounts) {
      loader.fill();
    }
    return commentInfo;
  }

  Map<String, List<CommentInfo>> format(Iterable<Comment> l)
      throws OrmException {
    return format(l, CommentInfo.class);
  }

  Map<String, List<RobotCommentInfo>> formatRobotComments(
      Iterable<RobotComment> l) throws OrmException {
    return format(l, RobotCommentInfo.class);
  }

  private <IN extends Comment, OUT extends CommentInfo> Map<String, List<OUT>> format(
      Iterable<IN> l, Class<OUT> clazz) throws OrmException {
    Map<String, List<OUT>> out = new TreeMap<>();
    AccountLoader accountLoader =
        fillAccounts ? accountLoaderFactory.create(true) : null;

    for (IN c : l) {
      OUT o = toCommentInfo(c, clazz, accountLoader);
      List<OUT> list = out.get(o.path);
      if (list == null) {
        list = new ArrayList<>();
        out.put(o.path, list);
      }
      o.path = null;
      list.add(o);
    }

    for (List<OUT> list : out.values()) {
      Collections.sort(list, COMMENT_INFO_ORDER);
    }

    if (accountLoader != null) {
      accountLoader.fill();
    }

    return out;
  }

  List<CommentInfo> formatCommentsAsList(Iterable<Comment> l)
      throws OrmException {
    return formatAsList(l, CommentInfo.class);
  }

  List<RobotCommentInfo> formatRobotCommentsAsList(Iterable<RobotComment> l)
      throws OrmException {
    return formatAsList(l, RobotCommentInfo.class);
  }

  private <IN extends Comment, OUT extends CommentInfo> List<OUT> formatAsList(
      Iterable<IN> l, Class<OUT> clazz) throws OrmException {
    AccountLoader accountLoader =
        fillAccounts ? accountLoaderFactory.create(true) : null;
    List<OUT> out = FluentIterable.from(l)
        .transform(c -> toCommentInfo(c, clazz, accountLoader))
        .toSortedList(COMMENT_INFO_ORDER);

    if (accountLoader != null) {
      accountLoader.fill();
    }

    return out;
  }

  private <IN extends Comment, OUT extends CommentInfo> OUT toCommentInfo(IN c,
      Class<OUT> clazz, AccountLoader loader) {
    try {
      OUT r = clazz.getDeclaredConstructor().newInstance();
      if (r instanceof RobotCommentInfo && c instanceof RobotComment) {
        RobotComment rc = (RobotComment) c;
        RobotCommentInfo rci = (RobotCommentInfo) r;
        rci.robotId = rc.robotId;
        rci.robotRunId = rc.robotRunId;
        rci.url = rc.url;
      }
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
    } catch (InstantiationException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
      throw new IllegalStateException(
          String.format("Cannot instantiate %s", clazz.getName()));
    }
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
