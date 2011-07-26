// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Branch.NameKey;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Subscription;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import javax.annotation.Nullable;

public class SubmoduleOp {

  public interface Factory {
    SubmoduleOp create(Branch.NameKey destBranch, RevCommit mergeTip,
        RevWalk rw, Repository db, Project destProject, List<Change> submitted,
        Map<Change.Id, CodeReviewCommit> commits);
  }

  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);
  private final Branch.NameKey destBranch;
  private RevCommit mergeTip;
  private RevWalk rw;
  private final Provider<String> urlProvider;
  private ReviewDb schema;
  private Repository db;
  private Project destProject;
  private HashSet<NameKey> updatedSubscribers;
  private List<Change> submitted;
  private final Map<Change.Id, CodeReviewCommit> commits;
  private final PersonIdent myIdent;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replication;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private List<Subscription> newSubscriptions;

  @Inject
  public SubmoduleOp(@Assisted final Branch.NameKey destBranch,
      @Assisted RevCommit mergeTip, @Assisted RevWalk rw,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider,
      final SchemaFactory<ReviewDb> sf, @Assisted Repository db,
      @Assisted Project destProject, @Assisted List<Change> submitted,
      @Assisted final Map<Change.Id, CodeReviewCommit> commits,
      @GerritPersonIdent final PersonIdent myIdent,
      GitRepositoryManager repoManager, ReplicationQueue replication) {
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

      updateSubscriptions();
      updateSubscribers(destBranch, mergeTip.getId().toObjectId(), null);
    } catch (OrmException e) {
      throw new SubmoduleException("Cannot open database", e);
    } finally {
      schema.close();
      schema = null;
    }
  }

  private void updateSubscriptions() throws SubmoduleException {
    final String gitmodulesFile = ".gitmodules";
    try {
      final TreeWalk tw =
          TreeWalk.forPath(db, gitmodulesFile, mergeTip.getTree());
      if (tw != null
          && (FileMode.REGULAR_FILE.equals(tw.getRawMode(0)) || FileMode.EXECUTABLE_FILE
              .equals(tw.getRawMode(0)))) {

        BlobBasedConfig bbc =
            new BlobBasedConfig(null, db, mergeTip, gitmodulesFile);

        final String thisServer = getSrvName(urlProvider.get());

        if (thisServer == null) {
          if (urlProvider.get() == null) {
            throw new DataFormatException(
                "You must define gerrit.canonicalWebUrl inside gerrit.config.");
          } else {
            throw new DataFormatException("At parse of " + gitmodulesFile
                + ": problem retrieving name of this server.");
          }
        }

        List<Subscription> oldSubscriptions =
            schema.subscriptions().getSubscription(destBranch).toList();
        newSubscriptions = new ArrayList<Subscription>();

        for (String id : bbc.getSubsections("submodule")) {
          String url = bbc.getString("submodule", id, "url");
          String path = bbc.getString("submodule", id, "path");
          String revision = bbc.getString("submodule", id, "revision");

          if (url != null && url.length() > 0 && path != null
              && path.length() > 0 && revision != null && revision.length() > 0) {
            boolean pathIsRelative = url.startsWith("/");
            String server = null;
            if (!pathIsRelative) {
              server = getSrvName(url);
            }
            if ((pathIsRelative)
                || (server != null && server.equalsIgnoreCase(thisServer))) {
              if (revision.equals(".")) {
                revision = destBranch.get();
              } else if (!revision.startsWith(Constants.R_REFS)) {
                revision = Constants.R_HEADS + revision;
              }

              String projectName = null;
              if (!destProject.getName().contains("/")) {
                projectName =
                    url.substring(url.lastIndexOf("/") + 1, url.length());
              } else {
                String prefix =
                    destProject.getName().substring(0,
                        destProject.getName().indexOf("/") + 1);
                if (url.contains(prefix)) {
                  projectName =
                      url.substring(url.indexOf(prefix), url.length());
                } else {
                  projectName =
                      url.substring(url.lastIndexOf("/") + 1, url.length());
                }
              }

              final Branch.NameKey target =
                  new Branch.NameKey(
                      new Project.NameKey(destProject.getName()), destBranch
                          .get());

              final Branch.NameKey source =
                  new Branch.NameKey(new Project.NameKey(projectName), revision);

              newSubscriptions.add(new Subscription(target, source, path));
            }
          }
        }

        List<Subscription> alreadySubscribeds = new ArrayList<Subscription>();
        for (Subscription s : newSubscriptions) {
          if (oldSubscriptions.contains(s)) {
            alreadySubscribeds.add(s);
          }
        }

        oldSubscriptions.removeAll(newSubscriptions);
        newSubscriptions.removeAll(alreadySubscribeds);

        if (!oldSubscriptions.isEmpty()) {
          schema.subscriptions().delete(oldSubscriptions);
        }
        schema.subscriptions().insert(newSubscriptions);
      }
    }

    catch (OrmException e) {
      log.error("Database problem at update of subscriptions table from "
          + gitmodulesFile + " file.", e);
      throw new SubmoduleException(
          "Database problem at update of subscriptions table from "
              + gitmodulesFile + " file.", e);
    } catch (ConfigInvalidException e) {
      log.error("Problem at update of subscriptions table: " + gitmodulesFile
          + " config file is invalid.", e);
      throw new SubmoduleException("Problem at update of subscriptions table: "
          + gitmodulesFile + " config file is invalid.", e);
    } catch (IOException e) {
      log.error("Problem at update of subscriptions table from "
          + gitmodulesFile + ".", e);
      throw new SubmoduleException(
          "Problem at update of subscriptions table from " + gitmodulesFile
              + ".", e);
    } catch (DataFormatException e) {
      log.error(e.getMessage(), e);
      throw new SubmoduleException(e.getMessage(), e);
    }
  }

  private void updateSubscribers(final Branch.NameKey updatedBranch,
      final ObjectId mergedCommit, final String msg) throws SubmoduleException {
    try {
      final List<Subscription> subscribers =
          schema.subscriptions().getSubscribers(updatedBranch).toList();

      if (!subscribers.isEmpty()) {
        final StringBuilder msgbuf = new StringBuilder();
        if (msg == null) {
          // safeguard against circular subscriptions, msg == null is only
          // passed by the first call.
          updatedSubscribers.add(updatedBranch);
          for (final Change chg : submitted) {
            final CodeReviewCommit c = commits.get(chg.getId());
            if (c != null
                && (c.statusCode == CommitMergeStatus.CLEAN_MERGE || c.statusCode == CommitMergeStatus.CLEAN_PICK)) {
              msgbuf.append("\n");
              msgbuf.append(c.getFullMessage());
            }
          }
        } else {
          msgbuf.append(msg);
        }

        // update subscribers of this module
        for (final Subscription s : subscribers) {
          if (updatedSubscribers.contains(s.getSubscriber())) {
            log.error("Possible circular subscription involving "
                + s.toString());
          } else {

            Map<Branch.NameKey, ObjectId> modules =
                new HashMap<Branch.NameKey, ObjectId>(1);
            modules.put(updatedBranch, mergedCommit);

            Map<Branch.NameKey, String> pathes =
                new HashMap<Branch.NameKey, String>(1);
            pathes.put(updatedBranch, s.getPath());

            try {
              updateGitlinks(s.getSubscriber(), modules, pathes, msgbuf
                  .toString());
            } catch (SubmoduleException e) {
              // Rollback subscriptions since gitlink update has failed.
              List<Subscription> toDelete = new ArrayList<Subscription>(1);
              toDelete.add(s);
              schema.subscriptions().delete(toDelete);
              throw e;
            }

            updatedSubscribers.add(s.getSubscriber());
          }
        }
      }
    } catch (OrmException e) {
      log.error("Cannot read subscription records", e);
      throw new SubmoduleException("Cannot read subscription records", e);
    }
  }

  private void updateGitlinks(final Branch.NameKey subscriber,
      final Map<Branch.NameKey, ObjectId> modules,
      final Map<Branch.NameKey, String> pathes, final String msg)
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
        ed.add(new PathEdit(pathes.get(me.getKey())) {
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
          replication.scheduleUpdate(subscriber.getParentKey(), rfu.getName());
          // TODO since this is performed "in the background" no mail will be
          // sent
          // to inform users about the updated branch
          break;

        default:
          throw new IOException(rfu.getResult().name());
      }

      // update subscribers of the subscriber
      updateSubscribers(subscriber, commitId, msgbuf.toString());
    } catch (IOException e) {
      log.error("Cannot update gitlinks for " + subscriber.get(), e);
      throw new SubmoduleException("Cannot update gitlinks for "
          + subscriber.get(), e);
    } finally {
      if (pdb != null) {
        pdb.close();
      }
    }
  }

  private String getSrvName(String srvUrl) {
    if (srvUrl != null) {
      final int begin = srvUrl.indexOf("//") + 2;

      if (begin >= 2) {
        int end = srvUrl.length();
        int e = srvUrl.indexOf("/", begin);

        if (e > 0) {
          end = e;
        }

        e = srvUrl.indexOf(":", begin);

        if (e > 0 && e < end) {
          end = e;
        }

        if (end >= begin) {
          return srvUrl.substring(begin, end);
        }
      }
    }

    return srvUrl;
  }

  private DirCache readTree(Repository pdb, Ref branch)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    RevWalk rw = new RevWalk(pdb);

    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        pdb.newObjectReader(), rw.parseTree(branch.getObjectId()));
    b.finish();
    return dc;
  }
}
