// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.InvalidNameException;
import com.google.gerrit.client.rpc.InvalidRevisionException;
import com.google.gerrit.git.GitRepositoryManager;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.ObjectWalk;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class AddBranch extends Handler<List<Branch>> {
  private static final Logger log = LoggerFactory.getLogger(AddBranch.class);

  interface Factory {
    AddBranch create(@Assisted Project.NameKey projectName,
        @Assisted("branchName") String branchName,
        @Assisted("startingRevision") String startingRevision);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ListBranches.Factory listBranchesFactory;
  private final IdentifiedUser identifiedUser;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replication;
  private final ReviewDb db;

  private final Project.NameKey projectName;
  private final String branchName;
  private final String startingRevision;

  @Inject
  AddBranch(final ProjectControl.Factory projectControlFactory,
      final ListBranches.Factory listBranchesFactory,
      final IdentifiedUser identifiedUser, final GitRepositoryManager repoManager,
      final ReplicationQueue replication, final ReviewDb db,

      @Assisted Project.NameKey projectName,
      @Assisted("branchName") String branchName,
      @Assisted("startingRevision") String startingRevision) {
    this.projectControlFactory = projectControlFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.replication = replication;
    this.db = db;

    this.projectName = projectName;
    this.branchName = branchName;
    this.startingRevision = startingRevision;
  }

  @Override
  public List<Branch> call() throws NoSuchProjectException, OrmException,
      InvalidNameException, InvalidRevisionException, IOException {
    final ProjectControl projectControl =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);

    String refname = branchName;
    while (refname.startsWith("/")) {
      refname = refname.substring(1);
    }
    if (!refname.startsWith(Constants.R_REFS)) {
      refname = Constants.R_HEADS + refname;
    }
    if (!Repository.isValidRefName(refname)) {
      throw new InvalidNameException();
    }
    if (!projectControl.canCreateRef(refname)) {
      throw new IllegalStateException("Cannot create " + refname);
    }

    final Branch.NameKey name = new Branch.NameKey(projectName, refname);
    final Repository repo = repoManager.openRepository(projectName.get());
    try {
      final ObjectId revid = parseStartingRevision(repo);
      final RevWalk rw = verifyConnected(repo, revid);

      try {
        final RefUpdate u = repo.updateRef(refname);
        u.setExpectedOldObjectId(ObjectId.zeroId());
        u.setNewObjectId(revid);
        u.setRefLogIdent(identifiedUser.newPersonIdent());
        u.setRefLogMessage("created via web from " + startingRevision, false);
        final RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            replication.scheduleUpdate(name.getParentKey(), refname);
            break;
          default: {
            final String msg =
                "Cannot create branch " + name + ": " + result.name();
            log.error(msg);
            throw new IOException(result.name());
          }
        }
      } catch (IOException err) {
        log.error("Cannot create branch " + name, err);
        throw err;
      }
    } finally {
      repo.close();
    }

    final Branch newBranch = new Branch(name);
    db.branches().insert(Collections.singleton(newBranch));

    return listBranchesFactory.create(projectName).call();
  }

  private ObjectId parseStartingRevision(final Repository repo)
      throws InvalidRevisionException {
    try {
      final ObjectId revid = repo.resolve(startingRevision);
      if (revid == null) {
        throw new InvalidRevisionException();
      }
      return revid;
    } catch (IOException err) {
      log.error("Cannot resolve \"" + startingRevision + "\" in project \""
          + projectName + "\"", err);
      throw new InvalidRevisionException();
    }
  }

  private RevWalk verifyConnected(final Repository repo, final ObjectId revid)
      throws InvalidRevisionException {
    try {
      final ObjectWalk rw = new ObjectWalk(repo);
      try {
        rw.markStart(rw.parseCommit(revid));
      } catch (IncorrectObjectTypeException err) {
        throw new InvalidRevisionException();
      }
      for (final Ref r : repo.getAllRefs().values()) {
        try {
          rw.markUninteresting(rw.parseAny(r.getObjectId()));
        } catch (MissingObjectException err) {
          continue;
        }
      }
      rw.checkConnectivity();
      return rw;
    } catch (IncorrectObjectTypeException err) {
      throw new InvalidRevisionException();
    } catch (MissingObjectException err) {
      throw new InvalidRevisionException();
    } catch (IOException err) {
      log.error("Repository \"" + repo.getDirectory()
          + "\" may be corrupt; suggest running git fsck", err);
      throw new InvalidRevisionException();
    }
  }
}
