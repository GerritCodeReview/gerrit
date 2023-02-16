// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class ApplyPatch implements RestModifyView<ChangeResource, ApplyPatchPatchSetInput> {
  //  private final ChangeJson.Factory jsonFactory;
  //  private final ContributorAgreementsChecker contributorAgreements;
  //  private final Provider<IdentifiedUser> user;
  //  private final GitRepositoryManager gitManager;
  //  private final BatchUpdate.Factory batchUpdateFactory;
  //  private final PatchSetInserter.Factory patchSetInserterFactory;
  //  private final Provider<InternalChangeQuery> queryProvider;
  //  private final ZoneId serverZoneId;

  @Inject
  ApplyPatch(
      //      ChangeJson.Factory jsonFactory,
      //      ContributorAgreementsChecker contributorAgreements,
      //      Provider<IdentifiedUser> user,
      //      GitRepositoryManager gitManager,
      //      BatchUpdate.Factory batchUpdateFactory,
      //      PatchSetInserter.Factory patchSetInserterFactory,
      //      Provider<InternalChangeQuery> queryProvider,
      //      @GerritPersonIdent PersonIdent myIdent
      ) {
    //    this.jsonFactory = jsonFactory;
    //    this.contributorAgreements = contributorAgreements;
    //    this.user = user;
    //    this.gitManager = gitManager;
    //    this.batchUpdateFactory = batchUpdateFactory;
    //    this.patchSetInserterFactory = patchSetInserterFactory;
    //    this.queryProvider = queryProvider;
    //    this.serverZoneId = myIdent.getZoneId();
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchPatchSetInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException, InvalidChangeOperationException {
    return null;
  }

  public static ObjectId apply(
      Repository repo, RevCommit baseCommit, ApplyPatchInput input, PersonIdent serverIdent)
      throws Exception {

    try (
    // This inserter and revwalk *must* be passed to any BatchUpdates
    // created later on, to ensure the applied commit is flushed
    // before patch sets are updated.
    ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId treeId = ApplyPatchUtil.applyPatch(repo, oi, input, baseCommit);

      String commitMessage = "msg";

      ObjectId appliedCommit =
          CommitUtil.createCommitWithTree(
              oi, serverIdent, serverIdent, baseCommit, commitMessage, treeId);
      oi.flush();
      return appliedCommit;
    }
  }

  //  private static Change insertPatchSet(
  //      BatchUpdate bu,
  //      Repository git,
  //      PatchSetInserter.Factory patchSetInserterFactory,
  //      ChangeNotes destNotes,
  //      CodeReviewCommit commit)
  //      throws IOException, UpdateException, RestApiException {
  //    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
  //      Change destChange = destNotes.getChange();
  //      PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
  //      PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, commit);
  //      inserter.setMessage(buildMessageForPatchSet(psId));
  //      bu.addOp(destChange.getId(), inserter);
  //      bu.execute();
  //      return inserter.getChange();
  //    }
  //  }

  //  private static String buildMessageForPatchSet(PatchSet.Id psId) {
  //    return new StringBuilder(String.format("Uploaded patch set %s.", psId.get())).toString();
  //  }
}
