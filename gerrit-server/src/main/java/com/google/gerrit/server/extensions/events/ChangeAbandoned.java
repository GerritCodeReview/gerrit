// Copyright (C) 2015 The Android Open Source Project
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
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ChangeAbandoned {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeAbandoned.class);

  private final DynamicSet<ChangeAbandonedListener> listeners;
  private final ChangeJson.Factory changeJsonFactory;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<ReviewDb> db;

  @Inject
  ChangeAbandoned(DynamicSet<ChangeAbandonedListener> listeners,
      ChangeJson.Factory changeJsonFactory,
      ChangeData.Factory changeDataFactory,
      Provider<ReviewDb> db) {
    this.listeners = listeners;
    this.changeJsonFactory = changeJsonFactory;
    this.changeDataFactory = changeDataFactory;
    this.db = db;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo abandoner, String reason) {
    Event e = new Event(change, revision, abandoner, reason);
    for (ChangeAbandonedListener l : listeners) {
      l.onChangeAbandoned(e);
    }
  }

  public void fire(Change change, PatchSet ps, Account abandoner, String reason) {
    try {
      ChangeJson changeJson = changeJsonFactory.create(ChangeJson.NO_OPTIONS);
      ChangeInfo changeInfo = changeJson.format(change);
      ChangeData cd = changeDataFactory.create(db.get(), change);
      ChangeControl ctl = cd.changeControl();
      RevisionInfo revisionInfo = changeJson.toRevisionInfo(ctl, ps);
      AccountInfo ai = new AccountInfo(abandoner.getId().get());
      ai.email = abandoner.getPreferredEmail();
      ai.name = abandoner.getFullName();
      ai.username =  abandoner.getUserName();
      fire(changeInfo, revisionInfo, ai, reason);
    } catch (PatchListNotAvailableException | GpgException | IOException
        | OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event implements ChangeAbandonedListener.Event {
    private final ChangeInfo change;
    private final RevisionInfo revision;
    private final AccountInfo abandoner;
    private final String reason;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo abandoner,
        String reason) {
      this.change = change;
      this.revision = revision;
      this.abandoner = abandoner;
      this.reason = reason;
    }

    @Override
    public RevisionInfo getRevision() {
      return revision;
    }

    @Override
    public ChangeInfo getChange() {
      return change;
    }

    @Override
    public AccountInfo getAbandoner() {
      return abandoner;
    }

    @Override
    public String getReason() {
      return reason;
    }
  }
}
