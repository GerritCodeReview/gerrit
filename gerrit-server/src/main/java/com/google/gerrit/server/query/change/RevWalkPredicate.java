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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.RepoWalk;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.RepoWalksCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Predicate which creates Repository, RevWalk objects and properly
 * closes them. Git based operators should extend this predicate.
 *
 */
public abstract class RevWalkPredicate extends OperatorPredicate<ChangeData> {
  public static class Arguments {
    public final PatchSet patchSet;
    public final RevId revision;
    public final AnyObjectId objectId;
    public final Change change;
    public final Project.NameKey projectName;

    public Arguments(PatchSet patchSet,
        RevId revision,
        AnyObjectId objectId,
        Change change,
        Project.NameKey projectName) {
      this.patchSet = patchSet;
      this.revision = revision;
      this.objectId = objectId;
      this.change = change;
      this.projectName = projectName;
    }
  }

  public final Provider<ReviewDb> db;
  public final RepoWalksCache repoWalksCache;

  public RevWalkPredicate(Provider<ReviewDb> db, RepoWalksCache repoWalksCache,
      String operator, String ref) {
    super(operator, ref);
    this.db = db;
    this.repoWalksCache = repoWalksCache;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    final PatchSet patchSet = object.currentPatchSet(db);
    if (patchSet == null) {
      return false;
    }

    final RevId revision = patchSet.getRevision();
    if (revision == null) {
      return false;
    }

    final AnyObjectId objectId = ObjectId.fromString(revision.get());
    if (objectId == null) {
      return false;
    }

    Change change = object.change(db);
    if (change == null) {
      return false;
    }

    final Project.NameKey projectName = change.getProject();
    if (projectName == null) {
      return false;
    }

    Arguments args = new Arguments(patchSet, revision, objectId, change, projectName);

    RepoWalk repoWalk = repoWalksCache.get(projectName);
    return match(repoWalk.repository, repoWalk.revWalk, args);
  }

  public abstract boolean match(Repository repo, RevWalk rw, Arguments args);
}
