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

import com.google.gerrit.common.data.ChangeSetDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetElement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.List;

/** Creates a {@link ChangeSetDetail} from a {@link ChangeSet}. */
class ChangeSetDetailFactory extends Handler<ChangeSetDetail> {

  interface Factory {
    ChangeSetDetailFactory create(@Assisted("csIdNew") ChangeSet.Id csIdA);
  }

  private final ChangeSetInfoFactory infoFactory;
  private final ReviewDb db;
  private final TopicControl.Factory topicControlFactory;
  private final ChangeSet.Id csIdNew;

  private ChangeSetDetail detail;
  TopicControl control;
  ChangeSet changeSet;

  @Inject
  ChangeSetDetailFactory(final ChangeSetInfoFactory csif, final ReviewDb db,
      final TopicControl.Factory topicControlFactory,
      @Assisted("csIdNew") final ChangeSet.Id csIdNew) {
    this.infoFactory = csif;
    this.db = db;
    this.topicControlFactory = topicControlFactory;
    this.csIdNew = csIdNew;
  }

  @Override
  public ChangeSetDetail call() throws OrmException, NoSuchEntityException,
      ChangeSetInfoNotAvailableException, NoSuchTopicException {
    if (control == null || changeSet == null) {
      control = topicControlFactory.validateFor(csIdNew.getParentKey());
      changeSet = db.changeSets().get(csIdNew);
      if (changeSet == null) {
        throw new NoSuchEntityException();
      }
    }

    // TODO support diff between changesets
    final List<ChangeSetElement> changeSetElements = db.changeSetElements().byChangeSet(csIdNew).toList();
    List<Change> changes = new ArrayList<Change>();
    for (ChangeSetElement cse : changeSetElements) {
      changes.add(db.changes().get(cse.getChangeId()));
    }

    detail = new ChangeSetDetail();
    detail.setChangeSet(changeSet);

    detail.setInfo(infoFactory.get(csIdNew));
    detail.setChanges(changes);

    return detail;
  }
}
