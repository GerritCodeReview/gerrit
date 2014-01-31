// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.PutChange.Input;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

class PutChange implements
    RestModifyView<ProjectResource, Input>, UiAction<ProjectResource> {

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  public static class Input {
    public String msg;
    public String ref;
  }

  private final ReviewDb db;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final Provider<CurrentUser> userProvider;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ChangeInserter.Factory changeInserterFactory;

  @Inject
  PutChange(ReviewDb db,
      GitRepositoryManager gitManager,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<CurrentUser> userProvider,
      CommitValidators.Factory commitValidatorsFactory,
      ChangeInserter.Factory changeInserterFactory) {
    this.db = db;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.userProvider = userProvider;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.changeInserterFactory = changeInserterFactory;
  }

  @Override
  public Response<String> apply(ProjectResource rsrc, Input input)
      throws AuthException, OrmException, InvalidChangeOperationException, IOException {
    String ref = input.ref;
    String msg = input.msg;
    if (Strings.isNullOrEmpty(ref)) {
      throw new InvalidChangeOperationException(
          "Destination branch cannot be null or empty");
    }

    if (Strings.isNullOrEmpty(msg)) {
      throw new InvalidChangeOperationException(
          "Commit message cannot be null or empty");
    }

    if (!userProvider.get().isIdentifiedUser()) {
      throw new AuthException("User must be authenticated to create a change");
    }

    RefControl refControl = rsrc.getControl().controlForRef(ref);
    if (!refControl.canUpload()) {
      throw new AuthException(String.format(
          "Not allowed to create a change on %s", ref));
    }

    Project.NameKey project = rsrc.getNameKey();
    final Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException e) {
      throw new InvalidChangeOperationException("Cannot open repo");
    }

    try {
      RevWalk rw = new RevWalk(git);
      try {
        Ref destRef = git.getRef(ref);
        if (destRef == null) {
          throw new InvalidChangeOperationException("Branch "
              + ref + " does not exist.");
        }

        RevCommit mergeTip = rw.parseCommit(destRef.getObjectId());

        PersonIdent authorIdent =
            ((IdentifiedUser)userProvider.get())
                .newCommitterIdent(myIdent.getWhen(),
                    myIdent.getTimeZone());

        ObjectId computedChangeId =
            ChangeIdUtil
                .computeChangeId(mergeTip.getTree(), mergeTip,
                    mergeTip.getAuthorIdent(), myIdent, msg);

        String commitMessage = ChangeIdUtil.insertId(msg, computedChangeId);

        RevCommit emptyCommit;
        ObjectInserter oi = git.newObjectInserter();

        try {
          CommitBuilder commit = new CommitBuilder();
          commit.setTreeId(mergeTip.getTree().getId());
          commit.setParentId(mergeTip);
          commit.setAuthor(authorIdent);
          commit.setCommitter(authorIdent);
          commit.setMessage(commitMessage);
          emptyCommit = rw.parseCommit(insert(oi, commit));
        } finally {
          oi.release();
        }

        Preconditions.checkNotNull(emptyCommit);

        Change.Key changeKey;
        final List<String> idList = emptyCommit.getFooterLines(CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = new Change.Key(idStr);
        } else {
          changeKey = new Change.Key("I" + computedChangeId.name());
        }

        Id changeId = createNewChange(git, rw, changeKey, project, destRef,
            emptyCommit, refControl);
        return Response.ok(String.valueOf(changeId.get()));
      } finally {
        rw.release();
      }
    } finally {
      git.close();
    }
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Bake a change...")
        .setVisible(userProvider.get().isIdentifiedUser())
        .setTitle("Create an empty change");
  }

  private ObjectId insert(ObjectInserter inserter,
      CommitBuilder mergeCommit) throws IOException,
      UnsupportedEncodingException {
    ObjectId id = inserter.insert(mergeCommit);
    inserter.flush();
    return id;
  }

  private Change.Id createNewChange(Repository git, RevWalk revWalk,
      Change.Key changeKey, Project.NameKey project, Ref destRef,
      RevCommit emptyCommit, RefControl refControl)
      throws OrmException, InvalidChangeOperationException, IOException {
    IdentifiedUser me = (IdentifiedUser)userProvider.get();
    Change change =
        new Change(changeKey, new Change.Id(db.nextChangeId()),
            me.getAccountId(), new Branch.NameKey(project,
                destRef.getName()), TimeUtil.nowTs());
    ChangeInserter ins =
        changeInserterFactory.create(refControl, change, emptyCommit);
    PatchSet newPatchSet = ins.getPatchSet();

    CommitValidators commitValidators =
        commitValidatorsFactory.create(refControl, new NoSshInfo(), git);
    CommitReceivedEvent commitReceivedEvent =
        new CommitReceivedEvent(new ReceiveCommand(ObjectId.zeroId(),
            emptyCommit.getId(), newPatchSet.getRefName()),
            refControl.getProjectControl().getProject(),
            refControl.getRefName(), emptyCommit, me);

    try {
      commitValidators.validateForGerritCommits(commitReceivedEvent);
    } catch (CommitValidationException e) {
      throw new InvalidChangeOperationException(e.getMessage());
    }

    final RefUpdate ru = git.updateRef(newPatchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(emptyCommit);
    ru.disableRefLog();
    if (ru.update(revWalk) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", newPatchSet.getRefName(),
          change.getDest().getParentKey().get(), ru.getResult()));
    }

    ins.insert();
    return change.getId();
  }
}
