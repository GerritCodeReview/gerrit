// Copyright (C) 2011 The Android Open Source Project
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SubmoduleOp {
  public interface Factory {
    SubmoduleOp create(MergeOpRepoManager orm);
  }

  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);

  private final GitModules.Factory gitmodulesFactory;
  private final PersonIdent myIdent;
  private final GitReferenceUpdated gitRefUpdated;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final Account account;
  private final boolean verboseSuperProject;
  private final boolean enableSuperProjectSubscriptions;
  private final MergeOpRepoManager orm;

  @AssistedInject
  public SubmoduleOp(
      GitModules.Factory gitmodulesFactory,
      @GerritPersonIdent PersonIdent myIdent,
      @GerritServerConfig Config cfg,
      GitReferenceUpdated gitRefUpdated,
      ProjectCache projectCache,
      ProjectState.Factory projectStateFactory,
      @Nullable Account account,
      @Assisted MergeOpRepoManager orm) {
    this.gitmodulesFactory = gitmodulesFactory;
    this.myIdent = myIdent;
    this.gitRefUpdated = gitRefUpdated;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.account = account;
    this.verboseSuperProject = cfg.getBoolean("submodule",
        "verboseSuperprojectUpdate", true);
    this.enableSuperProjectSubscriptions = cfg.getBoolean("submodule",
        "enableSuperProjectSubscriptions", true);
    this.orm = orm;
  }

  public Collection<Branch.NameKey> getDestinationBranches(Branch.NameKey src,
      SubscribeSection s) throws IOException {
    Collection<Branch.NameKey> ret = new ArrayList<>();
    logDebug("Inspecting SubscribeSection " + s);
    for (RefSpec r : s.getRefSpecs()) {
      logDebug("Inspecting ref " + r);
      if (r.matchSource(src.get())) {
        if (r.getDestination() == null) {
          // no need to care for wildcard, as we matched already
          try {
            orm.openRepo(s.getProject(), false);
          } catch (NoSuchProjectException e) {
            // A project listed a non existent project to be allowed
            // to subscribe to it. Allow this for now.
            continue;
          }
          OpenRepo or = orm.getRepo(s.getProject());
          for (Ref ref : or.repo.getRefDatabase().getRefs(
              RefNames.REFS_HEADS).values()) {
            ret.add(new Branch.NameKey(s.getProject(), ref.getName()));
          }
        } else if (r.isWildcard()) {
          // refs/heads/*:refs/heads/*
          ret.add(new Branch.NameKey(s.getProject(),
              r.expandFromSource(src.get()).getDestination()));
        } else {
          // e.g. refs/heads/master:refs/heads/stable
          ret.add(new Branch.NameKey(s.getProject(), r.getDestination()));
        }
      }
    }
    logDebug("Returning possible branches: " + ret +
        "for project " + s.getProject());
    return ret;
  }

  public Collection<SubmoduleSubscription>
      superProjectSubscriptionsForSubmoduleBranch(Branch.NameKey srcBranch)
      throws IOException {
    logDebug("Calculating possible superprojects for " + srcBranch);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    Project.NameKey srcProject = srcBranch.getParentKey();
    ProjectConfig cfg = projectCache.get(srcProject).getConfig();
    for (SubscribeSection s : projectStateFactory.create(cfg)
        .getSubscribeSections(srcBranch)) {
      logDebug("Checking subscribe section " + s);
      Collection<Branch.NameKey> branches =
          getDestinationBranches(srcBranch, s);
      for (Branch.NameKey targetBranch : branches) {
        Project.NameKey targetProject = targetBranch.getParentKey();
        try {
          orm.openRepo(targetProject, false);
          OpenRepo or = orm.getRepo(targetProject);
          ObjectId id = or.repo.resolve(targetBranch.get());
          if (id == null) {
            logDebug("The branch " + targetBranch + " doesn't exist.");
            continue;
          }
        } catch (NoSuchProjectException e) {
          logDebug("The project " + targetProject + " doesn't exist");
          continue;
        }
        GitModules m = gitmodulesFactory.create(targetBranch, orm);
        for (SubmoduleSubscription ss : m.subscribedTo(srcBranch)) {
          logDebug("Checking SubmoduleSubscription " + ss);
          if (projectCache.get(ss.getSubmodule().getParentKey()) != null) {
            logDebug("Adding SubmoduleSubscription " + ss);
            ret.add(ss);
          }
        }
      }
    }
    logDebug("Calculated superprojects for " + srcBranch + " are " + ret);
    return ret;
  }

  protected void updateSuperProjects(Collection<Branch.NameKey> updatedBranches)
      throws SubmoduleException {
    if (!enableSuperProjectSubscriptions) {
      logDebug("Updating superprojects disabled");
      return;
    }
    logDebug("Updating superprojects");

    Multimap<Branch.NameKey, SubmoduleSubscription> targets =
        HashMultimap.create();

    for (Branch.NameKey updatedBranch : updatedBranches) {
      logDebug("Now processing " + updatedBranch);
      Set<Branch.NameKey> checkedTargets = new HashSet<>();
      Set<Branch.NameKey> targetsToProcess = new HashSet<>();
      targetsToProcess.add(updatedBranch);

      while (!targetsToProcess.isEmpty()) {
        Set<Branch.NameKey> newTargets = new HashSet<>();
        for (Branch.NameKey b : targetsToProcess) {
          try {
            Collection<SubmoduleSubscription> subs =
                superProjectSubscriptionsForSubmoduleBranch(b);
            for (SubmoduleSubscription sub : subs) {
              Branch.NameKey dst = sub.getSuperProject();
              targets.put(dst, sub);
              newTargets.add(dst);
            }
          } catch (IOException e) {
            throw new SubmoduleException("Cannot find superprojects for " + b, e);
          }
        }
        logDebug("adding to done " + targetsToProcess);
        checkedTargets.addAll(targetsToProcess);
        logDebug("completely done with " + checkedTargets);

        Set<Branch.NameKey> intersection = new HashSet<>(checkedTargets);
        intersection.retainAll(newTargets);
        if (!intersection.isEmpty()) {
          throw new SubmoduleException(
              "Possible circular subscription involving " + updatedBranch);
        }

        targetsToProcess = newTargets;
      }
    }

    for (Branch.NameKey dst : targets.keySet()) {
      try {
        updateGitlinks(dst, targets.get(dst));
      } catch (SubmoduleException e) {
        throw new SubmoduleException("Cannot update gitlinks for " + dst, e);
      }
    }
  }

  /**
   * Update the submodules in one branch of one repository.
   *
   * @param subscriber the branch of the repository which should be changed.
   * @param updates submodule updates which should be updated to.
   * @throws SubmoduleException
   */
  private void updateGitlinks(Branch.NameKey subscriber,
      Collection<SubmoduleSubscription> updates)
          throws SubmoduleException {
    PersonIdent author = null;
    StringBuilder msgbuf = new StringBuilder("Update git submodules\n\n");
    boolean sameAuthorForAll = true;

    try {
      orm.openRepo(subscriber.getParentKey(), false);
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access superproject", e);
    }
    OpenRepo or = orm.getRepo(subscriber.getParentKey());
    try {
      Ref r = or.repo.exactRef(subscriber.get());
      if (r == null) {
        throw new SubmoduleException(
            "The branch was probably deleted from the subscriber repository");
      }

      DirCache dc = readTree(r, or.rw);
      DirCacheEditor ed = dc.editor();

      for (SubmoduleSubscription s : updates) {
        try {
          orm.openRepo(s.getSubmodule().getParentKey(), false);
        } catch (NoSuchProjectException | IOException e) {
          throw new SubmoduleException("Cannot access submodule", e);
        }
        OpenRepo subOr = orm.getRepo(s.getSubmodule().getParentKey());
        Repository subrepo = subOr.repo;

        Ref ref = subrepo.getRefDatabase().exactRef(s.getSubmodule().get());
        if (ref == null) {
          ed.add(new DeletePath(s.getPath()));
          continue;
        }

        final ObjectId updateTo = ref.getObjectId();
        RevCommit newCommit = subOr.rw.parseCommit(updateTo);

        subOr.rw.parseBody(newCommit);
        if (author == null) {
          author = newCommit.getAuthorIdent();
        } else if (!author.equals(newCommit.getAuthorIdent())) {
          sameAuthorForAll = false;
        }

        DirCacheEntry dce = dc.getEntry(s.getPath());
        ObjectId oldId;
        if (dce != null) {
          if (!dce.getFileMode().equals(FileMode.GITLINK)) {
            log.error("Requested to update gitlink " + s.getPath() + " in "
                + s.getSubmodule().getParentKey().get() + " but entry "
                + "doesn't have gitlink file mode.");
            continue;
          }
          oldId = dce.getObjectId();
        } else {
          // This submodule did not exist before. We do not want to add
          // the full submodule history to the commit message, so omit it.
          oldId = updateTo;
        }

        ed.add(new PathEdit(s.getPath()) {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(updateTo);
          }
        });
        if (verboseSuperProject) {
          msgbuf.append("Project: " + s.getSubmodule().getParentKey().get());
          msgbuf.append(" " + s.getSubmodule().getShortName());
          msgbuf.append(" " + updateTo.getName());
          msgbuf.append("\n\n");

          try {
            subOr.rw.resetRetain(subOr.canMergeFlag);
            subOr.rw.markStart(newCommit);
            subOr.rw.markUninteresting(subOr.rw.parseCommit(oldId));
            for (RevCommit c : subOr.rw) {
              subOr.rw.parseBody(c);
              msgbuf.append(c.getFullMessage() + "\n\n");
            }
          } catch (IOException e) {
            throw new SubmoduleException("Could not perform a revwalk to "
                + "create superproject commit message", e);
          }
        }
      }
      ed.finish();

      if (!sameAuthorForAll || author == null) {
        author = myIdent;
      }

      ObjectInserter oi = or.repo.newObjectInserter();
      ObjectId tree = dc.writeTree(oi);

      ObjectId currentCommitId =
          or.repo.exactRef(subscriber.get()).getObjectId();

      CommitBuilder commit = new CommitBuilder();
      commit.setTreeId(tree);
      commit.setParentIds(new ObjectId[] {currentCommitId});
      commit.setAuthor(author);
      commit.setCommitter(myIdent);
      commit.setMessage(msgbuf.toString());
      oi.insert(commit);
      oi.flush();

      ObjectId commitId = oi.idFor(Constants.OBJ_COMMIT, commit.build());

      final RefUpdate rfu = or.repo.updateRef(subscriber.get());
      rfu.setForceUpdate(false);
      rfu.setNewObjectId(commitId);
      rfu.setExpectedOldObjectId(currentCommitId);
      rfu.setRefLogMessage("Submit to " + subscriber.getParentKey().get(), true);

      switch (rfu.update()) {
        case NEW:
        case FAST_FORWARD:
          gitRefUpdated.fire(subscriber.getParentKey(), rfu, account);
          // TODO since this is performed "in the background" no mail will be
          // sent to inform users about the updated branch
          break;
        case FORCED:
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case NO_CHANGE:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
        default:
          throw new IOException(rfu.getResult().name());
      }
    } catch (IOException e) {
      throw new SubmoduleException("Cannot update gitlinks for "
          + subscriber.get(), e);
    }
  }

  private static DirCache readTree(final Ref branch, RevWalk rw)
      throws MissingObjectException, IncorrectObjectTypeException,
      IOException {
    final DirCache dc = DirCache.newInCore();
    final DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        rw.getObjectReader(), rw.parseTree(branch.getObjectId()));
    b.finish();
    return dc;
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug("[" + orm.getSubmissionId() + "]" + msg, args);
    }
  }
}
