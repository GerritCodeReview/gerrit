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

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.RevisionEvent;
import java.sql.Timestamp;

public abstract class AbstractRevisionEvent extends AbstractChangeEvent implements RevisionEvent {

  private final RevisionInfo revisionInfo;

  protected AbstractRevisionEvent(
      ChangeInfo change,
      RevisionInfo revision,
      AccountInfo who,
      Timestamp when,
      NotifyHandling notify) {
    super(change, who, when, notify);
    revisionInfo = revision;
  }

  @Override
  public RevisionInfo getRevision() {
    return revisionInfo;
  }
}
