// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;

import java.util.Set;

/** Support for services which require a {@link ReviewDb} instance. */
public class BaseServiceImplementation {
  /**
   * Executes <code>action.run</code> with an active ReviewDb connection.
   * <p>
   * A database handle is automatically opened and closed around the action's
   * {@link Action#run(ReviewDb)} method. OrmExceptions are caught and passed
   * into the onFailure method of the callback.
   * 
   * @param <T> type of result the callback expects.
   * @param callback the callback that will receive the result.
   * @param action the action logic to perform.
   */
  protected <T> void run(final AsyncCallback<T> callback, final Action<T> action) {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      final T r;
      try {
        r = action.run(db);
      } finally {
        db.close();
      }
      if (r != null) {
        callback.onSuccess(r);
      }
    } catch (OrmException e) {
      if (e.getCause() instanceof Failure) {
        callback.onFailure(e.getCause().getCause());

      } else if (e.getCause() instanceof CorruptEntityException) {
        callback.onFailure(e.getCause());

      } else if (e.getCause() instanceof NoSuchEntityException) {
        callback.onFailure(e.getCause());

      } else if (e.getCause() instanceof NoDifferencesException) {
        callback.onFailure(e.getCause());

      } else {
        callback.onFailure(e);
      }
    } catch (Failure e) {
      callback.onFailure(e.getCause());
    }
  }

  /** Throws NoSuchEntityException if the caller cannot access the project. */
  public static void assertCanRead(final Change change) throws Failure {
    if (!canRead(change)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  /** Throws NoSuchEntityException if the caller cannot access the project. */
  public static void assertCanRead(final Project.NameKey projectKey)
      throws Failure {
    if (!canRead(projectKey)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  /** Return true if the current user can read this change's project. */
  public static boolean canRead(final Change change) {
    return change != null && canRead(change.getDest().getParentKey());
  }

  /** Return true if the current user can read this project, and its contents. */
  public static boolean canRead(final Project.NameKey projectKey) {
    return canRead(Common.getAccountId(), projectKey);
  }

  public static boolean canRead(final Account.Id who,
      final Project.NameKey projectKey) {
    final ProjectCache.Entry e = Common.getProjectCache().get(projectKey);
    if (e == null) {
      // Unexpected, a project disappearing. But claim its not available.
      //
      return false;
    }

    final Set<AccountGroup.Id> myGroups = Common.getGroupCache().getGroups(who);
    if (myGroups.contains(e.getProject().getOwnerGroupId())) {
      // Ownership implies full access.
      //
      return true;
    }

    int val = Integer.MIN_VALUE;
    for (final ProjectRight pr : e.getRights()) {
      if (ApprovalCategory.READ.equals(pr.getApprovalCategoryId())
          && myGroups.contains(pr.getAccountGroupId())) {
        if (val < 0 && pr.getMaxValue() > 0) {
          // If one of the user's groups had denied them access, but
          // this group grants them access, prefer the grant over
          // the denial. We have to break the tie somehow and we
          // prefer being "more open" to being "more closed".
          //
          val = pr.getMaxValue();
        } else {
          // Otherwise we use the largest value we can get.
          //
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }
    if (val == Integer.MIN_VALUE) {
      for (final ProjectRight pr : Common.getProjectCache().getWildcardRights()) {
        if (ApprovalCategory.READ.equals(pr.getApprovalCategoryId())
            && myGroups.contains(pr.getAccountGroupId())) {
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }

    return val > 0;
  }

  /** Exception whose cause is passed into onFailure. */
  public static class Failure extends Exception {
    public Failure(final Throwable why) {
      super(why);
    }
  }

  /** Arbitrary action to run with a database connection. */
  public static interface Action<T> {
    /**
     * Perform this action, returning the onSuccess value.
     * 
     * @param db an open database handle to be used by this connection.
     * @return he value to pass to {@link AsyncCallback#onSuccess(Object)}.
     * @throws OrmException any schema based action failed.
     * @throws Failure cause is given to
     *         {@link AsyncCallback#onFailure(Throwable)}.
     */
    T run(ReviewDb db) throws OrmException, Failure;
  }
}
