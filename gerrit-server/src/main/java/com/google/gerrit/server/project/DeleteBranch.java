// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.DeleteBranch.Input;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class DeleteBranch implements RestModifyView<BranchResource, Input>{
  private static final Logger log = LoggerFactory.getLogger(DeleteBranch.class);
  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final long SLEEP_ON_LOCK_FAILURE_MS = 15;

  public static class Input {
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitReferenceUpdated referenceUpdated;
  private final ChangeHooks hooks;

  @Inject
  DeleteBranch(Provider<IdentifiedUser> identifiedUser,
      GitRepositoryManager repoManager, Provider<ReviewDb> dbProvider,
      Provider<InternalChangeQuery> queryProvider,
      GitReferenceUpdated referenceUpdated, ChangeHooks hooks) {
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.dbProvider = dbProvider;
    this.queryProvider = queryProvider;
    this.referenceUpdated = referenceUpdated;
    this.hooks = hooks;
  }

  @Override
  public Response<?> apply(BranchResource rsrc, Input input) throws AuthException,
      ResourceConflictException, OrmException, IOException {
    if (!rsrc.getControl().controlForRef(rsrc.getBranchKey()).canDelete()) {
      throw new AuthException("Cannot delete branch");
    }
    if (!queryProvider.get().setLimit(1)
        .byBranchOpen(rsrc.getBranchKey()).isEmpty()) {
      throw new ResourceConflictException("branch " + rsrc.getBranchKey()
          + " has open changes");
    }

    Repository r = repoManager.openRepository(rsrc.getNameKey());
    try {
      RefUpdate.Result result;
      RefUpdate u = r.updateRef(rsrc.getRef());
      u.setForceUpdate(true);
      int remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;
      for (;;) {
        try {
          result = u.delete();
        } catch (LockFailedException e) {
          result = RefUpdate.Result.LOCK_FAILURE;
        } catch (IOException e) {
          log.error("Cannot delete " + rsrc.getBranchKey(), e);
          throw e;
        }
        if (result == RefUpdate.Result.LOCK_FAILURE
            && --remainingLockFailureCalls > 0) {
          try {
            Thread.sleep(SLEEP_ON_LOCK_FAILURE_MS);
          } catch (InterruptedException ie) {
            // ignore
          }
        } else {
          break;
        }
      }

      switch (result) {
        case NEW:
        case NO_CHANGE:
        case FAST_FORWARD:
        case FORCED:
          referenceUpdated.fire(rsrc.getNameKey(), u, ReceiveCommand.Type.DELETE);
          hooks.doRefUpdatedHook(rsrc.getBranchKey(), u, identifiedUser.get().getAccount());
          ResultSet<SubmoduleSubscription> submoduleSubscriptions =
            dbProvider.get().submoduleSubscriptions().bySuperProject(rsrc.getBranchKey());
          dbProvider.get().submoduleSubscriptions().delete(submoduleSubscriptions);
          break;

        case REJECTED_CURRENT_BRANCH:
          log.error("Cannot delete " + rsrc.getBranchKey() + ": " + result.name());
          throw new ResourceConflictException("cannot delete current branch");

        default:
          log.error("Cannot delete " + rsrc.getBranchKey() + ": " + result.name());
          throw new ResourceConflictException("cannot delete branch: " + result.name());
      }
    } finally {
      r.close();
    }
    return Response.none();
  }
}
