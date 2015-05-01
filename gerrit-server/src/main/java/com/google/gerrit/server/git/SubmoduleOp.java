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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.util.SubmoduleSectionParser;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubmoduleOp {
  public interface Factory {
    SubmoduleOp create();
  }

  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);
  private static final String GIT_MODULES = ".gitmodules";

  private final Provider<String> urlProvider;
  //private ReviewDb schema;
  private final PersonIdent myIdent;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Set<Branch.NameKey> updatedSubscribers;
  private final Account account;
  private final ChangeHooks changeHooks;
  private final SubmoduleSectionParser.Factory subSecParserFactory;

  @Inject
  public SubmoduleOp(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      SchemaFactory<ReviewDb> sf,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Nullable Account account,
      ChangeHooks changeHooks,
      SubmoduleSectionParser.Factory subSecParserFactory) {
    this.urlProvider = urlProvider;
    this.schemaFactory = sf;
    this.myIdent = myIdent;
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.account = account;
    this.changeHooks = changeHooks;
    this.subSecParserFactory = subSecParserFactory;

    updatedSubscribers = new HashSet<>();
  }

  protected void updateSubmoduleSubscriptions(Set<Branch.NameKey> branches)
      throws SubmoduleException {
    for (Branch.NameKey branch : branches) {
      updateSubmoduleSubscriptions(branch);
    }
  }

  protected void updateSubmoduleSubscriptions(Branch.NameKey destBranch)
      throws SubmoduleException {
    if (urlProvider.get() == null) {
      logAndThrowSubmoduleException("Cannot establish canonical web url used to access gerrit."
              + " It should be provided in gerrit.config file.");
    }
    try (ReviewDb schema = schemaFactory.open();) {
      Repository repo = repoManager.openRepository(destBranch.getParentKey());
      ObjectId id = repo.resolve(destBranch.get());
      RevWalk rw = CodeReviewCommit.newRevWalk(repo);
      RevCommit commit = rw.parseCommit(id);

      Set<SubmoduleSubscription> oldSubscriptions =
          Sets.newHashSet(schema.submoduleSubscriptions()
              .bySuperProject(destBranch));

      Set<SubmoduleSubscription> newSubscriptions;
      TreeWalk tw = TreeWalk.forPath(repo, GIT_MODULES, commit.getTree());
      if (tw != null
          && (FileMode.REGULAR_FILE.equals(tw.getRawMode(0)) || FileMode.EXECUTABLE_FILE
              .equals(tw.getRawMode(0)))) {
        BlobBasedConfig bbc =
            new BlobBasedConfig(null, repo, commit, GIT_MODULES);

        String thisServer = new URI(urlProvider.get()).getHost();

        newSubscriptions = subSecParserFactory.create(bbc, thisServer, destBranch)
            .parseAllSections();
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
        schema.submoduleSubscriptions().delete(oldSubscriptions);
      }
      if (!newSubscriptions.isEmpty()) {
        schema.submoduleSubscriptions().insert(newSubscriptions);
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

  protected void updateSuperProjects(Set<Branch.NameKey> updatedBranches)
      throws SubmoduleException {
    try (ReviewDb schema = schemaFactory.open();) {
      // These (repo/branch) will be updated later with all the given
      // individual submodule subscriptions
      Map<Branch.NameKey, Set<SubmoduleSubscription>> targets = Maps.newHashMap();

      for (Branch.NameKey updatedBranch : updatedBranches) {
        for (SubmoduleSubscription sub : schema.submoduleSubscriptions().bySubmodule(updatedBranch)) {
          if (!targets.containsKey(sub.getSuperProject())) {
            targets.put(sub.getSuperProject(), Sets.newHashSet(sub));
          } else {
            targets.get(sub.getSuperProject()).add(sub);
          }
        }
      }

      updatedSubscribers.addAll(Sets.newHashSet(updatedBranches));

      // update subscribers
      for (Branch.NameKey dest : targets.keySet()) {
        int size = targets.get(dest).size();
        List<SubmoduleSubscription> incorrectSubscriptions = Lists.newLinkedList();

        Map<String, Branch.NameKey> paths = new HashMap<>(size);

        for (final SubmoduleSubscription s : targets.get(dest)) {
          paths.put(s.getPath(), s.getSubmodule());
        }
        try {
          if (!updatedSubscribers.add(dest)) {
            log.error("Possible circular subscription involving " + dest);
          } else {
            updateGitlinks(dest, paths);
          }
        } catch (SubmoduleException e) {
          log.warn("Cannot update gitlinks for " + dest + " due to " + e.getMessage());
          //incorrectSubscriptions.add(dest);
        } catch (Exception e) {
          log.error("Cannot update gitlinks for " + dest, e);
        }

        if (!incorrectSubscriptions.isEmpty()) {
          try {
            schema.submoduleSubscriptions().delete(incorrectSubscriptions);
            log.info("Deleted incorrect submodule subscription(s) "
                + incorrectSubscriptions);
          } catch (OrmException e) {
            log.error("Cannot delete submodule subscription(s) "
                + incorrectSubscriptions, e);
          }
        }
      }
    } catch (OrmException e) {
      logAndThrowSubmoduleException("Cannot read subscription records", e);
    }
  }

  /**
   * This updates the submodules in one branch of one repository.
   *
   * @param subscriber the branch of the repository which should be changed.
   * @param paths The submodule paths ids which should be updated to.
   * @throws SubmoduleException
   */
  private void updateGitlinks(final Branch.NameKey subscriber,
      final Map<String, Branch.NameKey> paths)
      throws SubmoduleException {
    PersonIdent author = null;

    Repository pdb = null;
    RevWalk recRw = null;

    final Map<String, ObjectId> modules = new HashMap<>();
    try {
      boolean sameAuthorForAll = true;

      for (final Map.Entry<String, Branch.NameKey> me : paths.entrySet()) {
        Repository subrepo = repoManager.openRepository(me.getValue().getParentKey());
        ObjectId updateTo = subrepo.resolve(me.getValue().get());
        modules.put(me.getKey(), updateTo);

        RevWalk rw = CodeReviewCommit.newRevWalk(subrepo);
        RevCommit c = rw.parseCommit(updateTo);

        //todo!
        if (c == null) {
          continue;
        }

        if (author == null) {
          author = c.getAuthorIdent();
        } else if (!author.equals(c.getAuthorIdent())) {
          sameAuthorForAll = false;
        }
      }

      if (!sameAuthorForAll || author == null) {
        author = myIdent;
      }

      pdb = repoManager.openRepository(subscriber.getParentKey());
      if (pdb.getRef(subscriber.get()) == null) {
        throw new SubmoduleException(
            "The branch was probably deleted from the subscriber repository");
      }

      final ObjectId currentCommitId =
          pdb.getRef(subscriber.get()).getObjectId();

      DirCache dc = readTree(pdb, pdb.getRef(subscriber.get()));
      DirCacheEditor ed = dc.editor();
      for (final Map.Entry<String, Branch.NameKey> me : paths.entrySet()) {
        ed.add(new PathEdit(me.getKey()) {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            // calculate new value for objectID, may call make a call outside
            // to store all the commit messages.
            ObjectId id = modules.get(me.getKey());
            ent.setObjectId(id);
          }
        });
      }
      ed.finish();

      ObjectInserter oi = pdb.newObjectInserter();
      ObjectId tree = dc.writeTree(oi);

      final CommitBuilder commit = new CommitBuilder();
      commit.setTreeId(tree);
      commit.setParentIds(new ObjectId[] {currentCommitId});
      commit.setAuthor(author);
      commit.setCommitter(myIdent);
      commit.setMessage(getCommitMessage());
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

        default:
          throw new IOException(rfu.getResult().name());
      }

      recRw = new RevWalk(pdb);

      // Recursive call: update subscribers of the subscriber
      updateSuperProjects(Sets.newHashSet(subscriber));
    } catch (IOException e) {
        throw new SubmoduleException("Cannot update gitlinks for "
            + subscriber.get(), e);
    } finally {
      if (recRw != null) {
        recRw.close();
      }
      if (pdb != null) {
        pdb.close();
      }
    }
  }

  private static String getCommitMessage() {
    final StringBuilder msgbuf = new StringBuilder("Updated git submodules\n");
    /* TODO:
        msgbuf.append("\nProject: ");
        msgbuf.append(me.getKey().getParentKey().get());
        msgbuf.append("  ").append(me.getValue().getName());
        msgbuf.append("\n");
        if (modules.size() == 1) {
          if (!Strings.isNullOrEmpty(msg)) {
            msgbuf.append(msg);
          } else {
            msgbuf.append("\n");
            msgbuf.append(c.getFullMessage());
          }
        } else {
          msgbuf.append(c.getShortMessage());
        }
        msgbuf.append("\n");
     */
    return msgbuf.toString();
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
