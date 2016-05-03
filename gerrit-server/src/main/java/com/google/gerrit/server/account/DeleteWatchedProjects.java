// Copyright (C) 2016 The Android Open Source Project
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
package com.google.gerrit.server.account;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@Singleton
public class DeleteWatchedProjects
    implements RestModifyView<AccountResource, List<String>> {

  private final Provider<ReviewDb> dbProvider;
  private final Provider<IdentifiedUser> self;

  @Inject
  DeleteWatchedProjects(Provider<ReviewDb> dbProvider,
      Provider<IdentifiedUser> self) {
    this.dbProvider = dbProvider;
    this.self = self;
  }

  @Override
  public Response<?> apply(
      AccountResource rsrc, List<String> input)
      throws UnprocessableEntityException, OrmException, AuthException {
    if (self.get() != rsrc.getUser()) {
      throw new AuthException("It is not allowed to edit project watches "
          + "of other users");
    }
    ResultSet<AccountProjectWatch> watchedProjects =
        dbProvider.get().accountProjectWatches()
            .byAccount(rsrc.getUser().getAccountId());
    HashMap<String, AccountProjectWatch> watchedProjectsMap = new HashMap<>();
    for (AccountProjectWatch watchedProject : watchedProjects) {
      watchedProjectsMap
          .put(watchedProject.getProjectNameKey().get(), watchedProject);
    }

    if (input != null) {
      List<AccountProjectWatch.Key> keysToDelete = new LinkedList<>();
      for (String projectKeyToDelete : input) {
        if (!watchedProjectsMap.containsKey(projectKeyToDelete))
          throw new UnprocessableEntityException(projectKeyToDelete
              + " is not currently watched by this user.");
        keysToDelete.add(watchedProjectsMap.get(projectKeyToDelete).getKey());
      }
      dbProvider.get().accountProjectWatches().deleteKeys(keysToDelete);
    }

    return Response.none();
  }
}
