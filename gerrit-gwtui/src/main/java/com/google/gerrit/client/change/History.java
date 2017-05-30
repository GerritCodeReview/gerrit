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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.MessageInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class History extends FlowPanel {
  private CommentLinkProcessor clp;
  private ReplyAction replyAction;
  private Change.Id changeId;
  private Project.NameKey project;

  private final Map<Integer, List<CommentInfo>> byAuthor = new HashMap<>();

  void set(CommentLinkProcessor clp, ReplyAction ra, Change.Id id, ChangeInfo info) {
    this.clp = clp;
    this.replyAction = ra;
    this.changeId = id;
    this.project = info.projectNameKey();

    JsArray<MessageInfo> messages = info.messages();
    if (messages != null) {
      for (MessageInfo msg : Natives.asList(messages)) {
        Message ui = new Message(this, msg);
        ui.addComments(comments(msg));
        add(ui);
      }
      autoOpen(ChangeScreen.myLastReply(info));
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

  @Nullable
  Project.NameKey getProject() {
    return project;
  }

  void replyTo(MessageInfo info) {
    replyAction.onReply(info);
  }

  void addComments(NativeMap<JsArray<CommentInfo>> map) {
    for (String path : map.keySet()) {
      for (CommentInfo c : Natives.asList(map.get(path))) {
        c.path(path);
        if (c.author() != null) {
          int authorId = c.author()._accountId();
          List<CommentInfo> l = byAuthor.get(authorId);
          if (l == null) {
            l = new ArrayList<>();
            byAuthor.put(authorId, l);
          }
          l.add(c);
        }
      }
    }
  }

  private List<CommentInfo> comments(MessageInfo msg) {
    if (msg.author() == null) {
      return Collections.emptyList();
    }

    int authorId = msg.author()._accountId();
    List<CommentInfo> list = byAuthor.get(authorId);
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
      byAuthor.remove(authorId);
    } else {
      byAuthor.put(authorId, other);
    }
    return match;
  }
}
