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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class AddBranch extends Handler<ListBranchesResult> {
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
  private final ChangeHookRunner hooks;

  private final Project.NameKey projectName;
  private final String branchName;
  private final String startingRevision;

  @Inject
  AddBranch(final ProjectControl.Factory projectControlFactory,
      final ListBranches.Factory listBranchesFactory,
      final IdentifiedUser identifiedUser,
      final GitRepositoryManager repoManager,
      final ReplicationQueue replication,
      final ChangeHookRunner hooks,

      @Assisted Project.NameKey projectName,
      @Assisted("branchName") String branchName,
      @Assisted("startingRevision") String startingRevision) {
    this.projectControlFactory = projectControlFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.replication = replication;
    this.hooks = hooks;

    this.projectName = projectName;
    this.branchName = branchName;
    this.startingRevision = startingRevision;
  }

  @Override
  public ListBranchesResult call() throws NoSuchProjectException,
      InvalidNameException, InvalidRevisionException, IOException,
      BranchCreationNotAllowedException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

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
    if (refname.startsWith(MagicBranch.NEW_CHANGE)) {
      throw new BranchCreationNotAllowedException(MagicBranch.NEW_CHANGE);
    }

    final Branch.NameKey name = new Branch.NameKey(projectName, refname);
    final RefControl refControl = projectControl.controlForRef(name);
    final Repository repo = repoManager.openRepository(projectName);
    try {
      final ObjectId revid = parseStartingRevision(repo);
      final RevWalk rw = verifyConnected(repo, revid);
      RevObject object = rw.parseAny(revid);

      if (refname.startsWith(Constants.R_HEADS)) {
        // Ensure that what we start the branch from is a commit. If we
        // were given a tag, deference to the commit instead.
        //
        try {
          object = rw.parseCommit(object);
        } catch (IncorrectObjectTypeException notCommit) {
          throw new IllegalStateException(startingRevision + " not a commit");
        }
      }

      if (!refControl.canCreate(rw, object)) {
        throw new IllegalStateException("Cannot create " + refname);
      }

      try {
        final RefUpdate u = repo.updateRef(refname);
        u.setExpectedOldObjectId(ObjectId.zeroId());
        u.setNewObjectId(object.copy());
        u.setRefLogIdent(identifiedUser.newRefLogIdent());
        u.setRefLogMessage("created via web from " + startingRevision, false);
        final RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            replication.scheduleUpdate(name.getParentKey(), refname);
            hooks.doRefUpdatedHook(name, u, identifiedUser.getAccount());
            break;
          default: {
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
