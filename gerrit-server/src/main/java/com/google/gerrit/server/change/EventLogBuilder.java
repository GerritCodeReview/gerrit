// Copyright (C) 2017 The Android Open Source Project
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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashBasedTable;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;

public class EventLogBuilder {

  private final HashBasedTable<Integer, String, List<CommentInfo>> byAuthor =
      HashBasedTable.create();
  private final List<ChangeMessageInfo> messages = new ArrayList<>();

  public EventLogBuilder addComments(Map<String, List<CommentInfo>> map) {
    for (String path : map.keySet()) {
      for (CommentInfo c : map.get(path)) {
        c.path = path;
        if (c.author != null) {
          int authorId = c.author._accountId;
          List<CommentInfo> l = byAuthor.get(authorId, path);
          if (l == null) {
            l = new ArrayList<>();
            byAuthor.put(authorId, path, l);
          }
          l.add(c);
        }
      }
    }
    return this;
  }

  private Map<String, List<CommentInfo>> comments(ChangeMessageInfo msg) {
    if (msg.author == null) {
      return Collections.emptyMap();
    }

    int authorId = msg.author._accountId;
    Map<String, List<CommentInfo>> map = byAuthor.rowMap().get(authorId);
    if (map == null) {
      return Collections.emptyMap();
    }

    Timestamp when = msg.date;
    // Populate reply messages with associated file comments.
    // Historically, comment and messages had no other relation but timestamp
    // they were created on.
    // Following code iterates over all author's comments and attaches them
    // to the earliest message.
    Map<String, List<CommentInfo>> match = new HashMap<>();
    Map<String, List<CommentInfo>> other = new HashMap<>();
    for (String path : map.keySet()) {
      for (CommentInfo c : map.get(path)) {
        List<CommentInfo> l;
        if (c.date.before(when)) {
          l = match.get(path);
          if (l == null) {
            l = new ArrayList<>();
            match.put(path, l);
          }
        } else {
          l = other.get(path);
          if (l == null) {
            l = new ArrayList<>();
            other.put(path, l);
          }
        }
        l.add(c);
      }
    }
    if (match.isEmpty()) {
      return Collections.emptyMap();
    } else if (other.isEmpty()) {
      byAuthor.row(authorId).clear();
    } else {
      byAuthor.row(authorId).putAll(other);
    }
    return match;
  }

  private EventLogBuilder addMessage(ChangeMessageInfo m) {
    m.comments = comments(m);
    messages.add(m);
    return this;
  }

  public EventLogBuilder addMessages(Collection<ChangeMessageInfo> ms) {
    for (ChangeMessageInfo m : ms) {
      addMessage(m);
    }
    return this;
  }

  public Collection<ChangeMessageInfo> build() {
    return messages;
  }
}
