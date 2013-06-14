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

package com.google.gerrit.server.change;

import static com.google.gerrit.reviewdb.client.Change.INITIAL_PATCH_SET_ID;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.ChangeNoteCreationException;
import com.google.gerrit.server.git.CreateChangeNotes;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class ChangeInserter {
  private static final Logger log = LoggerFactory.getLogger(ChangeInserter.class);
  public static interface Factory {
    ChangeInserter create(RefControl ctl, Change c, RevCommit rc);
  }

  private final Provider<ReviewDb> dbProvider;
  private final GitReferenceUpdated gitRefUpdated;
  private final ChangeHooks hooks;
  private final ApprovalsUtil approvalsUtil;
  private final TrackingFooters trackingFooters;
  private final ChangeIndexer indexer;

  private final RefControl refControl;
  private final Change change;
  private final PatchSet patchSet;
  private final RevCommit commit;
  private final PatchSetInfo patchSetInfo;
  private final CreateChangeNotes.Factory changeNotesFactory;
  private Repository repo;
  private final PersonIdent myIdent;
  private final GitReferenceUpdated replication;
  private final GitRepositoryManager repoManager;

  private ChangeMessage changeMessage;
  private Set<Account.Id> reviewers;
  private RevWalk revWalk;
  private Change srcChange;

  @Inject
  ChangeInserter(Provider<ReviewDb> dbProvider,
      PatchSetInfoFactory patchSetInfoFactory,
      GitReferenceUpdated gitRefUpdated,
      ChangeHooks hooks,
      ApprovalsUtil approvalsUtil,
      TrackingFooters trackingFooters,
      ChangeIndexer indexer,
      @Assisted RefControl refControl,
      @Assisted Change change,
      @Assisted RevCommit commit,
      final CreateChangeNotes.Factory changeNotesFactory,
      @GerritPersonIdent final PersonIdent myIdent,
      final GitReferenceUpdated replication,
      final GitRepositoryManager repoManager) {
    this.dbProvider = dbProvider;
    this.gitRefUpdated = gitRefUpdated;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.trackingFooters = trackingFooters;
    this.indexer = indexer;
    this.refControl = refControl;
    this.change = change;
    this.commit = commit;
    this.changeNotesFactory = changeNotesFactory;
    this.myIdent = myIdent;
    this.replication = replication;
    this.repoManager = repoManager;


    this.reviewers = Collections.emptySet();

    patchSet =
        new PatchSet(new PatchSet.Id(change.getId(), INITIAL_PATCH_SET_ID));
    patchSet.setCreatedOn(change.getCreatedOn());
    patchSet.setUploader(change.getOwner());
    patchSet.setRevision(new RevId(commit.name()));
    patchSetInfo = patchSetInfoFactory.get(commit, patchSet.getId());
    change.setCurrentPatchSet(patchSetInfo);
    ChangeUtil.computeSortKey(change);
  }

  public ChangeInserter setMessage(ChangeMessage changeMessage) {
    this.changeMessage = changeMessage;
    return this;
  }

  public ChangeInserter setReviewers(Set<Account.Id> reviewers) {
    this.reviewers = reviewers;
    return this;
  }

  public ChangeInserter setDraft() {
    change.setStatus(Change.Status.DRAFT);
    patchSet.setDraft(true);
    return this;
  }

  public ChangeInserter setSource(Change srcChange) {
    this.srcChange = srcChange;
    return this;
  }

  public PatchSet getPatchSet() {
    return patchSet;
  }

  public PatchSetInfo getPatchSetInfo() {
    return patchSetInfo;
  }

  public void insert() throws OrmException, IOException {
    ReviewDb db = dbProvider.get();
    db.changes().beginTransaction(change.getId());
    try {
      ChangeUtil.insertAncestors(db, patchSet.getId(), commit);
      db.patchSets().insert(Collections.singleton(patchSet));
      db.changes().insert(Collections.singleton(change));
      ChangeUtil.updateTrackingIds(db, change, trackingFooters, commit.getFooterLines());
      LabelTypes labelTypes = refControl.getProjectControl().getLabelTypes();
      approvalsUtil.addReviewers(db, labelTypes, change, patchSet, patchSetInfo,
          reviewers, Collections.<Account.Id> emptySet());
      db.commit();
      createNote(commit, srcChange, srcChange.getProject());
    } finally {
      db.rollback();
    }
    if (changeMessage != null) {
      db.changeMessages().insert(Collections.singleton(changeMessage));
    }

    indexer.index(change);
    gitRefUpdated.fire(change.getProject(), patchSet.getRefName(),
        ObjectId.zeroId(), commit);
    hooks.doPatchsetCreatedHook(change, patchSet, db);
  }

  private void createNote(ObjectId commitId, Change change,
      Project.NameKey project) throws OrmException, IOException {
    openRepository(project);

    CreateChangeNotes changeNotes =
        changeNotesFactory.create(repo);
    String notesRef = "";
    try {
      notesRef = changeNotes.create(commitId, change, myIdent);
    } catch (ChangeNoteCreationException e) {
      log.error("createNote: error!!!", e.getMessage());
    }
    //TODO: NOT SURE commit.getId() is the source Commit id!!!!!!!!!!!!!!!!!!!!!
    replication.fire(project, notesRef, commit.getId() , commitId);

    if (revWalk != null) {
      revWalk.release();
    }
    if (repo != null) {
      repo.close();
    }
  }

  private void openRepository(Project.NameKey project) throws IOException {
    try {
      repo = repoManager.openRepository(project);
    } catch (RepositoryNotFoundException notGit) {
      final String m = "Repository \"" + project.get() + "\" unknown.";
      throw new RepositoryNotFoundException(m, notGit);
    } catch (IOException err) {
      final String m = "Error opening repository \"" + project.get() + '"';
      throw new IOException(m, err);
    }

    revWalk = new RevWalk(repo);
    revWalk.sort(RevSort.TOPO);
    revWalk.sort(RevSort.COMMIT_TIME_DESC, true);

    //inserter = repo.newObjectInserter();
  }
}
