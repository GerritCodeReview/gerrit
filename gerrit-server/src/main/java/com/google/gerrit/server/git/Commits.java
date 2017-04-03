// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** RevCommit parser helper. */
public class Commits {
  /**
   * Locate a reference to a patchSet's commit and immediately parse its content.
   *
   * <p>This method only returns successfully if the commit object exists, is verified to be a
   * commit, and was parsed without error.
   *
   * @param repo git Repository to parse the commit object from.
   * @param patchSet Gerrit patchSet of the commit object.
   * @return reference to the commit object. Never null.
   * @throws RepositoryNotFoundException A Project with the supplied name does not exist.
   * @throws MissingObjectException the supplied commit does not exist.
   * @throws IncorrectObjectTypeException the supplied id is not a commit or an annotated tag.
   * @throws IOException a pack file or loose object could not be read.
   */
  public static RevCommit parse(Repository repo, PatchSet patchSet)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    try (RevWalk walk = new RevWalk(repo)) {
      return parse(walk, patchSet);
    }
  }

  /**
   * Locate a reference to a patchSet's commit and immediately parse its content.
   *
   * <p>This method only returns successfully if the commit object exists, is verified to be a
   * commit, and was parsed without error.
   *
   * @param walk RevWalk to parse the commit object from.
   * @param patchSet Gerrit patchSet of the commit object.
   * @return reference to the commit object. Never null.
   * @throws RepositoryNotFoundException A Project with the supplied name does not exist.
   * @throws MissingObjectException the supplied commit does not exist.
   * @throws IncorrectObjectTypeException the supplied id is not a commit or an annotated tag.
   * @throws IOException a pack file or loose object could not be read.
   */
  public static RevCommit parse(RevWalk walk, PatchSet patchSet)
      throws IncorrectObjectTypeException, IOException, MissingObjectException,
          RepositoryNotFoundException {
    return walk.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
  }

  private final GitRepositoryManager repos;

  @Inject
  public Commits(GitRepositoryManager repos) {
    this.repos = repos;
  }

  /**
   * Locate a reference to a patchSet's commit and immediately parse its content.
   *
   * <p>This method only returns successfully if the commit object exists, is verified to be a
   * commit, and was parsed without error.
   *
   * @param project Gerrit project to parse the commit object from.
   * @param patchSet Gerrit patchSet of the commit object.
   * @return reference to the commit object. Never null.
   * @throws RepositoryNotFoundException A Project with the supplied name does not exist.
   * @throws MissingObjectException the supplied commit does not exist.
   * @throws IncorrectObjectTypeException the supplied id is not a commit or an annotated tag.
   * @throws IOException a pack file or loose object could not be read.
   */
  public RevCommit parse(Project.NameKey project, PatchSet patchSet)
      throws IncorrectObjectTypeException, IOException, MissingObjectException,
          RepositoryNotFoundException {
    try (Repository repo = repos.openRepository(project)) {
      return parse(repo, patchSet);
    }
  }

  /**
   * Locate a reference to a commit from a refname immediately parse its content.
   *
   * <p>This method only returns successfully if the commit object exists, is verified to be a
   * commit, and was parsed without error.
   *
   * @param project Gerrit project to parse the commit object from.
   * @param refName name of the ref pointing to a commit object.
   * @return reference to the commit object. Never null.
   * @throws RepositoryNotFoundException A Project with the supplied name does not exist.
   * @throws MissingObjectException the supplied commit does not exist.
   * @throws IncorrectObjectTypeException the supplied id is not a commit or an annotated tag.
   * @throws IOException a pack file or loose object could not be read.
   */
  public RevCommit parseFromExactRef(Project.NameKey project, String refName)
      throws IllegalArgumentException, IncorrectObjectTypeException, IOException,
          MissingObjectException, RefNotFoundException, RepositoryNotFoundException {
    try (Repository repo = repos.openRepository(project)) {
      Ref ref = repo.exactRef(refName);
      if (ref == null) {
        throw new RefNotFoundException(refName);
      }
      return parse(repo, ref.getObjectId());
    }
  }

  /**
   * Locate a reference to a commit and immediately parse its content.
   *
   * <p>This method only returns successfully if the commit object exists, is verified to be a
   * commit, and was parsed without error.
   *
   * @param project Gerrit project to parse the commit object from.
   * @param id name of the commit object.
   * @return reference to the commit object. Never null.
   * @throws RepositoryNotFoundException A Project with the supplied name does not exist.
   * @throws MissingObjectException the supplied commit does not exist.
   * @throws IncorrectObjectTypeException the supplied id is not a commit or an annotated tag.
   * @throws IOException a pack file or loose object could not be read.
   */
  public RevCommit parse(Project.NameKey project, ObjectId commit)
      throws IncorrectObjectTypeException, IOException, MissingObjectException,
          RepositoryNotFoundException {
    if (commit instanceof RevCommit) {
      return (RevCommit) commit;
    }
    try (Repository repo = repos.openRepository(project)) {
      return parse(repo, commit);
    }
  }

  /* It would be nice to get this in jgit as Repository.parseCommit() */
  private static RevCommit parse(Repository repo, ObjectId commit)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    try (RevWalk walk = new RevWalk(repo)) {
      return walk.parseCommit(commit);
    }
  }
}
