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

package com.google.gerrit.server.change;

import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.UnchangedCommitMessageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;
import javax.inject.Inject;
import javax.inject.Provider;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

public class PutMessage
    extends RetryingRestModifyView<RevisionResource, PutMessage.Input, Response<?>> {

  public static class Input {
    @DefaultInput public String message;
  }

  private final GitRepositoryManager repositoryManager;
  private final Provider<CurrentUser> currentUserProvider;
  private final Provider<ReviewDb> db;
  private final TimeZone tz;
  private final PatchSetInserter.Factory psInserterFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  PutMessage(
      RetryHelper retryHelper,
      GitRepositoryManager repositoryManager,
      Provider<CurrentUser> currentUserProvider,
      Provider<ReviewDb> db,
      PatchSetInserter.Factory psInserterFactory,
      PermissionBackend permissionBackend,
      @GerritPersonIdent PersonIdent gerritIdent) {
    super(retryHelper);
    this.repositoryManager = repositoryManager;
    this.currentUserProvider = currentUserProvider;
    this.db = db;
    this.psInserterFactory = psInserterFactory;
    this.tz = gerritIdent.getTimeZone();
    this.permissionBackend = permissionBackend;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource resource, Input input)
      throws IOException, UnchangedCommitMessageException, RestApiException, UpdateException,
          PermissionBackendException {

    ensureCanEditCommitMessage(resource.getControl().getNotes());
    ensureChangeIdIsCorrect(resource.getChange().getKey().get(), input.message);

    try (Repository repository = repositoryManager.openRepository(resource.getProject());
        RevWalk revWalk = new RevWalk(repository)) {
      RevCommit patchSetCommit =
          revWalk.parseCommit(ObjectId.fromString(resource.getPatchSet().getRevision().get()));

      String currentCommitMessage = patchSetCommit.getFullMessage();
      if (input.equals(currentCommitMessage)) {
        throw new UnchangedCommitMessageException();
      }

      RevTree baseTree = patchSetCommit.getTree();
      Timestamp nowTimestamp = TimeUtil.nowTs();
      ObjectId newCommit =
          createCommit(repository, patchSetCommit, baseTree, input.message, nowTimestamp);

      try (BatchUpdate bu =
          updateFactory.create(
              db.get(), resource.getChange().getProject(), resource.getUser(), TimeUtil.nowTs())) {
        // Ensure that BatchUpdate will update the same repo
        ObjectInserter objectInserter = repository.newObjectInserter();
        bu.setRepository(repository, new RevWalk(objectInserter.newReader()), objectInserter);

        PatchSet.Id psId =
            ChangeUtil.nextPatchSetId(repository, resource.getChange().currentPatchSetId());
        PatchSetInserter inserter =
            psInserterFactory.create(resource.getControl(), psId, newCommit);
        inserter.setMessage(
            String.format("Patch Set %s: Commit message was updated.", psId.getId()));
        bu.addOp(resource.getChange().getId(), inserter);
        bu.execute();
      }
    }
    return Response.ok("");
  }

  private ObjectId createCommit(
      Repository repository,
      RevCommit basePatchSetCommit,
      ObjectId tree,
      String commitMessage,
      Timestamp timestamp)
      throws IOException {
    try (ObjectInserter objectInserter = repository.newObjectInserter()) {
      CommitBuilder builder = new CommitBuilder();
      builder.setTreeId(tree);
      builder.setParentIds(basePatchSetCommit.getParents());
      builder.setAuthor(basePatchSetCommit.getAuthorIdent());
      builder.setCommitter(
          currentUserProvider.get().asIdentifiedUser().newCommitterIdent(timestamp, tz));
      builder.setMessage(commitMessage);
      ObjectId newCommitId = objectInserter.insert(builder);
      objectInserter.flush();
      return newCommitId;
    }
  }

  private void ensureCanEditCommitMessage(ChangeNotes changeNotes)
      throws AuthException, PermissionBackendException {
    if (!currentUserProvider.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    try {
      permissionBackend
          .user(currentUserProvider.get())
          .database(db.get())
          .change(changeNotes)
          .check(ChangePermission.ADD_PATCH_SET);
    } catch (AuthException denied) {
      throw new AuthException("modifying commit message not permitted", denied);
    }
  }

  private static void ensureChangeIdIsCorrect(String currentChangeId, String newCommitMessage)
      throws ResourceConflictException {
    RevCommit revCommit =
        RevCommit.parse(
            Constants.encode("tree " + ObjectId.zeroId().name() + "\n\n" + newCommitMessage));
    List<String> changeIdFooters = revCommit.getFooterLines(FooterConstants.CHANGE_ID);
    if (changeIdFooters.isEmpty()) {
      throw new ResourceConflictException("missing Change-Id in commit message footer");
    }
    if (!changeIdFooters.get(0).equals(currentChangeId)) {
      throw new ResourceConflictException("wrong Change-Id in commit message footer");
    }
  }

  public static class CurrentRevision
      extends RetryingRestModifyView<ChangeResource, Input, Response<?>> {
    private final Provider<ReviewDb> dbProvider;
    private final PutMessage putMessage;
    private final PatchSetUtil psUtil;

    @Inject
    CurrentRevision(
        Provider<ReviewDb> dbProvider,
        RetryHelper retryHelper,
        PutMessage putMessage,
        PatchSetUtil psUtil) {
      super(retryHelper);
      this.dbProvider = dbProvider;
      this.putMessage = putMessage;
      this.psUtil = psUtil;
    }

    @Override
    protected Response<?> applyImpl(
        BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input input)
        throws RestApiException, IOException, OrmException, PermissionBackendException,
            UnchangedCommitMessageException, UpdateException {
      PatchSet ps = psUtil.current(dbProvider.get(), rsrc.getNotes());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }

      return putMessage.applyImpl(updateFactory, new RevisionResource(rsrc, ps), input);
    }
  }
}
