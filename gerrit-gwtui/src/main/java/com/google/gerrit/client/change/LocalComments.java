// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.CommentRange;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.user.client.Cookies;

public class LocalComments {
  private Change.Id changeId;
  private PatchSet.Id psId;

  private static class InlineComment {
    public final PatchSet.Id psId;
    public final CommentInfo commentInfo;

    protected InlineComment(PatchSet.Id psId,
        CommentInfo commentInfo) {
      this.psId = psId;
      this.commentInfo = commentInfo;
    }
  }

  public LocalComments(Change.Id changeId) {
    this.changeId = changeId;
  }

  public LocalComments(PatchSet.Id psId) {
    this.changeId = psId.getParentKey();
    this.psId = psId;
  }

  public String getReplyComment() {
    String comment = Cookies.getCookie(getReplyCommentName());
    Cookies.removeCookie(getReplyCommentName());
    return comment;
  }

  public void setReplyComment(String comment) {
    Cookies.setCookie(getReplyCommentName(), comment.trim());
  }

  public boolean hasReplyComment() {
    if (Cookies.getCookieNames().contains(getReplyCommentName())) {
      return true;
    }
    return false;
  }

  public void removeReplyComment() {
    if (hasReplyComment()) {
      Cookies.removeCookie(getReplyCommentName());
    }
  }

  private String getReplyCommentName() {
    return "savedReplyComment-" + changeId.toString();
  }

  public static void saveInlineComments() {
    for (final String cookie : Cookies.getCookieNames()) {
      if (isInlineComment(cookie)) {
        GerritCallback<CommentInfo> cb = new GerritCallback<CommentInfo>() {
          @Override
          public void onSuccess(CommentInfo result) {
            Cookies.removeCookie(cookie);
          }
        };
        InlineComment input = getInlineComment(cookie);
        if (input.commentInfo.id() == null) {
          CommentApi.createDraft(input.psId, input.commentInfo, cb);
        } else {
          CommentApi.updateDraft(input.psId, input.commentInfo.id(), input.commentInfo, cb);
        }
      }
    }
  }

  public void setInlineComment(CommentInfo comment) {
    String name = getInlineCommentName(comment);
    if (name == null) {
      // Failed to get the store key -- so we can't continue.
      return;
    }
    Cookies.setCookie(name, comment.message().trim());
  }

  public boolean hasInlineComments() {
    for (String cookie : Cookies.getCookieNames()) {
      if (isInlineComment(cookie)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInlineComment(String cookie) {
    if (cookie.startsWith("patchCommentEdit-")
        || cookie.startsWith("patchReply-")
        || cookie.startsWith("patchComment-")) {
      return true;
    }
    return false;
  }

  private static InlineComment getInlineComment(String cookie) {
    String path = null;
    Side side = Side.PARENT;
    int line = 0;
    CommentRange range = null;

    String[] elements = cookie.split("-");
    int offset = 1;
    if (cookie.startsWith("patchReply-")
        || cookie.startsWith("patchCommentEdit-")) {
      offset = 2;
    }
    Change.Id changeId = new Change.Id(Integer.parseInt(elements[offset + 0]));
    PatchSet.Id psId =
        new PatchSet.Id(changeId, Integer.parseInt(elements[offset + 1]));
    path = atob(elements[offset + 2]);
    side = (Side.PARENT.toString() == elements[offset + 3]) ? Side.PARENT
        : Side.REVISION;
    if (elements[offset + 4].startsWith("R")) {
      String rangeStart = elements[offset + 4].substring(1);
      String rangeEnd = elements[offset + 5];
      String[] split = rangeStart.split(",");
      int sl = Integer.parseInt(split[0]);
      int sc = Integer.parseInt(split[1]);
      split = rangeEnd.split(",");
      int el = Integer.parseInt(split[0]);
      int ec = Integer.parseInt(split[1]);
      range = CommentRange.create(sl, sc, el, ec);
      line = sl;
    } else {
      line = Integer.parseInt(elements[offset + 4]);
    }
    CommentInfo info = CommentInfo.create(path, side, line, range);
    info.message(Cookies.getCookie(cookie));
    if (cookie.startsWith("patchReply-")) {
      info.inReplyTo(elements[1]);
    } else if (cookie.startsWith("patchCommentEdit-")) {
      info.id(elements[1]);
    }
    InlineComment inlineComment = new InlineComment(psId, info);
    return inlineComment;
  }

  private String getInlineCommentName(CommentInfo comment) {
    if (psId == null) {
      return null;
    }
    String result = "patchComment-";
    if (comment.id() != null) {
      result = "patchCommentEdit-" + comment.id() + "-";
    } else if (comment.inReplyTo() != null) {
      result = "patchReply-" + comment.inReplyTo() + "-";
    }
    result += changeId + "-" + psId.getId() + "-" + btoa(comment.path()) + "-"
        + comment.side() + "-";
    if (comment.hasRange()) {
      result += "R" + comment.range().startLine() + ","
          + comment.range().startCharacter() + "-" + comment.range().endLine()
          + "," + comment.range().endCharacter();
    } else {
      result += comment.line();
    }
    return result;
  }

  private static native String btoa(String a) /*-{ return btoa(a); }-*/;

  private static native String atob(String b) /*-{ return atob(b); }-*/;
}
