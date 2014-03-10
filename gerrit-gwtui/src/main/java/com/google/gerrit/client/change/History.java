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
  private ReplyAction replyAction;
  private Change.Id changeId;

  private final Set<Integer> loaded = new HashSet<>();
  private final Map<AuthorRevision, List<CommentInfo>> byAuthor =
      new HashMap<>();

  private final List<Integer> toLoad = new ArrayList<>(4);
  private int active;

  void set(CommentLinkProcessor clp, ReplyAction ra,
      Change.Id id, ChangeInfo info) {
    this.clp = clp;
    this.replyAction = ra;
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

      MessageInfo last = ChangeScreen2.myLastReply(info);
      autoOpen(last != null ? last.date() : null);
    }
  }

  private void autoOpen(Timestamp lastReply) {
    if (lastReply == null) {
      for (Widget child : getChildren()) {
        ((Message) child).autoOpen();
      }
    } else {
      for (int i = getChildren().size() - 1; i >= 0; i--) {
        Message ui = (Message) getChildren().get(i);
        MessageInfo msg = ui.getMessageInfo();
        if (lastReply.compareTo(msg.date()) < 0) {
          ui.autoOpen();
        } else {
          break;
        }
      }
    }
  }

  CommentLinkProcessor getCommentLinkProcessor() {
    return clp;
  }

  Change.Id getChangeId() {
    return changeId;
  }

  void replyTo(MessageInfo info) {
    replyAction.onReply(info);
  }

  void addComments(int id, NativeMap<JsArray<CommentInfo>> map) {
    loaded.add(id);

    for (String path : map.keySet()) {
      for (CommentInfo c : Natives.asList(map.get(path))) {
        c.path(path);
        if (c.author() != null) {
          AuthorRevision k = new AuthorRevision(c.author(), id);
          List<CommentInfo> l = byAuthor.get(k);
          if (l == null) {
            l = new ArrayList<>();
            byAuthor.put(k, l);
          }
          l.add(c);
        }
      }
    }
  }

  void load(int revisionNumber) {
    if (revisionNumber > 0 && loaded.add(revisionNumber)) {
      toLoad.add(revisionNumber);
      start();
    }
  }

  private void start() {
    if (active >= 2 || toLoad.isEmpty() || !isAttached()) {
      return;
    }

    final int revisionNumber = toLoad.remove(0);
    active++;
    ChangeApi.revision(new PatchSet.Id(changeId, revisionNumber))
      .view("comments")
      .get(new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
        @Override
        public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
          addComments(revisionNumber, result);
          update(revisionNumber);
          --active;
          start();
        }

        @Override
        public void onFailure(Throwable caught) {
          loaded.remove(revisionNumber);
          loaded.removeAll(toLoad);
          toLoad.clear();
          active--;
        }
      });
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
    List<CommentInfo> match = new ArrayList<>();
    List<CommentInfo> other = new ArrayList<>();
    for (CommentInfo c : list) {
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
      if (!(o instanceof AuthorRevision)) {
        return false;
      }
      AuthorRevision b = (AuthorRevision) o;
      return author == b.author && revision == b.revision;
    }
  }
}
