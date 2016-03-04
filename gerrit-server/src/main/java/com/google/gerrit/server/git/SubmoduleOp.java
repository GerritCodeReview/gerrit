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
import com.google.common.collect.Sets;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.inject.Inject;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SubmoduleOp {
  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);

  private final GitModules.Factory gitmodulesFactory;
  private final PersonIdent myIdent;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final Set<Branch.NameKey> updatedSubscribers;
  private final Account account;
  private final ChangeHooks changeHooks;
  private final boolean verboseSuperProject;
  private final boolean enableSuperProjectSubscriptions;
  private String submissionId;

  @Inject
  public SubmoduleOp(
      GitModules.Factory gitmodulesFactory,
      @GerritPersonIdent PersonIdent myIdent,
      @GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Nullable Account account,
      ChangeHooks changeHooks) {
    this.gitmodulesFactory = gitmodulesFactory;
    this.myIdent = myIdent;
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.account = account;
    this.changeHooks = changeHooks;
    this.verboseSuperProject = cfg.getBoolean("submodule",
        "verboseSuperprojectUpdate", true);
    this.enableSuperProjectSubscriptions = cfg.getBoolean("submodule",
        "enableSuperProjectSubscriptions", false);
    updatedSubscribers = new HashSet<>();

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
          try (Repository repo = repoManager.openRepository(s.getProject())) {
            for (Ref ref : repo.getAllRefs().values()) {
              ret.add(new Branch.NameKey(s.getProject(), ref.getName()));
            }
          }
        } else {
          if (r.isWildcard()) {
            // refs/heads/*:refs/heads/*
            ret.add(new Branch.NameKey(s.getProject(),
                r.expandFromSource(src.get()).getDestination()));
          } else {
            // e.g. refs/heads/master:refs/heads/stable
            ret.add(new Branch.NameKey(s.getProject(), r.getDestination()));
          }
        }
      }
    }
    logDebug("Returning possible branches: " + ret +
        "for project " + s.getProject());
    return ret;
  }

  private Collection<SubmoduleSubscription>
      superProjectSubscriptionsForSubmoduleBranch(
      Branch.NameKey branch) throws SubmoduleException {
    logDebug("Calculating possible superprojects for " + branch);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(branch.getParentKey())) {
      ProjectConfig cfg =
          new ProjectConfig(branch.getParentKey());
      cfg.load(repo);
      for (SubscribeSection s : cfg.getSubscribeSections(branch)) {
        Collection<Branch.NameKey> branches = getDestinationBranches(branch, s);
        for (Branch.NameKey targetBranch : branches) {
          GitModules m = gitmodulesFactory.create(targetBranch, submissionId);
          m.load();
          ret.addAll(m.subscribedTo(branch));
        }
      }
    } catch (IOException e) {
      logAndThrowSubmoduleException("Could not update superproject", e);
    } catch (ConfigInvalidException e) {
      logAndThrowSubmoduleException("Error in project.config in " +
          "refs/meta/config of project" + branch.getParentKey(), e);
    }
    logDebug("Calculated superprojects for " + branch + " are "+ ret);
    return ret;
  }

  protected void updateSuperProjects(ReviewDb db,
      Collection<Branch.NameKey> updatedBranches, String submissionId)
          throws SubmoduleException {
    if (!enableSuperProjectSubscriptions) {
      logDebug("Updating superprojects disabled");
      return;
    }
    this.submissionId = submissionId;
    logDebug("Updating superprojects");
    // These (repo/branch) will be updated later with all the given
    // individual submodule subscriptions
    Multimap<Branch.NameKey, SubmoduleSubscription> targets =
        HashMultimap.create();

    for (Branch.NameKey updatedBranch : updatedBranches) {
      for (SubmoduleSubscription sub :
        superProjectSubscriptionsForSubmoduleBranch(updatedBranch)) {
        targets.put(sub.getSuperProject(), sub);
      }
    }
    updatedSubscribers.addAll(updatedBranches);
    // Update subscribers.
    for (Branch.NameKey dest : targets.keySet()) {
      try {
        if (!updatedSubscribers.add(dest)) {
          log.error("Possible circular subscription involving " + dest);
        } else {
          updateGitlinks(db, dest, targets.get(dest));
        }
      } catch (SubmoduleException e) {
        log.warn("Cannot update gitlinks for " + dest, e);
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
  private void updateGitlinks(ReviewDb db, Branch.NameKey subscriber,
      Collection<SubmoduleSubscription> updates) throws SubmoduleException {
    PersonIdent author = null;
    StringBuilder msgbuf = new StringBuilder("Update git submodules\n\n");
    boolean sameAuthorForAll = true;

    try (Repository pdb = repoManager.openRepository(subscriber.getParentKey())) {
      if (pdb.exactRef(subscriber.get()) == null) {
        throw new SubmoduleException(
            "The branch was probably deleted from the subscriber repository");
      }

      DirCache dc = readTree(pdb, pdb.exactRef(subscriber.get()));
      DirCacheEditor ed = dc.editor();

      for (SubmoduleSubscription s : updates) {
        try (Repository subrepo = repoManager.openRepository(
            s.getSubmodule().getParentKey());
            RevWalk rw = CodeReviewCommit.newRevWalk(subrepo)) {
          Ref ref = subrepo.getRefDatabase().exactRef(s.getSubmodule().get());
          if (ref == null) {
            ed.add(new DeletePath(s.getPath()));
            continue;
          }

          final ObjectId updateTo = ref.getObjectId();
          RevCommit newCommit = rw.parseCommit(updateTo);

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
              rw.markStart(newCommit);
              rw.markUninteresting(rw.parseCommit(oldId));
              for (RevCommit c : rw) {
                msgbuf.append(c.getFullMessage() + "\n\n");
              }
            } catch (IOException e) {
              logAndThrowSubmoduleException("Could not perform a revwalk to "
                  + "create superproject commit message", e);
            }
          }
        }
      }
      ed.finish();

      if (!sameAuthorForAll || author == null) {
        author = myIdent;
      }

      ObjectInserter oi = pdb.newObjectInserter();
      ObjectId tree = dc.writeTree(oi);

      ObjectId currentCommitId =
          pdb.exactRef(subscriber.get()).getObjectId();

      CommitBuilder commit = new CommitBuilder();
      commit.setTreeId(tree);
      commit.setParentIds(new ObjectId[] {currentCommitId});
      commit.setAuthor(author);
      commit.setCommitter(myIdent);
      commit.setMessage(msgbuf.toString());
      oi.insert(commit);
      oi.flush();

      ObjectId commitId = oi.idFor(Constants.OBJ_COMMIT, commit.build());

      final RefUpdate rfu = pdb.updateRef(subscriber.get());
      rfu.setForceUpdate(false);
      rfu.setNewObjectId(commitId);
      rfu.setExpectedOldObjectId(currentCommitId);
      rfu.setRefLogMessage("Submit to " + subscriber.getParentKey().get(), true);

      switch (rfu.update()) {
        case NEW:
        case FAST_FORWARD:
          gitRefUpdated.fire(subscriber.getParentKey(), rfu);
          changeHooks.doRefUpdatedHook(subscriber, rfu, account);
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
      // Recursive call: update subscribers of the subscriber
      updateSuperProjects(db, Sets.newHashSet(subscriber), submissionId);
    } catch (IOException e) {
      throw new SubmoduleException("Cannot update gitlinks for "
          + subscriber.get(), e);
    }
  }

  private static DirCache readTree(final Repository pdb, final Ref branch)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    try (RevWalk rw = new RevWalk(pdb)) {
      final DirCache dc = DirCache.newInCore();
      final DirCacheBuilder b = dc.builder();
      b.addTree(new byte[0], // no prefix path
          DirCacheEntry.STAGE_0, // standard stage
          pdb.newObjectReader(), rw.parseTree(branch.getObjectId()));
      b.finish();
      return dc;
    }
  }

  private static void logAndThrowSubmoduleException(final String errorMsg,
      final Exception e) throws SubmoduleException {
    log.error(errorMsg, e);
    throw new SubmoduleException(errorMsg, e);
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug("[" + submissionId + "]" + msg, args);
    }
  }
}
