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

package com.google.gerrit.server.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class AbandonUtil {
  private static final Logger log = LoggerFactory
      .getLogger(AbandonUtil.class);

  private final ChangeCleanupConfig cfg;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final QueryProcessor queryProcessor;
  private final ChangeQueryBuilder queryBuilder;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Abandon abandon;

  @Inject
  AbandonUtil(
      ChangeCleanupConfig cfg,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      QueryProcessor queryProcessor,
      ChangeQueryBuilder queryBuilder,
      ChangeControl.GenericFactory changeControlFactory,
      Abandon abandon) {
    this.cfg = cfg;
    this.identifiedUserFactory = identifiedUserFactory;
    this.queryProcessor = queryProcessor;
    this.queryBuilder = queryBuilder;
    this.changeControlFactory = changeControlFactory;
    this.abandon = abandon;
  }

  public void abandonInactiveOpenChanges() {
    if (cfg.getAbandonAfter() <= 0) {
      return;
    }

    try {
      String query = "status:open age:"
          + TimeUnit.MILLISECONDS.toMinutes(cfg.getAbandonAfter())
          + "m";
      List<ChangeData> changesToAbandon = queryProcessor.enforceVisibility(false)
          .queryChanges(queryBuilder.parse(query)).changes();
      for (ChangeData cd : changesToAbandon) {
        abandon.abandon(changeControl(cd.change()), cfg.getAbandonMessage(), null);
      }
    } catch (Throwable e) {
      log.error("Failed to auto-abandon inactive open changes.", e);
    }
  }

  private ChangeControl changeControl(Change c) throws NoSuchChangeException {
    return changeControlFactory.controlFor(
        c, identifiedUserFactory.create(c.getOwner()));
  }
}
