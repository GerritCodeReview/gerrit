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
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
  private final Provider<CurrentUser> user;
  private final ProjectsCollection projectsCollection;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeJson.Factory jsonFactory;
  private final ChangeUtil changeUtil;
  private final BatchUpdate.Factory updateFactory;
  private final boolean allowDrafts;

  @Inject
  CreateChange(Provider<ReviewDb> db,
      GitRepositoryManager gitManager,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<CurrentUser> user,
      ProjectsCollection projectsCollection,
      ChangeInserter.Factory changeInserterFactory,
      ChangeJson.Factory json,
      ChangeUtil changeUtil,
      BatchUpdate.Factory updateFactory,
      @GerritServerConfig Config config) {
    this.db = db;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.projectsCollection = projectsCollection;
    this.changeInserterFactory = changeInserterFactory;
    this.jsonFactory = json;
    this.changeUtil = changeUtil;
    this.updateFactory = updateFactory;
    this.allowDrafts = config.getBoolean("change", "allowDrafts", true);
  }

  @Override
  public Response<ChangeInfo> apply(TopLevelResource parent, ChangeInfo input)
      throws OrmException, IOException, InvalidChangeOperationException,
      RestApiException, UpdateException {
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
        throw new MethodNotAllowedException("draft workflow is disabled");
      }
    }

    String refName = RefNames.fullName(input.branch);
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
    try (Repository git = gitManager.openRepository(project);
        RevWalk rw = new RevWalk(git)) {
      ObjectId parentCommit;
      List<String> groups;
      if (input.baseChange != null) {
        List<ChangeControl> ctls = changeUtil.findChanges(
            input.baseChange, rsrc.getControl().getUser());
        if (ctls.size() != 1) {
          throw new InvalidChangeOperationException(
              "Base change not found: " + input.baseChange);
        }
        ChangeControl ctl = Iterables.getOnlyElement(ctls);
        if (!ctl.isVisible(db.get())) {
          throw new InvalidChangeOperationException(
              "Base change not found: " + input.baseChange);
        }
        PatchSet ps =
            db.get().patchSets().get(ctl.getChange().currentPatchSetId());
        parentCommit = ObjectId.fromString(ps.getRevision().get());
        groups = ps.getGroups();
      } else {
        Ref destRef = git.getRefDatabase().exactRef(refName);
        if (destRef == null) {
          throw new UnprocessableEntityException(String.format(
              "Branch %s does not exist.", refName));
        }
        parentCommit = destRef.getObjectId();
        groups = null;
      }
      RevCommit mergeTip = rw.parseCommit(parentCommit);

      Timestamp now = TimeUtil.nowTs();
      IdentifiedUser me = user.get().asIdentifiedUser();
      PersonIdent author = me.newCommitterIdent(now, serverTimeZone);

      ObjectId id = ChangeIdUtil.computeChangeId(mergeTip.getTree(),
          mergeTip, author, author, input.subject);
      String commitMessage = ChangeIdUtil.insertId(input.subject, id);

      try (ObjectInserter oi = git.newObjectInserter()) {
        RevCommit c = newCommit(oi, rw, author, mergeTip, commitMessage);

        Change change = new Change(
            getChangeId(id, c),
            new Change.Id(db.get().nextChangeId()),
            me.getAccountId(),
            new Branch.NameKey(project, refName),
            now);

        ChangeInserter ins = changeInserterFactory
            .create(refControl, change, c)
            .setValidatePolicy(CommitValidators.Policy.GERRIT);
        ins.setMessage(String.format("Uploaded patch set %s.",
            ins.getPatchSet().getPatchSetId()));
        String topic = input.topic;
        if (topic != null) {
          topic = Strings.emptyToNull(topic.trim());
        }
        change.setTopic(topic);
        ins.setDraft(input.status != null && input.status == ChangeStatus.DRAFT);
        ins.setGroups(groups);
        try (BatchUpdate bu = updateFactory.create(
            db.get(), change.getProject(), me, now)) {
          bu.setRepository(git, rw, oi);
          bu.insertChange(ins);
          bu.execute();
        }
        ChangeJson json = jsonFactory.create(ChangeJson.NO_OPTIONS);
        return Response.created(json.format(change.getId()));
      }

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

  private static RevCommit newCommit(ObjectInserter oi, RevWalk rw,
      PersonIdent authorIdent, RevCommit mergeTip, String commitMessage)
      throws IOException {
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(mergeTip.getTree().getId());
    commit.setParentId(mergeTip);
    commit.setAuthor(authorIdent);
    commit.setCommitter(authorIdent);
    commit.setMessage(commitMessage);
    return rw.parseCommit(insert(oi, commit));
  }

  private static ObjectId insert(ObjectInserter inserter,
      CommitBuilder commit) throws IOException,
      UnsupportedEncodingException {
    ObjectId id = inserter.insert(commit);
    inserter.flush();
    return id;
  }
}
