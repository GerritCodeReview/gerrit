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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class Rebase
    implements RestModifyView<RevisionResource, RebaseInput>, UiAction<RevisionResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<ListChangesOption> OPTIONS =
      Sets.immutableEnumSet(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

  private final Provider<PersonIdent> serverIdent;
  private final BatchUpdate.Factory updateFactory;
  private final GitRepositoryManager repoManager;
  private final RebaseUtil rebaseUtil;
  private final ChangeJson.Factory json;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetUtil patchSetUtil;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeResource.Factory changeResourceFactory;

  @Inject
  public Rebase(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      BatchUpdate.Factory updateFactory,
      GitRepositoryManager repoManager,
      RebaseUtil rebaseUtil,
      ChangeJson.Factory json,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil patchSetUtil,
      IdentifiedUser.GenericFactory userFactory,
      ChangeResource.Factory changeResourceFactory) {
    this.serverIdent = serverIdent;
    this.updateFactory = updateFactory;
    this.repoManager = repoManager;
    this.rebaseUtil = rebaseUtil;
    this.json = json;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.patchSetUtil = patchSetUtil;
    this.userFactory = userFactory;
    this.changeResourceFactory = changeResourceFactory;
  }

  @Override
  public Response<ChangeInfo> apply(RevisionResource rsrc, RebaseInput input)
      throws UpdateException, RestApiException, IOException, PermissionBackendException {

    if (input.onBehalfOfUploader) {
      rsrc.permissions().check(ChangePermission.REBASE_ON_BEHALF_OF_UPLOADER);
      rsrc = onBehalfOf(rsrc, input);
    } else {
      rsrc.permissions().check(ChangePermission.REBASE);
    }

    projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .checkStatePermitsWrite();

    Change change = rsrc.getChange();
    try (Repository repo = repoManager.openRepository(change.getProject());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = CodeReviewCommit.newRevWalk(reader);
        BatchUpdate bu =
            updateFactory.create(change.getProject(), rsrc.getUser(), TimeUtil.now())) {
      rebaseUtil.verifyRebasePreconditions(rw, rsrc.getNotes(), rsrc.getPatchSet());

      RebaseChangeOp rebaseOp =
          rebaseUtil.getRebaseOp(
              rsrc,
              input,
              rebaseUtil.parseOrFindBaseRevision(repo, rw, permissionBackend, rsrc, input, true));

      // TODO(dborowitz): Why no notification? This seems wrong; dig up blame.
      bu.setNotify(NotifyResolver.Result.none());
      bu.setRepository(repo, rw, oi);
      bu.addOp(change.getId(), rebaseOp);
      bu.execute();

      ChangeInfo changeInfo = json.create(OPTIONS).format(change.getProject(), change.getId());
      changeInfo.containsGitConflicts =
          !rebaseOp.getRebasedCommit().getFilesWithGitConflicts().isEmpty() ? true : null;
      return Response.ok(changeInfo);
    }
  }

  private RevisionResource onBehalfOf(RevisionResource rsrc, RebaseInput rebaseInput)
      throws IOException, PermissionBackendException, BadRequestException,
          ResourceConflictException {
    if (rsrc.getPatchSet().id().get() != rsrc.getChange().currentPatchSetId().get()) {
      throw new BadRequestException(
          "non-current patch set cannot be rebased on behalf of the uploader");
    }
    if (rebaseInput.allowConflicts) {
      throw new BadRequestException(
          "allow_conflicts and on_behalf_of_uploader are mutually exclusive");
    }

    CurrentUser caller = rsrc.getUser();
    Account.Id uploaderId = rsrc.getPatchSet().uploader();
    IdentifiedUser uploader = userFactory.runAs(/*remotePeer= */ null, uploaderId, caller);
    logger.atFine().log(
        "%s is rebasing patch set %s of project %s on behalf of uploader %s",
        caller.getLoggableName(),
        rsrc.getPatchSet().id(),
        rsrc.getProject(),
        uploader.getLoggableName());

    checkPermissionForUploader(
        uploader,
        rsrc.getNotes(),
        ChangePermission.READ,
        String.format("uploader %s cannot read change", uploader.getLoggableName()));
    checkPermissionForUploader(
        uploader,
        rsrc.getNotes(),
        ChangePermission.ADD_PATCH_SET,
        String.format("uploader %s cannot add patch set", uploader.getLoggableName()));

    try (Repository repo = repoManager.openRepository(rsrc.getProject())) {
      RevCommit commit = repo.parseCommit(rsrc.getPatchSet().commitId());

      if (!uploader.hasEmailAddress(commit.getAuthorIdent().getEmailAddress())) {
        checkPermissionForUploader(
            uploader,
            rsrc.getNotes(),
            RefPermission.FORGE_AUTHOR,
            String.format(
                "author of patch set %d is forged and the uploader %s cannot forge author",
                rsrc.getPatchSet().id().get(), uploader.getLoggableName()));

        if (serverIdent.get().getEmailAddress().equals(commit.getAuthorIdent().getEmailAddress())) {
          checkPermissionForUploader(
              uploader,
              rsrc.getNotes(),
              RefPermission.FORGE_SERVER,
              String.format(
                  "author of patch set %d is the server identity and the uploader %s cannot forge the"
                      + " server identity",
                  rsrc.getPatchSet().id().get(), uploader.getLoggableName()));
        }
      }

      // If the committer of the patch set has been forged (the uploader doesn't own the committer
      // email), the FORGE_COMMITTER permission is not required for the uploader, since the rebased
      // patch set will have the uploader as the committer. Hence on the rebased patch set the
      // committer is no longer forged.
    }

    return new RevisionResource(
        changeResourceFactory.create(rsrc.getNotes(), uploader), rsrc.getPatchSet());
  }

  private void checkPermissionForUploader(
      IdentifiedUser uploader,
      ChangeNotes changeNotes,
      ChangePermission changePermission,
      String errorMessage)
      throws PermissionBackendException, ResourceConflictException {
    try {
      permissionBackend.user(uploader).change(changeNotes).check(changePermission);
    } catch (AuthException e) {
      throw new ResourceConflictException(errorMessage, e);
    }
  }

  private void checkPermissionForUploader(
      IdentifiedUser uploader,
      ChangeNotes changeNotes,
      RefPermission refPermission,
      String errorMessage)
      throws PermissionBackendException, ResourceConflictException {
    try {
      permissionBackend.user(uploader).ref(changeNotes.getChange().getDest()).check(refPermission);
    } catch (AuthException e) {
      throw new ResourceConflictException(errorMessage, e);
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) throws IOException {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Rebase")
            .setTitle(
                "Rebase onto tip of branch or parent change. Makes you the uploader of this "
                    + "change which can affect validity of approvals.")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (!(change.isNew() && rsrc.isCurrent())) {
      return description;
    }
    if (!projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .statePermitsWrite()) {
      return description;
    }
    if (patchSetUtil.isPatchSetLocked(rsrc.getNotes())) {
      return description;
    }

    boolean enabled = false;
    try (Repository repo = repoManager.openRepository(change.getDest().project());
        RevWalk rw = new RevWalk(repo)) {
      if (RebaseUtil.hasOneParent(rw, rsrc.getPatchSet())) {
        enabled = rebaseUtil.canRebase(rsrc.getPatchSet(), change.getDest(), repo, rw);
      }
    }

    boolean canRebase = rsrc.permissions().testOrFalse(ChangePermission.REBASE);
    boolean canRebaseOnBehalfOfUploader =
        rsrc.permissions().testOrFalse(ChangePermission.REBASE_ON_BEHALF_OF_UPLOADER);
    if (canRebase || canRebaseOnBehalfOfUploader) {
      return description
          .addOption("rebase", canRebase)
          .addOption("rebase_on_behalf_of_uploader", canRebaseOnBehalfOfUploader)
          .setEnabled(enabled)
          .setVisible(true);
    }

    return description;
  }

  public static class CurrentRevision implements RestModifyView<ChangeResource, RebaseInput> {
    private final PatchSetUtil psUtil;
    private final Rebase rebase;

    @Inject
    CurrentRevision(PatchSetUtil psUtil, Rebase rebase) {
      this.psUtil = psUtil;
      this.rebase = rebase;
    }

    @Override
    public Response<ChangeInfo> apply(ChangeResource rsrc, RebaseInput input)
        throws RestApiException, UpdateException, IOException, PermissionBackendException {
      PatchSet ps = psUtil.current(rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      }
      return Response.ok(rebase.apply(new RevisionResource(rsrc, ps), input).value());
    }
  }
}
