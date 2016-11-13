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

import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountJson;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventUtil {
  private static final Logger log = LoggerFactory.getLogger(EventUtil.class);

  private final ChangeData.Factory changeDataFactory;
  private final Provider<ReviewDb> db;
  private final ChangeJson changeJson;

  @Inject
  EventUtil(
      ChangeJson.Factory changeJsonFactory,
      ChangeData.Factory changeDataFactory,
      Provider<ReviewDb> db) {
    this.changeDataFactory = changeDataFactory;
    this.db = db;
    EnumSet<ListChangesOption> opts = EnumSet.allOf(ListChangesOption.class);
    opts.remove(ListChangesOption.CHECK);
    this.changeJson = changeJsonFactory.create(opts);
  }

  public ChangeInfo changeInfo(Change change) throws OrmException {
    return changeJson.format(change);
  }

  public RevisionInfo revisionInfo(Project project, PatchSet ps)
      throws OrmException, PatchListNotAvailableException, GpgException, IOException {
    return revisionInfo(project.getNameKey(), ps);
  }

  public RevisionInfo revisionInfo(Project.NameKey project, PatchSet ps)
      throws OrmException, PatchListNotAvailableException, GpgException, IOException {
    ChangeData cd = changeDataFactory.create(db.get(), project, ps.getId().getParentKey());
    ChangeControl ctl = cd.changeControl();
    return changeJson.getRevisionInfo(ctl, ps);
  }

  public AccountInfo accountInfo(Account a) {
    if (a == null || a.getId() == null) {
      return null;
    }
    return AccountJson.toAccountInfo(a);
  }

  public Map<String, ApprovalInfo> approvals(
      Account a, Map<String, Short> approvals, Timestamp ts) {
    Map<String, ApprovalInfo> result = new HashMap<>();
    for (Map.Entry<String, Short> e : approvals.entrySet()) {
      Integer value = e.getValue() != null ? Integer.valueOf(e.getValue()) : null;
      result.put(e.getKey(), ChangeJson.getApprovalInfo(a.getId(), value, null, ts));
    }
    return result;
  }

  public void logEventListenerError(Object event, Object listener, Exception error) {
    if (log.isDebugEnabled()) {
      log.debug(
          String.format(
              "Error in event listener %s for event %s",
              listener.getClass().getName(), event.getClass().getName()),
          error);
    } else {
      log.warn(
          "Error in listener {} for event {}: {}",
          listener.getClass().getName(),
          event.getClass().getName(),
          error.getMessage());
    }
  }

  public static void logEventListenerError(Object listener, Exception error) {
    if (log.isDebugEnabled()) {
      log.debug(String.format("Error in event listener %s", listener.getClass().getName()), error);
    } else {
      log.warn("Error in listener {}: {}", listener.getClass().getName(), error.getMessage());
    }
  }
}
