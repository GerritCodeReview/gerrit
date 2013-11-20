// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class History extends FlowPanel {
  private CommentLinkProcessor clp;
  private Change.Id changeId;

  private final Set<Integer> loaded = new HashSet<Integer>();
  private final Map<AuthorRevision, List<CommentInfo>> byAuthor =
      new HashMap<AuthorRevision, List<CommentInfo>>();

  void set(CommentLinkProcessor clp, Change.Id id, ChangeInfo info) {
    this.clp = clp;
    this.changeId = id;

    JsArray<MessageInfo> messages = info.messages();
    if (messages != null) {
      for (MessageInfo msg : Natives.asList(messages)) {
        Message ui = new Message(this, msg);
        if (loaded.contains(msg._revisionNumber())) {
          ui.addComments(comments(msg));
        }
        add(ui);
      }
    }
  }

  CommentLinkProcessor getCommentLinkProcessor() {
    return clp;
  }

  Change.Id getChangeId() {
    return changeId;
  }

  void addComments(int id, NativeMap<JsArray<CommentInfo>> map) {
    loaded.add(id);

    for (String path : map.keySet()) {
      for (CommentInfo c : Natives.asList(map.get(path))) {
        c.setPath(path);
        if (c.author() != null) {
          AuthorRevision k = new AuthorRevision(c.author(), id);
          List<CommentInfo> l = byAuthor.get(k);
          if (l == null) {
            l = new ArrayList<CommentInfo>();
            byAuthor.put(k, l);
          }
          l.add(c);
        }
      }
    }
  }

  void load(final int revisionNumber) {
    if (revisionNumber > 0 && loaded.add(revisionNumber)) {
      ChangeApi.revision(new PatchSet.Id(changeId, revisionNumber))
        .view("comments")
        .get(new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
          @Override
          public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
            addComments(revisionNumber, result);
            update(revisionNumber);
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        });
    }
  }

  private void update(int revisionNumber) {
    for (Widget child : getChildren()) {
      Message ui = (Message) child;
      MessageInfo info = ui.getMessageInfo();
      if (info._revisionNumber() == revisionNumber) {
        ui.addComments(comments(info));
      }
    }
  }

  private List<CommentInfo> comments(MessageInfo msg) {
    if (msg.author() == null) {
      return Collections.emptyList();
    }

    AuthorRevision k = new AuthorRevision(msg.author(), msg._revisionNumber());
    List<CommentInfo> list = byAuthor.get(k);
    if (list == null) {
      return Collections.emptyList();
    }

    Timestamp when = msg.date();
    List<CommentInfo> match = new ArrayList<CommentInfo>();
    List<CommentInfo> other = new ArrayList<CommentInfo>();
    for (int i = 0; i < list.size(); i++) {
      CommentInfo c = list.get(i);
      if (c.updated().compareTo(when) <= 0) {
        match.add(c);
      } else {
        other.add(c);
      }
    }
    if (match.isEmpty()) {
      return Collections.emptyList();
    } else if (other.isEmpty()) {
      byAuthor.remove(k);
    } else {
      byAuthor.put(k, other);
    }
    return match;
  }

  private static final class AuthorRevision {
    final int author;
    final int revision;

    AuthorRevision(AccountInfo author, int revision) {
      this.author = author._account_id();
      this.revision = revision;
    }

    @Override
    public int hashCode() {
      return author * 31 + revision;
    }

    @Override
    public boolean equals(Object o) {
      AuthorRevision b = (AuthorRevision) o;
      return author == b.author && revision == b.revision;
    }
  }
}
