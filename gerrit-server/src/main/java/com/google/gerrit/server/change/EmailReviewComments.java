// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.PostReview.NotifyHandling;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class EmailReviewComments implements Runnable, RequestContext {
  private static final Logger log = LoggerFactory.getLogger(EmailReviewComments.class);

  interface Factory {
    EmailReviewComments create(
        NotifyHandling notify,
        Change change,
        PatchSet patchSet,
        Account.Id authorId,
        ChangeMessage message,
        List<PatchLineComment> comments);
  }

  private final WorkQueue workQueue;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final CommentSender.Factory commentSenderFactory;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ThreadLocalRequestContext requestContext;

  private final PostReview.NotifyHandling notify;
  private final Change change;
  private final PatchSet patchSet;
  private final Account.Id authorId;
  private final ChangeMessage message;
  private List<PatchLineComment> comments;
  private ReviewDb db;

  @Inject
  EmailReviewComments (
      WorkQueue workQueue,
      PatchSetInfoFactory patchSetInfoFactory,
      CommentSender.Factory commentSenderFactory,
      SchemaFactory<ReviewDb> schemaFactory,
      ThreadLocalRequestContext requestContext,
      @Assisted NotifyHandling notify,
      @Assisted Change change,
      @Assisted PatchSet patchSet,
      @Assisted Account.Id authorId,
      @Assisted ChangeMessage message,
      @Assisted List<PatchLineComment> comments) {
    this.workQueue = workQueue;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commentSenderFactory = commentSenderFactory;
    this.schemaFactory = schemaFactory;
    this.requestContext = requestContext;
    this.notify = notify;
    this.change = change;
    this.patchSet = patchSet;
    this.authorId = authorId;
    this.message = message;
    this.comments = comments;
  }

  void sendAsync() {
    workQueue.getDefaultQueue().submit(this);
  }

  @Override
  public void run() {
    try {
      requestContext.setContext(this);

      comments = Lists.newArrayList(comments);
      Collections.sort(comments, new Comparator<PatchLineComment>() {
        @Override
        public int compare(PatchLineComment a, PatchLineComment b) {
          int cmp = path(a).compareTo(path(b));
          if (cmp != 0) {
            return cmp;
          }

          // 0 is ancestor, 1 is revision. Sort ancestor first.
          cmp = a.getSide() - b.getSide();
          if (cmp != 0) {
            return cmp;
          }

          return a.getLine() - b.getLine();
        }

        private String path(PatchLineComment c) {
          return c.getKey().getParentKey().getFileName();
        }
      });

      CommentSender cm = commentSenderFactory.create(notify, change);
      cm.setFrom(authorId);
      cm.setPatchSet(patchSet, patchSetInfoFactory.get(change, patchSet));
      cm.setChangeMessage(message);
      cm.setPatchLineComments(comments);
      cm.send();
    } catch (Exception e) {
      log.error("Cannot email comments for " + patchSet.getId(), e);
    } finally {
      requestContext.setContext(null);
      if (db != null) {
        db.close();
        db = null;
      }
    }
  }

  @Override
  public String toString() {
    return "send-email comments";
  }

  @Override
  public CurrentUser getCurrentUser() {
    return null;
  }

  @Override
  public Provider<ReviewDb> getReviewDbProvider() {
    return new Provider<ReviewDb>() {
      @Override
      public ReviewDb get() {
        if (db == null) {
          try {
            db = schemaFactory.open();
          } catch (OrmException e) {
            throw new ProvisionException("Cannot open ReviewDb", e);
          }
        }
        return db;
      }
    };
  }
}
