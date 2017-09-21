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
import com.google.gerrit.extensions.events.ChangeEvent;
import java.sql.Timestamp;

public abstract class AbstractChangeEvent implements ChangeEvent {
  private final ChangeInfo changeInfo;
  private final AccountInfo who;
  private final Timestamp when;
  private final NotifyHandling notify;

  protected AbstractChangeEvent(
      ChangeInfo change, AccountInfo who, Timestamp when, NotifyHandling notify) {
    this.changeInfo = change;
    this.who = who;
    this.when = when;
    this.notify = notify;
  }

  @Override
  public ChangeInfo getChange() {
    return changeInfo;
  }

  @Override
  public AccountInfo getWho() {
    return who;
  }

  @Override
  public Timestamp getWhen() {
    return when;
  }

  @Override
  public NotifyHandling getNotify() {
    return notify;
  }
}
