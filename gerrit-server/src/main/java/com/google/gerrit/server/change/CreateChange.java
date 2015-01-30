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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.gerrit.server.config.GerritServerConfig;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;

@Singleton
public class CreateChange implements
    RestModifyView<TopLevelResource, ChangeInfo> {

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final Provider<CurrentUser> userProvider;
  private final ProjectsCollection projectsCollection;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeJson json;
  private final ChangeUtil changeUtil;
  private final boolean allowDrafts;

  @Inject
  CreateChange(Provider<ReviewDb> db,
      GitRepositoryManager gitManager,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<CurrentUser> userProvider,
      ProjectsCollection projectsCollection,
      CommitValidators.Factory commitValidatorsFactory,
      ChangeInserter.Factory changeInserterFactory,
      ChangeJson json,
      ChangeUtil changeUtil,
      @GerritServerConfig Config config) {
    this.db = db;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.userProvider = userProvider;
    this.projectsCollection = projectsCollection;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.json = json;
    this.changeUtil = changeUtil;
    this.allowDrafts = config.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<ChangeInfo> apply(TopLevelResource parent,
      ChangeInfo input) throws AuthException, OrmException,
      BadRequestException, UnprocessableEntityException, IOException,
      InvalidChangeOperationException, ResourceNotFoundException {

    if (Strings.isNullOrEmpty(input.project)) {
      throw new BadRequestException("project must be non-empty");
    }

    if (Strings.isNullOrEmpty(input.branch)) {
      throw new BadRequestException("branch must be non-empty");
    }

    if (Strings.isNullOrEmpty(input.subject)) {
      throw new BadRequestException("commit message must be non-empty");
    }

    if (input.status != null) {
      if (input.status != ChangeStatus.NEW
          && input.status != ChangeStatus.DRAFT) {
        throw new BadRequestException("unsupported change status");
      }

      if (!allowDrafts && input.status == ChangeStatus.DRAFT) {
        throw new BadRequestException("cannot upload drafts");
      }
    }

    String refName = input.branch;
    if (!refName.startsWith(Constants.R_REFS)) {
      refName = Constants.R_HEADS + input.branch;
    }

    ProjectResource rsrc = projectsCollection.parse(input.project);

    Capable r = rsrc.getControl().canPushToAtLeastOneRef();
    if (r != Capable.OK) {
      throw new AuthException(r.getMessage());
    }

    RefControl refControl = rsrc.getControl().controlForRef(refName);
    if (!refControl.canUpload() || !refControl.canRead()) {
      throw new AuthException("cannot upload review");
    }

    Project.NameKey project = rsrc.getNameKey();
    Repository git = gitManager.openRepository(project);

    try {
      RevWalk rw = new RevWalk(git);
      try {
        ObjectId parentCommit;
        if (input.baseChange != null) {
          List<Change> changes = changeUtil.findChanges(input.baseChange);
          if (changes.size() != 1) {
            throw new InvalidChangeOperationException(
                "Base change not found: " + input.baseChange);
          }
          Change change = Iterables.getOnlyElement(changes);
          if (!rsrc.getControl().controlFor(change).isVisible(db.get())) {
            throw new InvalidChangeOperationException(
                "Base change not found: " + input.baseChange);
          }
          PatchSet ps = db.get().patchSets().get(
              new PatchSet.Id(change.getId(),
              change.currentPatchSetId().get()));
          parentCommit = ObjectId.fromString(ps.getRevision().get());
        } else {
          Ref destRef = git.getRef(refName);
          if (destRef == null) {
            throw new UnprocessableEntityException(String.format(
                "Branch %s does not exist.", refName));
          }
          parentCommit = destRef.getObjectId();
        }
        RevCommit mergeTip = rw.parseCommit(parentCommit);

        Timestamp now = TimeUtil.nowTs();
        IdentifiedUser me = (IdentifiedUser) userProvider.get();
        PersonIdent author = me.newCommitterIdent(now, serverTimeZone);

        ObjectId id = ChangeIdUtil.computeChangeId(mergeTip.getTree(),
            mergeTip, author, author, input.subject);
        String commitMessage = ChangeIdUtil.insertId(input.subject, id);

        RevCommit c = newCommit(git, rw, author, mergeTip, commitMessage);

        Change change = new Change(
            getChangeId(id, c),
            new Change.Id(db.get().nextChangeId()),
            me.getAccountId(),
            new Branch.NameKey(project, refName),
            now);

        ChangeInserter ins =
            changeInserterFactory.create(refControl, change, c);

        validateCommit(git, refControl, c, me, ins);
        updateRef(git, rw, c, change, ins.getPatchSet());

        change.setTopic(input.topic);
        ins.setDraft(input.status != null && input.status == ChangeStatus.DRAFT);
        ins.insert();

        return Response.created(json.format(change.getId()));
      } finally {
        rw.release();
      }
    } finally {
      git.close();
    }
  }

  private void validateCommit(Repository git, RefControl refControl,
      RevCommit c, IdentifiedUser me, ChangeInserter ins)
      throws InvalidChangeOperationException {
    PatchSet newPatchSet = ins.getPatchSet();
    CommitValidators commitValidators =
        commitValidatorsFactory.create(refControl, new NoSshInfo(), git);
    CommitReceivedEvent commitReceivedEvent =
        new CommitReceivedEvent(new ReceiveCommand(
            ObjectId.zeroId(),
            c.getId(),
            newPatchSet.getRefName()),
            refControl.getProjectControl().getProject(),
            refControl.getRefName(),
            c,
            me);

    try {
      commitValidators.validateForGerritCommits(commitReceivedEvent);
    } catch (CommitValidationException e) {
      throw new InvalidChangeOperationException(e.getMessage());
    }
  }

  private static void updateRef(Repository git, RevWalk rw, RevCommit c,
      Change change, PatchSet newPatchSet) throws IOException {
    RefUpdate ru = git.updateRef(newPatchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(c);
    ru.disableRefLog();
    if (ru.update(rw) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", newPatchSet.getRefName(),
          change.getDest().getParentKey().get(), ru.getResult()));
    }
  }

  private static Change.Key getChangeId(ObjectId id, RevCommit emptyCommit) {
    List<String> idList = emptyCommit.getFooterLines(
        FooterConstants.CHANGE_ID);
    Change.Key changeKey = !idList.isEmpty()
        ? new Change.Key(idList.get(idList.size() - 1).trim())
        : new Change.Key("I" + id.name());
    return changeKey;
  }

  private static RevCommit newCommit(Repository git, RevWalk rw,
      PersonIdent authorIdent, RevCommit mergeTip, String commitMessage)
      throws IOException {
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
    return emptyCommit;
  }

  private static ObjectId insert(ObjectInserter inserter,
      CommitBuilder commit) throws IOException,
      UnsupportedEncodingException {
    ObjectId id = inserter.insert(commit);
    inserter.flush();
    return id;
  }
}
