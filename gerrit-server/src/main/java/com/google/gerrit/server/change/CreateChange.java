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

import static org.eclipse.jgit.lib.Constants.SIGNED_OFF_BY_TAG;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CreateChange
    extends RetryingRestModifyView<TopLevelResource, ChangeInput, Response<ChangeInfo>> {
  private final String anonymousCowardName;
  private final Provider<ReviewDb> db;
  private final GitRepositoryManager gitManager;
  private final AccountCache accountCache;
  private final Sequences seq;
  private final TimeZone serverTimeZone;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final ProjectsCollection projectsCollection;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeJson.Factory jsonFactory;
  private final ChangeFinder changeFinder;
  private final PatchSetUtil psUtil;
  private final boolean allowDrafts;
  private final MergeUtil.Factory mergeUtilFactory;
  private final SubmitType submitType;
  private final NotifyUtil notifyUtil;

  @Inject
  CreateChange(
      @AnonymousCowardName String anonymousCowardName,
      Provider<ReviewDb> db,
      GitRepositoryManager gitManager,
      AccountCache accountCache,
      Sequences seq,
      @GerritPersonIdent PersonIdent myIdent,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      ProjectsCollection projectsCollection,
      ChangeInserter.Factory changeInserterFactory,
      ChangeJson.Factory json,
      ChangeFinder changeFinder,
      RetryHelper retryHelper,
      PatchSetUtil psUtil,
      @GerritServerConfig Config config,
      MergeUtil.Factory mergeUtilFactory,
      NotifyUtil notifyUtil) {
    super(retryHelper);
    this.anonymousCowardName = anonymousCowardName;
    this.db = db;
    this.gitManager = gitManager;
    this.accountCache = accountCache;
    this.seq = seq;
    this.serverTimeZone = myIdent.getTimeZone();
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.projectsCollection = projectsCollection;
    this.changeInserterFactory = changeInserterFactory;
    this.jsonFactory = json;
    this.changeFinder = changeFinder;
    this.psUtil = psUtil;
    this.allowDrafts = config.getBoolean("change", "allowDrafts", true);
    this.submitType = config.getEnum("project", null, "submitType", SubmitType.MERGE_IF_NECESSARY);
    this.mergeUtilFactory = mergeUtilFactory;
    this.notifyUtil = notifyUtil;
  }

  @Override
  protected Response<ChangeInfo> applyImpl(
      BatchUpdate.Factory updateFactory, TopLevelResource parent, ChangeInput input)
      throws OrmException, IOException, InvalidChangeOperationException, RestApiException,
          UpdateException, PermissionBackendException {
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
      if (input.status != ChangeStatus.NEW && input.status != ChangeStatus.DRAFT) {
        throw new BadRequestException("unsupported change status");
      }
      if (!allowDrafts && input.status == ChangeStatus.DRAFT) {
        throw new MethodNotAllowedException("draft workflow is disabled");
      }
    }

    ProjectResource rsrc = projectsCollection.parse(input.project);
    checkValidCLA(rsrc.getControl());

    Project.NameKey project = rsrc.getNameKey();
    String refName = RefNames.fullName(input.branch);
    permissionBackend.user(user).project(project).ref(refName).check(RefPermission.CREATE_CHANGE);

    try (Repository git = gitManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      ObjectId parentCommit;
      List<String> groups;
      if (input.baseChange != null) {
        List<ChangeControl> ctls = changeFinder.find(input.baseChange, rsrc.getControl().getUser());
        if (ctls.size() != 1) {
          throw new UnprocessableEntityException("Base change not found: " + input.baseChange);
        }
        ChangeControl ctl = Iterables.getOnlyElement(ctls);
        if (!ctl.isVisible(db.get())) {
          throw new UnprocessableEntityException("Base change not found: " + input.baseChange);
        }
        PatchSet ps = psUtil.current(db.get(), ctl.getNotes());
        parentCommit = ObjectId.fromString(ps.getRevision().get());
        groups = ps.getGroups();
      } else {
        Ref destRef = git.getRefDatabase().exactRef(refName);
        if (destRef != null) {
          if (Boolean.TRUE.equals(input.newBranch)) {
            throw new ResourceConflictException(
                String.format("Branch %s already exists.", refName));
          }
          parentCommit = destRef.getObjectId();
        } else {
          if (Boolean.TRUE.equals(input.newBranch)) {
            parentCommit = null;
          } else {
            throw new UnprocessableEntityException(
                String.format("Branch %s does not exist.", refName));
          }
        }
        groups = Collections.emptyList();
      }
      RevCommit mergeTip = parentCommit == null ? null : rw.parseCommit(parentCommit);

      Timestamp now = TimeUtil.nowTs();
      IdentifiedUser me = user.get().asIdentifiedUser();
      PersonIdent author = me.newCommitterIdent(now, serverTimeZone);
      AccountState account = accountCache.get(me.getAccountId());
      GeneralPreferencesInfo info = account.getAccount().getGeneralPreferencesInfo();

      ObjectId treeId = mergeTip == null ? emptyTreeId(oi) : mergeTip.getTree();
      ObjectId id = ChangeIdUtil.computeChangeId(treeId, mergeTip, author, author, input.subject);
      String commitMessage = ChangeIdUtil.insertId(input.subject, id);
      if (Boolean.TRUE.equals(info.signedOffBy)) {
        commitMessage +=
            String.format(
                "%s%s", SIGNED_OFF_BY_TAG, account.getAccount().getNameEmail(anonymousCowardName));
      }

      RevCommit c;
      if (input.merge != null) {
        // create a merge commit
        if (!(submitType.equals(SubmitType.MERGE_ALWAYS)
            || submitType.equals(SubmitType.MERGE_IF_NECESSARY))) {
          throw new BadRequestException("Submit type: " + submitType + " is not supported");
        }
        c =
            newMergeCommit(
                git, oi, rw, rsrc.getControl(), mergeTip, input.merge, author, commitMessage);
      } else {
        // create an empty commit
        c = newCommit(oi, rw, author, mergeTip, commitMessage);
      }

      Change.Id changeId = new Change.Id(seq.nextChangeId());
      ChangeInserter ins = changeInserterFactory.create(changeId, c, refName);
      ins.setMessage(String.format("Uploaded patch set %s.", ins.getPatchSetId().get()));
      String topic = input.topic;
      if (topic != null) {
        topic = Strings.emptyToNull(topic.trim());
      }
      ins.setTopic(topic);
      ins.setDraft(input.status == ChangeStatus.DRAFT);
      ins.setPrivate(input.isPrivate != null && input.isPrivate);
      ins.setWorkInProgress(input.workInProgress != null && input.workInProgress);
      ins.setGroups(groups);
      ins.setNotify(input.notify);
      ins.setAccountsToNotify(notifyUtil.resolveAccounts(input.notifyDetails));
      try (BatchUpdate bu = updateFactory.create(db.get(), project, me, now)) {
        bu.setRepository(git, rw, oi);
        bu.insertChange(ins);
        bu.execute();
      }
      ChangeJson json = jsonFactory.noOptions();
      return Response.created(json.format(ins.getChange()));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  private static RevCommit newCommit(
      ObjectInserter oi,
      RevWalk rw,
      PersonIdent authorIdent,
      RevCommit mergeTip,
      String commitMessage)
      throws IOException {
    CommitBuilder commit = new CommitBuilder();
    if (mergeTip == null) {
      commit.setTreeId(emptyTreeId(oi));
    } else {
      commit.setTreeId(mergeTip.getTree().getId());
      commit.setParentId(mergeTip);
    }
    commit.setAuthor(authorIdent);
    commit.setCommitter(authorIdent);
    commit.setMessage(commitMessage);
    return rw.parseCommit(insert(oi, commit));
  }

  private RevCommit newMergeCommit(
      Repository repo,
      ObjectInserter oi,
      RevWalk rw,
      ProjectControl projectControl,
      RevCommit mergeTip,
      MergeInput merge,
      PersonIdent authorIdent,
      String commitMessage)
      throws RestApiException, IOException {
    if (Strings.isNullOrEmpty(merge.source)) {
      throw new BadRequestException("merge.source must be non-empty");
    }

    RevCommit sourceCommit = MergeUtil.resolveCommit(repo, rw, merge.source);
    if (!projectControl.canReadCommit(db.get(), repo, sourceCommit)) {
      throw new BadRequestException("do not have read permission for: " + merge.source);
    }

    MergeUtil mergeUtil = mergeUtilFactory.create(projectControl.getProjectState());
    // default merge strategy from project settings
    String mergeStrategy =
        MoreObjects.firstNonNull(
            Strings.emptyToNull(merge.strategy), mergeUtil.mergeStrategyName());

    return MergeUtil.createMergeCommit(
        oi,
        repo.getConfig(),
        mergeTip,
        sourceCommit,
        mergeStrategy,
        authorIdent,
        commitMessage,
        rw);
  }

  private static ObjectId insert(ObjectInserter inserter, CommitBuilder commit)
      throws IOException, UnsupportedEncodingException {
    ObjectId id = inserter.insert(commit);
    inserter.flush();
    return id;
  }

  private static ObjectId emptyTreeId(ObjectInserter inserter) throws IOException {
    return inserter.insert(new TreeFormatter());
  }

  static void checkValidCLA(ProjectControl ctl) throws AuthException {
    Capable capable = ctl.canPushToAtLeastOneRef();
    if (capable != Capable.OK) {
      throw new AuthException(capable.getMessage());
    }
  }
}
