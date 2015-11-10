// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.extensions.events.AbstractRevisionEvent;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.extensions.events.ChangeEventUtil;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;

public class CommentAdded {
  private static final Logger log =
      LoggerFactory.getLogger(CommentAdded.class);

  private final DynamicSet<CommentAddedListener> listeners;
  private final ChangeEventUtil util;

  @Inject
  CommentAdded(DynamicSet<CommentAddedListener> listeners,
      ChangeEventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision, AccountInfo author,
      String comment, Map<String, ApprovalInfo> approvals) {
    Event e = new Event(change, revision, author, comment, approvals);
    for (CommentAddedListener l : listeners) {
      l.onCommentAdded(e);
    }
  }

  public void fire(Change change, PatchSet ps, Account author,
      String comment, Map<String, Short> approvals, Timestamp ts) {
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(ps),
          util.accountInfo(author),
          comment,
          util.approvals(author, approvals, ts));
    } catch (PatchListNotAvailableException | GpgException | IOException
        | OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements CommentAddedListener.Event {

    private final AccountInfo author;
    private final String comment;
    private final Map<String, ApprovalInfo> approvals;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo author,
        String comment, Map<String, ApprovalInfo> approvals) {
      super(change, revision);
      this.author = author;
      this.comment = comment;
      this.approvals = approvals;
    }

    @Override
    public AccountInfo getAuthor() {
      return author;
    }

    @Override
    public String getComment() {
      return comment;
    }

    @Override
    public Map<String, ApprovalInfo> getApprovals() {
      return approvals;
    }
  }
}
