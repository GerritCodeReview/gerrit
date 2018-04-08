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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.config.ChangeCleanupConfig;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AbandonUtil {
  private static final Logger log = LoggerFactory.getLogger(AbandonUtil.class);

  private final ChangeCleanupConfig cfg;
  private final Provider<ChangeQueryProcessor> queryProvider;
  private final ChangeQueryBuilder queryBuilder;
  private final BatchAbandon batchAbandon;
  private final InternalUser internalUser;

  @Inject
  AbandonUtil(
      ChangeCleanupConfig cfg,
      InternalUser.Factory internalUserFactory,
      Provider<ChangeQueryProcessor> queryProvider,
      ChangeQueryBuilder queryBuilder,
      BatchAbandon batchAbandon) {
    this.cfg = cfg;
    this.queryProvider = queryProvider;
    this.queryBuilder = queryBuilder;
    this.batchAbandon = batchAbandon;
    internalUser = internalUserFactory.create();
  }

  public void abandonInactiveOpenChanges(BatchUpdate.Factory updateFactory) {
    if (cfg.getAbandonAfter() <= 0) {
      return;
    }

    try {
      String query =
          "status:new age:" + TimeUnit.MILLISECONDS.toMinutes(cfg.getAbandonAfter()) + "m";
      if (!cfg.getAbandonIfMergeable()) {
        query += " -is:mergeable";
      }

      List<ChangeData> changesToAbandon =
          queryProvider.get().enforceVisibility(false).query(queryBuilder.parse(query)).entities();
      ImmutableListMultimap.Builder<Project.NameKey, ChangeData> builder =
          ImmutableListMultimap.builder();
      for (ChangeData cd : changesToAbandon) {
        builder.put(cd.project(), cd);
      }

      int count = 0;
      ListMultimap<Project.NameKey, ChangeData> abandons = builder.build();
      String message = cfg.getAbandonMessage();
      for (Project.NameKey project : abandons.keySet()) {
        Collection<ChangeData> changes = getValidChanges(abandons.get(project), query);
        try {
          batchAbandon.batchAbandon(updateFactory, project, internalUser, changes, message);
          count += changes.size();
        } catch (Throwable e) {
          StringBuilder msg = new StringBuilder("Failed to auto-abandon inactive change(s):");
          for (ChangeData change : changes) {
            msg.append(" ").append(change.getId().get());
          }
          msg.append(".");
          log.error(msg.toString(), e);
        }
      }
      log.info(String.format("Auto-Abandoned %d of %d changes.", count, changesToAbandon.size()));
    } catch (QueryParseException | OrmException e) {
      log.error("Failed to query inactive open changes for auto-abandoning.", e);
    }
  }

  private Collection<ChangeData> getValidChanges(Collection<ChangeData> changes, String query)
      throws OrmException, QueryParseException {
    Collection<ChangeData> validChanges = new ArrayList<>();
    for (ChangeData cd : changes) {
      String newQuery = query + " change:" + cd.getId();
      List<ChangeData> changesToAbandon =
          queryProvider
              .get()
              .enforceVisibility(false)
              .query(queryBuilder.parse(newQuery))
              .entities();
      if (!changesToAbandon.isEmpty()) {
        validChanges.add(cd);
      } else {
        log.debug(
            "Change data with id \"{}\" does not satisfy the query \"{}\""
                + " any more, hence skipping it in clean up",
            cd.getId(),
            query);
      }
    }
    return validChanges;
  }
}
