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

package com.google.gerrit.server.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.extensions.events.AbstractRevisionEvent;
import com.google.gerrit.server.extensions.events.ChangeEventUtil;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.io.IOException;

public class RevisionCreated {

  private final DynamicSet<RevisionCreatedListener> listeners;
  private final ChangeEventUtil util;

  @Inject
  RevisionCreated(DynamicSet<RevisionCreatedListener> listeners,
      ChangeEventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo uploader) {
    Event e = new Event(change, revision, uploader);
    for (RevisionCreatedListener l : listeners) {
      l.onRevisionCreated(e);
    }
  }

  public void fire(Change change, PatchSet patchSet, Account.Id uploader)
      throws OrmException {
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(patchSet),
          util.accountInfo(uploader));
    } catch ( PatchListNotAvailableException | GpgException | IOException e) {
      throw new OrmException(e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements RevisionCreatedListener.Event {
    private final AccountInfo uploader;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo uploader) {
      super(change, revision);
      this.uploader = uploader;
    }

    @Override
    public AccountInfo getUploader() {
      return uploader;
    }
  }
}
