// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.config.AttentionSetConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class ReaddOwnerUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AttentionSetConfig cfg;
  private final Provider<ChangeQueryProcessor> queryProvider;
  private final ChangeQueryBuilder queryBuilder;
  private final AddToAttentionSetOp.Factory opFactory;
  private final ServiceUserClassifier serviceUserClassifier;
  private final InternalUser internalUser;

  @Inject
  ReaddOwnerUtil(
      AttentionSetConfig cfg,
      Provider<ChangeQueryProcessor> queryProvider,
      ChangeQueryBuilder queryBuilder,
      AddToAttentionSetOp.Factory opFactory,
      ServiceUserClassifier serviceUserClassifier,
      InternalUser.Factory internalUserFactory) {
    this.cfg = cfg;
    this.queryProvider = queryProvider;
    this.queryBuilder = queryBuilder;
    this.opFactory = opFactory;
    this.serviceUserClassifier = serviceUserClassifier;
    internalUser = internalUserFactory.create();
  }

  public void readdOwnerForInactiveOpenChanges(BatchUpdate.Factory updateFactory) {
    if (cfg.getReaddAfter() <= 0) {
      logger.atWarning().log("readdOwnerAfter needs to be set to a positive value");
      return;
    }

    try {
      String query =
          "status:new -is:wip -is:private age:"
              + TimeUnit.MILLISECONDS.toMinutes(cfg.getReaddAfter())
              + "m";

      List<ChangeData> changesToAddOwner =
          queryProvider.get().enforceVisibility(false).query(queryBuilder.parse(query)).entities();

      ImmutableListMultimap.Builder<Project.NameKey, ChangeData> builder =
          ImmutableListMultimap.builder();
      for (ChangeData cd : changesToAddOwner) {
        builder.put(cd.project(), cd);
      }

      ListMultimap<Project.NameKey, ChangeData> ownerAdds = builder.build();
      int ownersAdded = 0;
      for (Project.NameKey project : ownerAdds.keySet()) {
        try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
          try (BatchUpdate bu = updateFactory.create(project, internalUser, TimeUtil.now())) {
            for (ChangeData changeData : ownerAdds.get(project)) {
              Account.Id ownerId = changeData.change().getOwner();
              if (!inAttentionSet(changeData, ownerId)
                  && !serviceUserClassifier.isServiceUser(ownerId)) {
                logger.atFine().log(
                    "Batch owner for add to AS of change %s in project %s",
                    changeData.getId(), project.get());
                bu.addOp(
                    changeData.getId(), opFactory.create(ownerId, cfg.getReaddMessage(), true));
                ownersAdded++;
              }
            }
            bu.execute();
          } catch (RestApiException | UpdateException e) {
            logger.atSevere().withCause(e).log(
                "Failed to readd owners for changes in project %s", project.get());
          }
        }
      }
      logger.atInfo().log("Auto-Added %d owners to changes", ownersAdded);
    } catch (QueryParseException | StorageException e) {
      logger.atSevere().withCause(e).log(
          "Failed to query inactive open changes for readding owners.");
    }
  }

  private static boolean inAttentionSet(ChangeData changeData, Account.Id accountId) {
    return AttentionSetUtil.additionsOnly(changeData.attentionSet()).stream()
        .anyMatch(u -> u.account().equals(accountId));
  }
}
