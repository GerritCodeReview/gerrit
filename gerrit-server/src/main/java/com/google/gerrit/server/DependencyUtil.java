// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DependencyUtil {
  private interface ChangeControlFactory {
    ChangeControl get(Change change) throws NoSuchChangeException;
  }

  private static class FullChangeControlFactory implements ChangeControlFactory {
    private ChangeControl.Factory factory;

    public FullChangeControlFactory(ChangeControl.Factory factory) {
      this.factory = factory;
    }

    @Override
    public ChangeControl get(Change change) throws NoSuchChangeException {
      return factory.controlFor(change);
    }
  }

  private static class GenericChangeControlFactory implements
      ChangeControlFactory {
    private ChangeControl.GenericFactory genericFactory;
    private CurrentUser currentUser;

    public GenericChangeControlFactory(
        ChangeControl.GenericFactory genericFactory, CurrentUser currentUser) {
      this.genericFactory = genericFactory;
      this.currentUser = currentUser;
    }

    @Override
    public ChangeControl get(Change change) throws NoSuchChangeException {
      return genericFactory.controlFor(change, currentUser);
    }
  }

  /**
   * Returns dependency errors in the given change or its ancestors
   *
   * @param db to use
   * @param change to query
   * @param generic control factory
   * @param currentUser
   * @param processed the list of already processed changes
   * @return set of dependency errors
   * @throws OrmException
   * @throws NoSuchChangeException
   */
  public static Set<Change.DependencyError> findDependencyErrors(
      final ReviewDb db, Change change,
      ChangeControl.GenericFactory controlFactory, CurrentUser currentUser,
      Map<Change.Id, Set<Change.DependencyError>> processed) throws OrmException, NoSuchChangeException {
    return findDependencyErrors(db, change, new GenericChangeControlFactory(
        controlFactory, currentUser), processed);
  }

  /**
   * Returns dependency errors in the given change or its ancestors
   *
   * @param db to use
   * @param change to query
   * @param controlFactory
   * @return set of dependency errors
   * @throws OrmException
   * @throws NoSuchChangeException
   */
  public static Set<Change.DependencyError> findDependencyErrors(
      final ReviewDb db, Change change, ChangeControl.Factory controlFactory,
      Map<Change.Id, Set<Change.DependencyError>> processed) throws OrmException, NoSuchChangeException {
    return findDependencyErrors(db, change, new FullChangeControlFactory(
        controlFactory), processed);
  }

  private static Set<Change.DependencyError> findDependencyErrors(
      final ReviewDb db, Change change, ChangeControlFactory controlFactory,
      Map<Change.Id, Set<Change.DependencyError>> processed) throws OrmException, NoSuchChangeException {
    if (processed == null) {
      processed = new HashMap<Change.Id, Set<Change.DependencyError>>();
    }

    Set<Change.DependencyError> result = new HashSet<Change.DependencyError>();

    // If the change has already been processed skip it
    if (processed.containsKey(change.getId())) {
      return processed.get(change.getId());
    }

    ChangeData data = new ChangeData(change);
    boolean allAncestorsVisible =
        data.loadAncestorData(new Provider<ReviewDb>() {
          @Override
          public ReviewDb get() {
            return db;
          }
        }, controlFactory.get(change));
    boolean allParentsMerged = findErrors(data, result);

    if (!allParentsMerged) {
      if (!allAncestorsVisible) {
        result.add(Change.DependencyError.ANCESTOR_NOT_VISIBLE);
      }
      for (ChangeData parentData : data.getDependsOn()) {
        // If we have already found all possible errors break
        if (result.size() == Change.DependencyError.values().length) {
          return result;
        }

        Change parent = parentData.getChange();
        result.addAll(findDependencyErrors(db, parent, controlFactory, processed));
      }
    }

    processed.put(change.getId(), result);

    return result;
  }

  private static boolean findErrors(ChangeData data,
      Set<Change.DependencyError> result) {
    Change change = data.getChange();
    boolean allParentsMerged = true;

    for (ChangeData parentData : data.getDependsOn()) {
      Change parent = parentData.getChange();

      if (parent.getStatus() == Status.MERGED) {
        continue;
      }

      allParentsMerged = false;

      if (!parent.getDest().getShortName()
          .equals(change.getDest().getShortName())) {
        result.add(Change.DependencyError.ANCESTOR_NOT_CUR_BRANCH);
      }
      if (!parent.currPatchSetId().equals(
          data.getAncestorPatchId(parent.getId()))) {
        result.add(Change.DependencyError.ANCESTOR_OUTDATED);
      }
      if (parent.getStatus() == Change.Status.DRAFT) {
        result.add(Change.DependencyError.ANCESTOR_DRAFT);
      }
      if (parent.getStatus() == Change.Status.ABANDONED) {
        result.add(Change.DependencyError.ANCESTOR_ABANDONED);
      }
    }

    return allParentsMerged;
  }
}
