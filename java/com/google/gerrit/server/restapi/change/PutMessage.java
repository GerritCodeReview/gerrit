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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.CommitMessageInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
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
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class PutMessage implements RestModifyView<ChangeResource, CommitMessageInput> {

  private final BatchUpdate.Factory updateFactory;
  private final GitRepositoryManager repositoryManager;
  private final Provider<CurrentUser> userProvider;
  private final ZoneId zoneId;
  private final PatchSetInserter.Factory psInserterFactory;
  private final PermissionBackend permissionBackend;
  private final PatchSetUtil psUtil;
  private final NotifyResolver notifyResolver;
  private final ProjectCache projectCache;

  @Inject
  PutMessage(
      BatchUpdate.Factory updateFactory,
      GitRepositoryManager repositoryManager,
      Provider<CurrentUser> userProvider,
      PatchSetInserter.Factory psInserterFactory,
      PermissionBackend permissionBackend,
      @GerritPersonIdent PersonIdent gerritIdent,
      PatchSetUtil psUtil,
      NotifyResolver notifyResolver,
      ProjectCache projectCache) {
    this.updateFactory = updateFactory;
    this.repositoryManager = repositoryManager;
    this.userProvider = userProvider;
    this.psInserterFactory = psInserterFactory;
    this.zoneId = gerritIdent.getZoneId();
    this.permissionBackend = permissionBackend;
    this.psUtil = psUtil;
    this.notifyResolver = notifyResolver;
    this.projectCache = projectCache;
  }

  @Override
  public Response<String> apply(ChangeResource resource, CommitMessageInput input)
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
    ChangeUtil.ensureChangeIdIsCorrect(
        projectCache
            .get(resource.getProject())
            .orElseThrow(illegalState(resource.getProject()))
            .is(BooleanProjectConfig.REQUIRE_CHANGE_ID),
        resource.getChange().getKey().get(),
        sanitizedCommitMessage);

    try (Repository repository = repositoryManager.openRepository(resource.getProject());
        RevWalk revWalk = new RevWalk(repository);
        ObjectInserter objectInserter = repository.newObjectInserter()) {
      RevCommit patchSetCommit = revWalk.parseCommit(ps.commitId());

      String currentCommitMessage = patchSetCommit.getFullMessage();
      if (input.message.equals(currentCommitMessage)) {
        throw new ResourceConflictException("new and existing commit message are the same");
      }

      Instant ts = TimeUtil.now();
      try (BatchUpdate bu =
          updateFactory.create(resource.getChange().getProject(), userProvider.get(), ts)) {
        // Ensure that BatchUpdate will update the same repo
        bu.setRepository(repository, new RevWalk(objectInserter.newReader()), objectInserter);

        PatchSet.Id psId = ChangeUtil.nextPatchSetId(repository, ps.id());
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
      Instant timestamp)
      throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(basePatchSetCommit.getTree());
    builder.setParentIds(basePatchSetCommit.getParents());
    builder.setAuthor(basePatchSetCommit.getAuthorIdent());
    builder.setCommitter(
        userProvider.get().asIdentifiedUser().newCommitterIdent(timestamp, zoneId));
    builder.setMessage(commitMessage);
    ObjectId newCommitId = objectInserter.insert(builder);
    objectInserter.flush();
    return newCommitId;
  }

  private void ensureCanEditCommitMessage(ChangeNotes changeNotes)
      throws AuthException, PermissionBackendException, ResourceConflictException {
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
      projectCache
          .get(changeNotes.getProjectName())
          .orElseThrow(illegalState(changeNotes.getProjectName()))
          .checkStatePermitsWrite();
    } catch (AuthException denied) {
      throw new AuthException("modifying commit message not permitted", denied);
    }
  }
}
