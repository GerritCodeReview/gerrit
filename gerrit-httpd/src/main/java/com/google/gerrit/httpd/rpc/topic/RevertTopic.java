// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.TopicUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

import javax.annotation.Nullable;

class RevertTopic extends Handler<TopicDetail> {
  interface Factory {
    RevertTopic create(ChangeSet.Id changeSetId, String message);
  }

  private final TopicControl.Factory topicControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final RevertedSender.Factory revertedSenderFactory;
  private final TopicDetailFactory.Factory topicDetailFactory;
  private final ReplicationQueue replication;

  private final ChangeSet.Id changeSetId;
  @Nullable
  private final String message;

  private final ChangeHookRunner hooks;

  private final GitRepositoryManager gitManager;
  private final PatchSetInfoFactory patchSetInfoFactory;

  private final PersonIdent myIdent;

  @Inject
  RevertTopic(final TopicControl.Factory topicControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final RevertedSender.Factory revertedSenderFactory,
      final TopicDetailFactory.Factory topicDetailFactory,
      @Assisted final ChangeSet.Id changeSetId,
      @Assisted @Nullable final String message, final ChangeHookRunner hooks,
      final GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ReplicationQueue replication,
      @GerritPersonIdent final PersonIdent myIdent) {
    this.topicControlFactory = topicControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.revertedSenderFactory = revertedSenderFactory;
    this.topicDetailFactory = topicDetailFactory;

    this.changeSetId = changeSetId;
    this.message = message;
    this.hooks = hooks;
    this.gitManager = gitManager;

    this.patchSetInfoFactory = patchSetInfoFactory;
    this.replication = replication;
    this.myIdent = myIdent;
  }

  @Override
  public TopicDetail call() throws NoSuchTopicException, OrmException,
      EmailException, NoSuchEntityException, ChangeSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      NoSuchChangeException, PatchSetInfoNotAvailableException {

    final Topic.Id topicId = changeSetId.getParentKey();
    final TopicControl control = topicControlFactory.validateFor(topicId);
    if (!control.canAddChangeSet()) {
      throw new NoSuchTopicException(topicId);
    }

    TopicUtil.revert(changeSetId, currentUser, message, db,
        revertedSenderFactory, hooks, gitManager, patchSetInfoFactory,
        replication, myIdent);

    return topicDetailFactory.create(topicId).call();
  }
}
