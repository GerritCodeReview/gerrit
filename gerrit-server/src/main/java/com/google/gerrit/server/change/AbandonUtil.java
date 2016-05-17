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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class AbandonUtil {
  private static final Logger log = LoggerFactory.getLogger(AbandonUtil.class);

  private final ChangeCleanupConfig cfg;
  private final InternalUser.Factory internalUserFactory;
  private final QueryProcessor queryProcessor;
  private final ChangeQueryBuilder queryBuilder;
  private final Abandon abandon;

  @Inject
  AbandonUtil(
      ChangeCleanupConfig cfg,
      InternalUser.Factory internalUserFactory,
      QueryProcessor queryProcessor,
      ChangeQueryBuilder queryBuilder,
      Abandon abandon) {
    this.cfg = cfg;
    this.internalUserFactory = internalUserFactory;
    this.queryProcessor = queryProcessor;
    this.queryBuilder = queryBuilder;
    this.abandon = abandon;
  }

  public void abandonInactiveChanges() {
    if (cfg.getAbandonAfter() <= 0) {
      return;
    }

    long abandonAfterMinutes =
        TimeUnit.MILLISECONDS.toMinutes(cfg.getAbandonAfter());
    String openQuery = "status:new age:" + abandonAfterMinutes + "m";
    String draftQuery = "status:draft age:" + abandonAfterMinutes + "m";


    if (!cfg.getAbandonIfMergeable()) {
      openQuery += " -is:mergeable";
      draftQuery += " -is:mergeable";
    }

    List<ChangeData> openChangesToAbandon = new ArrayList<>();
    List<ChangeData> draftsToAbandon = new ArrayList<>();
    try {
      openChangesToAbandon = queryProcessor.enforceVisibility(false)
          .queryChanges(queryBuilder.parse(openQuery)).changes();

      if (cfg.getAbandonDrafts()) {
        draftsToAbandon = queryProcessor.enforceVisibility(false)
            .queryChanges(queryBuilder.parse(draftQuery)).changes();
      }
    } catch (QueryParseException | OrmException e) {
      log.error("Failed to query inactive changes for auto-abandoning.", e);
      return;
    }

    int openAbandonedCount = 0;
    for (ChangeData cd : openChangesToAbandon) {
      if (abandon(cd)) {
        openAbandonedCount++;
      }
    }
    log.info(String.format("Auto-Abandoned %d of %d open changes.",
        openAbandonedCount, openChangesToAbandon.size()));

    int draftsAbandonedCount = 0;
    for (ChangeData cd : draftsToAbandon) {
      try {
      cd.db().patchSets().atomicUpdate(cd.change().currentPatchSetId(),
          new AtomicUpdate<PatchSet>() {
          @Override
          public PatchSet update(PatchSet patchset) {
            patchset.setDraft(false);
            return patchset;
          }
        });
      cd.db().changes()
        .atomicUpdate(cd.change().getId(), new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            change.setStatus(Change.Status.NEW);
            return change;
          }
        });
      } catch (OrmException e) {
        log.error("Failed to undraft change when preparing to abandon.", e);
        continue;
      }
      if (abandon(cd)) {
        draftsAbandonedCount++;
      }
    }
    log.info(String.format("Auto-Abandoned %d of %d draft changes.",
        draftsAbandonedCount, draftsToAbandon.size()));
  }

  private boolean abandon(ChangeData cd) {
    try {
      abandon.abandon(changeControl(cd), cfg.getAbandonMessage());
    } catch (ResourceConflictException e) {
      // Change was already merged or abandoned.
      return false;
    } catch (Throwable e) {
      log.error(String.format("Failed to auto-abandon inactive change %d.",
          cd.getId().get()), e);
      return false;
    }
    return true;
  }

  private ChangeControl changeControl(ChangeData cd) throws OrmException {
    return cd.changeControl(internalUserFactory.create());
  }
}
