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

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.CreateBranch.Input;
import com.google.gerrit.server.project.ListBranches.BranchInfo;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
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

public class CreateBranch implements RestModifyView<ProjectResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(CreateBranch.class);

  public static class Input {
    public String ref;

    @DefaultInput
    public String revision;
  }

  public static interface Factory {
    CreateBranch create(String ref);
  }

  private final IdentifiedUser identifiedUser;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final ChangeHooks hooks;
  private String ref;

  @Inject
  CreateBranch(IdentifiedUser identifiedUser, GitRepositoryManager repoManager,
      GitReferenceUpdated referenceUpdated, ChangeHooks hooks,
      @Assisted String ref) {
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.referenceUpdated = referenceUpdated;
    this.hooks = hooks;
    this.ref = ref;
  }

  @Override
  public BranchInfo apply(ProjectResource rsrc, Input input)
      throws BadRequestException, AuthException, ResourceConflictException,
      IOException {
    if (input == null) {
      input = new Input();
    }
    if (input.ref != null && !ref.equals(input.ref)) {
      throw new BadRequestException("ref must match URL");
    }
    if (input.revision == null) {
      input.revision = Constants.HEAD;
    }
    while (ref.startsWith("/")) {
      ref = ref.substring(1);
    }
    if (!ref.startsWith(Constants.R_REFS)) {
      ref = Constants.R_HEADS + ref;
    }
    if (!Repository.isValidRefName(ref)) {
      throw new BadRequestException("invalid branch name \"" + ref + "\"");
    }
    if (MagicBranch.isMagicBranch(ref)) {
      throw new BadRequestException("not allowed to create branches under \""
          + MagicBranch.getMagicRefNamePrefix(ref) + "\"");
    }

    final Branch.NameKey name = new Branch.NameKey(rsrc.getNameKey(), ref);
    final RefControl refControl = rsrc.getControl().controlForRef(name);
    final Repository repo = repoManager.openRepository(rsrc.getNameKey());
    try {
      final ObjectId revid = parseBaseRevision(repo, rsrc.getNameKey(), input.revision);
      final RevWalk rw = verifyConnected(repo, revid);
      RevObject object = rw.parseAny(revid);

      if (ref.startsWith(Constants.R_HEADS)) {
        // Ensure that what we start the branch from is a commit. If we
        // were given a tag, deference to the commit instead.
        //
        try {
          object = rw.parseCommit(object);
        } catch (IncorrectObjectTypeException notCommit) {
          throw new BadRequestException("\"" + input.revision + "\" not a commit");
        }
      }

      if (!refControl.canCreate(rw, object)) {
        throw new AuthException("Cannot create \"" + ref + "\"");
      }

      try {
        final RefUpdate u = repo.updateRef(ref);
        u.setExpectedOldObjectId(ObjectId.zeroId());
        u.setNewObjectId(object.copy());
        u.setRefLogIdent(identifiedUser.newRefLogIdent());
        u.setRefLogMessage("created via REST from " + input.revision, false);
        final RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            referenceUpdated.fire(name.getParentKey(), u);
            hooks.doRefUpdatedHook(name, u, identifiedUser.getAccount());
            break;
          case LOCK_FAILURE:
            if (repo.getRef(ref) != null) {
              throw new ResourceConflictException("branch \"" + ref
                  + "\" already exists");
            }
            String refPrefix = getRefPrefix(ref);
            while (!Constants.R_HEADS.equals(refPrefix)) {
              if (repo.getRef(refPrefix) != null) {
                throw new ResourceConflictException("Cannot create branch \""
                    + ref + "\" since it conflicts with branch \"" + refPrefix
                    + "\".");
              }
              refPrefix = getRefPrefix(refPrefix);
            }
          default: {
            throw new IOException(result.name());
          }
        }

        return new BranchInfo(ref, revid.getName(), refControl.canDelete());
      } catch (IOException err) {
        log.error("Cannot create branch \"" + name + "\"", err);
        throw err;
      }
    } catch (InvalidRevisionException e) {
      throw new BadRequestException("invalid revision \"" + input.revision + "\"");
    } finally {
      repo.close();
    }
  }

  private static String getRefPrefix(final String refName) {
    final int i = refName.lastIndexOf('/');
    if (i > Constants.R_HEADS.length() - 1) {
      return refName.substring(0, i);
    }
    return Constants.R_HEADS;
  }

  private ObjectId parseBaseRevision(Repository repo,
      Project.NameKey projectName, String baseRevision)
      throws InvalidRevisionException {
    try {
      final ObjectId revid = repo.resolve(baseRevision);
      if (revid == null) {
        throw new InvalidRevisionException();
      }
      return revid;
    } catch (IOException err) {
      log.error("Cannot resolve \"" + baseRevision + "\" in project \""
          + projectName.get() + "\"", err);
      throw new InvalidRevisionException();
    } catch (RevisionSyntaxException err) {
      log.error("Invalid revision syntax \"" + baseRevision + "\"", err);
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
      for (final Ref r : repo.getRefDatabase().getRefs(ALL).values()) {
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
