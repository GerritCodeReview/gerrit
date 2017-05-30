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
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Cookies;
import java.util.ArrayList;
import java.util.Collection;

public class LocalComments {
  private final Project.NameKey project;
  private final Change.Id changeId;
  private final PatchSet.Id psId;
  private final StorageBackend storage;

  private static class InlineComment {
    final Project.NameKey project;
    final PatchSet.Id psId;
    final CommentInfo commentInfo;

    InlineComment(@Nullable Project.NameKey project, PatchSet.Id psId, CommentInfo commentInfo) {
      this.project = project;
      this.psId = psId;
      this.commentInfo = commentInfo;
    }
  }

  private static class StorageBackend {
    private final Storage storageBackend;

    StorageBackend() {
      storageBackend =
          (Storage.isLocalStorageSupported())
              ? Storage.getLocalStorageIfSupported()
              : Storage.getSessionStorageIfSupported();
    }

    String getItem(String key) {
      if (storageBackend == null) {
        return Cookies.getCookie(key);
      }
      return storageBackend.getItem(key);
    }

    void setItem(String key, String value) {
      if (storageBackend == null) {
        Cookies.setCookie(key, value);
        return;
      }
      storageBackend.setItem(key, value);
    }

    void removeItem(String key) {
      if (storageBackend == null) {
        Cookies.removeCookie(key);
        return;
      }
      storageBackend.removeItem(key);
    }

    Collection<String> getKeys() {
      if (storageBackend == null) {
        return Cookies.getCookieNames();
      }
      ArrayList<String> result = new ArrayList<>(storageBackend.getLength());
      for (int i = 0; i < storageBackend.getLength(); i++) {
        result.add(storageBackend.key(i));
      }
      return result;
    }
  }

  public LocalComments(@Nullable Project.NameKey project, Change.Id changeId) {
    this.project = project;
    this.changeId = changeId;
    this.psId = null;
    this.storage = new StorageBackend();
  }

  public LocalComments(@Nullable Project.NameKey project, PatchSet.Id psId) {
    this.project = project;
    this.changeId = psId.getParentKey();
    this.psId = psId;
    this.storage = new StorageBackend();
  }

  public String getReplyComment() {
    String comment = storage.getItem(getReplyCommentName());
    storage.removeItem(getReplyCommentName());
    return comment;
  }

  public void setReplyComment(String comment) {
    storage.setItem(getReplyCommentName(), comment.trim());
  }

  public boolean hasReplyComment() {
    return storage.getKeys().contains(getReplyCommentName());
  }

  public void removeReplyComment() {
    if (hasReplyComment()) {
      storage.removeItem(getReplyCommentName());
    }
  }

  private String getReplyCommentName() {
    if (project != null) {
      return "savedReplyComment-"
          + project.get()
          + PageLinks.PROJECT_CHANGE_DELIMITER
          + changeId.toString();
    }
    return "savedReplyComment-" + changeId.toString();
  }

  public static void saveInlineComments() {
    final StorageBackend storage = new StorageBackend();
    for (final String cookie : storage.getKeys()) {
      if (isInlineComment(cookie)) {
        InlineComment input = getInlineComment(cookie);
        if (input.commentInfo.id() == null) {
          CommentApi.createDraft(
              input.psId,
              Project.NameKey.asStringOrNull(input.project),
              input.commentInfo,
              new GerritCallback<CommentInfo>() {
                @Override
                public void onSuccess(CommentInfo result) {
                  storage.removeItem(cookie);
                }
              });
        } else {
          CommentApi.updateDraft(
              input.psId,
              Project.NameKey.asStringOrNull(input.project),
              input.commentInfo.id(),
              input.commentInfo,
              new GerritCallback<CommentInfo>() {
                @Override
                public void onSuccess(CommentInfo result) {
                  storage.removeItem(cookie);
                }

                @Override
                public void onFailure(Throwable caught) {
                  if (RestApi.isNotFound(caught)) {
                    // the draft comment, that was supposed to be updated,
                    // was deleted in the meantime
                    storage.removeItem(cookie);
                  } else {
                    super.onFailure(caught);
                  }
                }
              });
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
    storage.setItem(name, comment.message().trim());
  }

  public boolean hasInlineComments() {
    for (String cookie : storage.getKeys()) {
      if (isInlineComment(cookie)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInlineComment(String key) {
    return key.startsWith("patchCommentEdit-")
        || key.startsWith("patchReply-")
        || key.startsWith("patchComment-");
  }

  private static InlineComment getInlineComment(String key) {
    String path;
    Side side = Side.PARENT;
    int line = 0;
    CommentRange range;
    StorageBackend storage = new StorageBackend();

    String[] elements = key.split("-");
    int offset = 1;
    if (key.startsWith("patchReply-") || key.startsWith("patchCommentEdit-")) {
      offset = 2;
    }
    ChangeIdParser.Result id = ChangeIdParser.parse(elements[offset + 0]);
    PatchSet.Id psId = new PatchSet.Id(id.changeId, Integer.parseInt(elements[offset + 1]));
    path = atob(elements[offset + 2]);
    side = (Side.PARENT.toString().equals(elements[offset + 3])) ? Side.PARENT : Side.REVISION;
    range = null;
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
    CommentInfo info = CommentInfo.create(path, side, line, range, false);
    info.message(storage.getItem(key));
    if (key.startsWith("patchReply-")) {
      info.inReplyTo(elements[1]);
    } else if (key.startsWith("patchCommentEdit-")) {
      info.id(elements[1]);
    }
    InlineComment inlineComment = new InlineComment(id.project, psId, info);
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
    if (project != null) {
      result += project.get() + PageLinks.PROJECT_CHANGE_DELIMITER;
    }
    result +=
        changeId + "-" + psId.getId() + "-" + btoa(comment.path()) + "-" + comment.side() + "-";
    if (comment.hasRange()) {
      result +=
          "R"
              + comment.range().startLine()
              + ","
              + comment.range().startCharacter()
              + "-"
              + comment.range().endLine()
              + ","
              + comment.range().endCharacter();
    } else {
      result += comment.line();
    }
    return result;
  }

  private static native String btoa(String a) /*-{ return btoa(a); }-*/;

  private static native String atob(String b) /*-{ return atob(b); }-*/;
}
