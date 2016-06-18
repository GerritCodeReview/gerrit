// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class ReviewerDeleted {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewerDeleted.class);

  private final DynamicSet<ReviewerDeletedListener> listeners;
  private final EventUtil util;

  @Inject
  ReviewerDeleted(DynamicSet<ReviewerDeletedListener> listeners,
      EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo reviewer, String message,
      Map<String, Short> newApprovals,
      Map<String, Short> oldApprovals) {
    Event e = new Event(change, revision, reviewer, message,
        newApprovals, oldApprovals);
    for (ReviewerDeletedListener listener : listeners) {
      listener.onReviewerDeleted(e);
    }
  }

  public void fire(Change change, PatchSet patchSet,
      Account reviewer, String message,
      Map<String, Short> newApprovals,
      Map<String, Short> oldApprovals) {
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(change.getProject(), patchSet),
          util.accountInfo(reviewer),
          message,
          newApprovals,
          oldApprovals);
    } catch (PatchListNotAvailableException | GpgException | IOException
        | OrmException e) {
      log.error("Couldn't fire event", e);
    }

  }

  private static class Event extends AbstractRevisionEvent
      implements ReviewerDeletedListener.Event {
    private final AccountInfo reviewer;
    private final String comment;
    private final Map<String, Short> newApprovals;
    private final Map<String, Short> oldApprovals;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo reviewer,
        String comment, Map<String, Short> newApprovals,
        Map<String, Short> oldApprovals) {
      super(change, revision);
      this.reviewer = reviewer;
      this.comment = comment;
      this.newApprovals = newApprovals;
      this.oldApprovals = oldApprovals;
    }

    @Override
    public AccountInfo getReviewer() {
      return reviewer;
    }

    @Override
    public String getComment() {
      return comment;
    }

    @Override
    public Map<String, Short> getNewApprovals() {
      return newApprovals;
    }

    @Override
    public Map<String, Short> getOldApprovals() {
      return oldApprovals;
    }
  }
}
