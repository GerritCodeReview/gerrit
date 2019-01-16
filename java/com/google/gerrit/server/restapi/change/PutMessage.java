// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.CommitMessageInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class PutMessage
    extends RetryingRestModifyView<ChangeResource, CommitMessageInput, Response<?>> {

  private final GitRepositoryManager repositoryManager;
  private final Provider<CurrentUser> userProvider;
  private final TimeZone tz;
  private final PatchSetInserter.Factory psInserterFactory;
  private final PermissionBackend permissionBackend;
  private final PatchSetUtil psUtil;
  private final NotifyResolver notifyResolver;
  private final ProjectCache projectCache;

  @Inject
  PutMessage(
      RetryHelper retryHelper,
      GitRepositoryManager repositoryManager,
      Provider<CurrentUser> userProvider,
      PatchSetInserter.Factory psInserterFactory,
      PermissionBackend permissionBackend,
      @GerritPersonIdent PersonIdent gerritIdent,
      PatchSetUtil psUtil,
      NotifyResolver notifyResolver,
      ProjectCache projectCache) {
    super(retryHelper);
    this.repositoryManager = repositoryManager;
    this.userProvider = userProvider;
    this.psInserterFactory = psInserterFactory;
    this.tz = gerritIdent.getTimeZone();
    this.permissionBackend = permissionBackend;
    this.psUtil = psUtil;
    this.notifyResolver = notifyResolver;
    this.projectCache = projectCache;
  }

  @Override
  protected Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource resource, CommitMessageInput input)
      throws IOException, RestApiException, UpdateException, PermissionBackendException,
          ConfigInvalidException {
    PatchSet ps = psUtil.current(resource.getNotes());
    if (ps == null) {
      throw new ResourceConflictException("current revision is missing");
    }

    if (input == null) {
      throw new BadRequestException("input cannot be null");
    }
    String sanitizedCommitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(input.message);

    ensureCanEditCommitMessage(resource.getNotes());
    ensureChangeIdIsCorrect(
        projectCache.checkedGet(resource.getProject()).is(BooleanProjectConfig.REQUIRE_CHANGE_ID),
        resource.getChange().getKey().get(),
        sanitizedCommitMessage);

    try (Repository repository = repositoryManager.openRepository(resource.getProject());
        RevWalk revWalk = new RevWalk(repository);
        ObjectInserter objectInserter = repository.newObjectInserter()) {
      RevCommit patchSetCommit = revWalk.parseCommit(ObjectId.fromString(ps.getRevision().get()));

      String currentCommitMessage = patchSetCommit.getFullMessage();
      if (input.message.equals(currentCommitMessage)) {
        throw new ResourceConflictException("new and existing commit message are the same");
      }

      Timestamp ts = TimeUtil.nowTs();
      try (BatchUpdate bu =
          updateFactory.create(resource.getChange().getProject(), userProvider.get(), ts)) {
        // Ensure that BatchUpdate will update the same repo
        bu.setRepository(repository, new RevWalk(objectInserter.newReader()), objectInserter);

        PatchSet.Id psId = ChangeUtil.nextPatchSetId(repository, ps.getId());
        ObjectId newCommit =
            createCommit(objectInserter, patchSetCommit, sanitizedCommitMessage, ts);
        PatchSetInserter inserter = psInserterFactory.create(resource.getNotes(), psId, newCommit);
        inserter.setMessage(
            String.format("Patch Set %s: Commit message was updated.", psId.getId()));
        inserter.setDescription("Edit commit message");
        bu.setNotify(resolveNotify(input, resource));
        bu.addOp(resource.getChange().getId(), inserter);
        bu.execute();
      }
    }
    return Response.ok("ok");
  }

  private NotifyResolver.Result resolveNotify(CommitMessageInput input, ChangeResource resource)
      throws BadRequestException, ConfigInvalidException, IOException {
    NotifyHandling notifyHandling = input.notify;
    if (notifyHandling == null) {
      notifyHandling =
          resource.getChange().isWorkInProgress() ? NotifyHandling.OWNER : NotifyHandling.ALL;
    }
    return notifyResolver.resolve(notifyHandling, input.notifyDetails);
  }

  private ObjectId createCommit(
      ObjectInserter objectInserter,
      RevCommit basePatchSetCommit,
      String commitMessage,
      Timestamp timestamp)
      throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(basePatchSetCommit.getTree());
    builder.setParentIds(basePatchSetCommit.getParents());
    builder.setAuthor(basePatchSetCommit.getAuthorIdent());
    builder.setCommitter(userProvider.get().asIdentifiedUser().newCommitterIdent(timestamp, tz));
    builder.setMessage(commitMessage);
    ObjectId newCommitId = objectInserter.insert(builder);
    objectInserter.flush();
    return newCommitId;
  }

  private void ensureCanEditCommitMessage(ChangeNotes changeNotes)
      throws AuthException, PermissionBackendException, IOException, ResourceConflictException {
    if (!userProvider.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    // Not allowed to put message if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(changeNotes);
    try {
      permissionBackend
          .user(userProvider.get())
          .change(changeNotes)
          .check(ChangePermission.ADD_PATCH_SET);
      projectCache.checkedGet(changeNotes.getProjectName()).checkStatePermitsWrite();
    } catch (AuthException denied) {
      throw new AuthException("modifying commit message not permitted", denied);
    }
  }

  private static void ensureChangeIdIsCorrect(
      boolean requireChangeId, String currentChangeId, String newCommitMessage)
      throws ResourceConflictException, BadRequestException {
    RevCommit revCommit =
        RevCommit.parse(
            Constants.encode("tree " + ObjectId.zeroId().name() + "\n\n" + newCommitMessage));

    // Check that the commit message without footers is not empty
    CommitMessageUtil.checkAndSanitizeCommitMessage(revCommit.getShortMessage());

    List<String> changeIdFooters = revCommit.getFooterLines(FooterConstants.CHANGE_ID);
    if (requireChangeId && changeIdFooters.isEmpty()) {
      throw new ResourceConflictException("missing Change-Id footer");
    }
    if (!changeIdFooters.isEmpty() && !changeIdFooters.get(0).equals(currentChangeId)) {
      throw new ResourceConflictException("wrong Change-Id footer");
    }
    if (changeIdFooters.size() > 1) {
      throw new ResourceConflictException("multiple Change-Id footers");
    }
  }
}
