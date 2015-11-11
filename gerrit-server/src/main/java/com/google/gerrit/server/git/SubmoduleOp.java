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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.util.SubmoduleSectionParser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BlobBasedConfig;
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
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SubmoduleOp {
  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);
  private static final String GIT_MODULES = ".gitmodules";

  private final Provider<String> urlProvider;
  private final PersonIdent myIdent;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final Set<Branch.NameKey> updatedSubscribers;
  private final Account account;
  private final ChangeHooks changeHooks;
  private final SubmoduleSectionParser.Factory subSecParserFactory;
  private final boolean verboseSuperProject;

  @Inject
  public SubmoduleOp(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      @GerritPersonIdent PersonIdent myIdent,
      @GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Nullable Account account,
      ChangeHooks changeHooks,
      SubmoduleSectionParser.Factory subSecParserFactory) {
    this.urlProvider = urlProvider;
    this.myIdent = myIdent;
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.account = account;
    this.changeHooks = changeHooks;
    this.subSecParserFactory = subSecParserFactory;
    this.verboseSuperProject = cfg.getBoolean("submodule",
        "verboseSuperprojectUpdate", true);

    updatedSubscribers = new HashSet<>();
  }

  void updateSubmoduleSubscriptions(ReviewDb db, Set<Branch.NameKey> branches)
      throws SubmoduleException {
    for (Branch.NameKey branch : branches) {
      updateSubmoduleSubscriptions(db, branch);
    }
  }

  void updateSubmoduleSubscriptions(ReviewDb db, Branch.NameKey destBranch)
      throws SubmoduleException {
    if (urlProvider.get() == null) {
      logAndThrowSubmoduleException("Cannot establish canonical web url used "
          + "to access gerrit. It should be provided in gerrit.config file.");
    }
    try (Repository repo = repoManager.openRepository(
            destBranch.getParentKey());
        RevWalk rw = new RevWalk(repo)) {

      ObjectId id = repo.resolve(destBranch.get());
      if (id == null) {
        logAndThrowSubmoduleException(
            "Cannot resolve submodule destination branch " + destBranch);
      }
      RevCommit commit = rw.parseCommit(id);

      Set<SubmoduleSubscription> oldSubscriptions =
          Sets.newHashSet(db.submoduleSubscriptions()
              .bySuperProject(destBranch));

      Set<SubmoduleSubscription> newSubscriptions;
      TreeWalk tw = TreeWalk.forPath(repo, GIT_MODULES, commit.getTree());
      if (tw != null
          && (FileMode.REGULAR_FILE.equals(tw.getRawMode(0)) ||
              FileMode.EXECUTABLE_FILE.equals(tw.getRawMode(0)))) {
        BlobBasedConfig bbc =
            new BlobBasedConfig(null, repo, commit, GIT_MODULES);

        String thisServer = new URI(urlProvider.get()).getHost();

        newSubscriptions = subSecParserFactory.create(bbc, thisServer,
            destBranch).parseAllSections();
      } else {
        newSubscriptions = Collections.emptySet();
      }

      Set<SubmoduleSubscription> alreadySubscribeds = new HashSet<>();
      for (SubmoduleSubscription s : newSubscriptions) {
        if (oldSubscriptions.contains(s)) {
          alreadySubscribeds.add(s);
        }
      }

      oldSubscriptions.removeAll(newSubscriptions);
      newSubscriptions.removeAll(alreadySubscribeds);

      if (!oldSubscriptions.isEmpty()) {
        db.submoduleSubscriptions().delete(oldSubscriptions);
      }
      if (!newSubscriptions.isEmpty()) {
        db.submoduleSubscriptions().insert(newSubscriptions);
      }

    } catch (OrmException e) {
      logAndThrowSubmoduleException(
          "Database problem at update of subscriptions table from "
              + GIT_MODULES + " file.", e);
    } catch (ConfigInvalidException e) {
      logAndThrowSubmoduleException(
          "Problem at update of subscriptions table: " + GIT_MODULES
              + " config file is invalid.", e);
    } catch (IOException e) {
      logAndThrowSubmoduleException(
          "Problem at update of subscriptions table from " + GIT_MODULES + ".",
          e);
    } catch (URISyntaxException e) {
      logAndThrowSubmoduleException(
          "Incorrect gerrit canonical web url provided in gerrit.config file.",
          e);
    }
  }

  protected void updateSuperProjects(ReviewDb db,
      Collection<Branch.NameKey> updatedBranches) throws SubmoduleException {
    try {
      // These (repo/branch) will be updated later with all the given
      // individual submodule subscriptions
      Multimap<Branch.NameKey, SubmoduleSubscription> targets =
          HashMultimap.create();

      for (Branch.NameKey updatedBranch : updatedBranches) {
        for (SubmoduleSubscription sub : db.submoduleSubscriptions()
            .bySubmodule(updatedBranch)) {
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
    } catch (OrmException e) {
      logAndThrowSubmoduleException("Cannot read subscription records", e);
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
    StringBuilder msgbuf = new StringBuilder("Updated git submodules\n\n");
    boolean sameAuthorForAll = true;

    try (Repository pdb = repoManager.openRepository(subscriber.getParentKey())) {
      if (pdb.getRef(subscriber.get()) == null) {
        throw new SubmoduleException(
            "The branch was probably deleted from the subscriber repository");
      }

      DirCache dc = readTree(pdb, pdb.getRef(subscriber.get()));
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
          pdb.getRef(subscriber.get()).getObjectId();

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
          gitRefUpdated.fire(subscriber.getParentKey(), rfu, account);
          changeHooks.doRefUpdatedHook(subscriber, rfu, account);
          // TODO since this is performed "in the background" no mail will be
          // sent to inform users about the updated branch
          break;

        default:
          throw new IOException(rfu.getResult().name());
      }
      // Recursive call: update subscribers of the subscriber
      updateSuperProjects(db, Sets.newHashSet(subscriber));
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

  private static void logAndThrowSubmoduleException(final String errorMsg)
      throws SubmoduleException {
    log.error(errorMsg);
    throw new SubmoduleException(errorMsg);
  }
}
