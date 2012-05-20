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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
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
import com.google.inject.assistedinject.Assisted;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class SubmoduleOp {
  public interface Factory {
    SubmoduleOp create(Branch.NameKey destBranch, RevCommit mergeTip,
        RevWalk rw, Repository db, Project destProject, List<Change> submitted,
        Map<Change.Id, CodeReviewCommit> commits);
  }

  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);
  private static final String GIT_MODULES = ".gitmodules";

  private final Branch.NameKey destBranch;
  private RevCommit mergeTip;
  private RevWalk rw;
  private final Provider<String> urlProvider;
  private ReviewDb schema;
  private Repository db;
  private Project destProject;
  private List<Change> submitted;
  private final Map<Change.Id, CodeReviewCommit> commits;
  private final PersonIdent myIdent;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated replication;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Set<Branch.NameKey> updatedSubscribers;

  @Inject
  public SubmoduleOp(@Assisted final Branch.NameKey destBranch,
      @Assisted RevCommit mergeTip, @Assisted RevWalk rw,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider,
      final SchemaFactory<ReviewDb> sf, @Assisted Repository db,
      @Assisted Project destProject, @Assisted List<Change> submitted,
      @Assisted final Map<Change.Id, CodeReviewCommit> commits,
      @GerritPersonIdent final PersonIdent myIdent,
      GitRepositoryManager repoManager, GitReferenceUpdated replication) {
    this.destBranch = destBranch;
    this.mergeTip = mergeTip;
    this.rw = rw;
    this.urlProvider = urlProvider;
    this.schemaFactory = sf;
    this.db = db;
    this.destProject = destProject;
    this.submitted = submitted;
    this.commits = commits;
    this.myIdent = myIdent;
    this.repoManager = repoManager;
    this.replication = replication;

    updatedSubscribers = new HashSet<Branch.NameKey>();
  }

  public void update() throws SubmoduleException {
    try {
      schema = schemaFactory.open();

      updateSubmoduleSubscriptions();
      updateSuperProjects(destBranch, mergeTip.getId().toObjectId(), null);
    } catch (OrmException e) {
      throw new SubmoduleException("Cannot open database", e);
    } finally {
      if (schema != null) {
        schema.close();
        schema = null;
      }
    }
  }

  private void updateSubmoduleSubscriptions() throws SubmoduleException {
    if (urlProvider.get() == null) {
      logAndThrowSubmoduleException("Cannot establish canonical web url used to access gerrit."
              + " It should be provided in gerrit.config file.");
    }

    try {
      final TreeWalk tw = TreeWalk.forPath(db, GIT_MODULES, mergeTip.getTree());
      if (tw != null
          && (FileMode.REGULAR_FILE.equals(tw.getRawMode(0)) || FileMode.EXECUTABLE_FILE
              .equals(tw.getRawMode(0)))) {

        BlobBasedConfig bbc =
            new BlobBasedConfig(null, db, mergeTip, GIT_MODULES);

        final String thisServer = new URI(urlProvider.get()).getHost();

        final Branch.NameKey target =
            new Branch.NameKey(new Project.NameKey(destProject.getName()),
                destBranch.get());

        final Set<SubmoduleSubscription> oldSubscriptions =
            new HashSet<SubmoduleSubscription>(schema.submoduleSubscriptions()
                .bySuperProject(destBranch).toList());
        final List<SubmoduleSubscription> newSubscriptions =
            new SubmoduleSectionParser(bbc, thisServer, target, repoManager)
                .parseAllSections();

        final Set<SubmoduleSubscription> alreadySubscribeds =
            new HashSet<SubmoduleSubscription>();
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

  private void updateSuperProjects(final Branch.NameKey updatedBranch,
      final ObjectId mergedCommit, final String msg) throws SubmoduleException {
    try {
      final List<SubmoduleSubscription> subscribers =
          schema.submoduleSubscriptions().bySubmodule(updatedBranch).toList();

      if (!subscribers.isEmpty()) {
        String msgbuf = msg;
        if (msgbuf == null) {
          // Initialize the message buffer
          msgbuf = "";

          // The first updatedBranch on a cascade event of automatic
          // updates of repos is added to updatedSubscribers set so
          // if we face a situation having
          // submodule-a(master)-->super(master)-->submodule-a(master),
          // it will be detected we have a circular subscription
          // when updateSuperProjects is called having as updatedBranch
          // the super(master) value.
          updatedSubscribers.add(updatedBranch);

          for (final Change chg : submitted) {
            final CodeReviewCommit c = commits.get(chg.getId());
            if (c != null
                && (c.statusCode == CommitMergeStatus.CLEAN_MERGE || c.statusCode == CommitMergeStatus.CLEAN_PICK)) {
              msgbuf += "\n";
              msgbuf += c.getFullMessage();
            }
          }
        }

        // update subscribers of this module
        for (final SubmoduleSubscription s : subscribers) {
          if (!updatedSubscribers.add(s.getSuperProject())) {
            log.error("Possible circular subscription involving "
                + s.toString());
          } else {

            Map<Branch.NameKey, ObjectId> modules =
                new HashMap<Branch.NameKey, ObjectId>(1);
            modules.put(updatedBranch, mergedCommit);

            Map<Branch.NameKey, String> paths =
                new HashMap<Branch.NameKey, String>(1);
            paths.put(updatedBranch, s.getPath());

            try {
              updateGitlinks(s.getSuperProject(), modules, paths, msgbuf);
            } catch (SubmoduleException e) {
              throw e;
            }
          }
        }
      }
    } catch (OrmException e) {
      logAndThrowSubmoduleException("Cannot read subscription records", e);
    }
  }

  private void updateGitlinks(final Branch.NameKey subscriber,
      final Map<Branch.NameKey, ObjectId> modules,
      final Map<Branch.NameKey, String> paths, final String msg)
      throws SubmoduleException {
    PersonIdent author = null;

    final StringBuilder msgbuf = new StringBuilder();
    msgbuf.append("Updated " + subscriber.getParentKey().get());
    Repository pdb = null;

    try {
      boolean sameAuthorForAll = true;

      for (final Map.Entry<Branch.NameKey, ObjectId> me : modules.entrySet()) {
        RevCommit c = rw.parseCommit(me.getValue());

        msgbuf.append("\nProject: ");
        msgbuf.append(me.getKey().getParentKey().get());
        msgbuf.append("  " + me.getValue().getName());
        msgbuf.append("\n");
        if (modules.size() == 1 && msg != null) {
          msgbuf.append(msg);
        } else {
          msgbuf.append(c.getShortMessage());
        }
        msgbuf.append("\n");

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
      for (final Map.Entry<Branch.NameKey, ObjectId> me : modules.entrySet()) {
        ed.add(new PathEdit(paths.get(me.getKey())) {
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(me.getValue().copy());
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
      commit.setMessage(msgbuf.toString());
      oi.insert(commit);

      ObjectId commitId = oi.idFor(Constants.OBJ_COMMIT, commit.build());

      final RefUpdate rfu = pdb.updateRef(subscriber.get());
      rfu.setForceUpdate(false);
      rfu.setNewObjectId(commitId);
      rfu.setExpectedOldObjectId(currentCommitId);
      rfu
          .setRefLogMessage("Submit to " + subscriber.getParentKey().get(),
              true);

      switch (rfu.update()) {
        case NEW:
        case FAST_FORWARD:
          replication.fire(subscriber.getParentKey(), rfu.getName());
          // TODO since this is performed "in the background" no mail will be
          // sent to inform users about the updated branch
          break;

        default:
          throw new IOException(rfu.getResult().name());
      }

      // Recursive call: update subscribers of the subscriber
      updateSuperProjects(subscriber, commitId, msgbuf.toString());
    } catch (IOException e) {
      logAndThrowSubmoduleException("Cannot update gitlinks for "
          + subscriber.get(), e);
    } finally {
      if (pdb != null) {
        pdb.close();
      }
    }
  }

  private static DirCache readTree(final Repository pdb, final Ref branch)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    final RevWalk rw = new RevWalk(pdb);

    final DirCache dc = DirCache.newInCore();
    final DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        pdb.newObjectReader(), rw.parseTree(branch.getObjectId()));
    b.finish();
    return dc;
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
