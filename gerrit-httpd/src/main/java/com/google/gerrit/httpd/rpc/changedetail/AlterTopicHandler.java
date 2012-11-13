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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.changedetail.AlterTopic;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;

import javax.annotation.Nullable;

class AlterTopicHandler extends Handler<ChangeDetail> {

  interface Factory {
    AlterTopicHandler create(@Assisted Change.Id changeId,
                             @Assisted("topic") String topic,
                             @Assisted("message") @Nullable String message);
  }

  private final Provider<AlterTopic> alterTopicProvider;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final Change.Id changeId;
  private final String topic;
  @Nullable
  private final String message;

  @Inject
  AlterTopicHandler(final Provider<AlterTopic> alterTopicProvider,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final Change.Id changeId,
      @Assisted("topic") final String topic,
      @Assisted("message") @Nullable final String message) {
    this.alterTopicProvider = alterTopicProvider;
    this.changeDetailFactory = changeDetailFactory;

    this.changeId = changeId;
    this.topic = topic;
    this.message = message;
  }

  @Override
  public ChangeDetail call() throws EmailException, IOException,
      NoSuchChangeException, NoSuchEntityException, OrmException,
      PatchSetInfoNotAvailableException, RepositoryNotFoundException,
      InvalidChangeOperationException {
    final AlterTopic alterTopic = alterTopicProvider.get();
    alterTopic.setChangeId(changeId);
    alterTopic.setTopic(topic);
    alterTopic.setMessage(message);
    alterTopic.call();
    return changeDetailFactory.create(changeId).call();
  }
}
