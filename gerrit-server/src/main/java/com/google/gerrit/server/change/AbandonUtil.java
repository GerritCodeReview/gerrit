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

import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class AbandonUtil {
  private static final Logger log = LoggerFactory.getLogger(AbandonUtil.class);

  private final ChangeCleanupConfig cfg;
  private final InternalUser.Factory internalUserFactory;
  private final ChangeQueryProcessor queryProcessor;
  private final ChangeQueryBuilder queryBuilder;
  private final Abandon abandon;

  @Inject
  AbandonUtil(
      ChangeCleanupConfig cfg,
      InternalUser.Factory internalUserFactory,
      ChangeQueryProcessor queryProcessor,
      ChangeQueryBuilder queryBuilder,
      Abandon abandon) {
    this.cfg = cfg;
    this.internalUserFactory = internalUserFactory;
    this.queryProcessor = queryProcessor;
    this.queryBuilder = queryBuilder;
    this.abandon = abandon;
  }

  public void abandonInactiveOpenChanges() {
    if (cfg.getAbandonAfter() <= 0) {
      return;
    }

    try {
      String query = "status:new age:"
          + TimeUnit.MILLISECONDS.toMinutes(cfg.getAbandonAfter())
          + "m";
      if (!cfg.getAbandonIfMergeable()) {
        query += " -is:mergeable";
      }
      List<ChangeData> changesToAbandon = queryProcessor.enforceVisibility(false)
          .query(queryBuilder.parse(query)).entities();
      int count = 0;
      for (ChangeData cd : changesToAbandon) {
        try {
          abandon.abandon(changeControl(cd), cfg.getAbandonMessage());
          count++;
        } catch (ResourceConflictException e) {
          // Change was already merged or abandoned.
        } catch (Throwable e) {
          log.error(String.format(
              "Failed to auto-abandon inactive open change %d.",
                  cd.getId().get()), e);
        }
      }
      log.info(String.format("Auto-Abandoned %d of %d changes.",
          count, changesToAbandon.size()));
    } catch (QueryParseException | OrmException e) {
      log.error("Failed to query inactive open changes for auto-abandoning.", e);
    }
  }

  private ChangeControl changeControl(ChangeData cd) throws OrmException {
    return cd.changeControl(internalUserFactory.create());
  }
}
