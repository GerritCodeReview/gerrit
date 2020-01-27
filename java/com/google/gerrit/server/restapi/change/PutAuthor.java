// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

// NOSUBMIT UIAction?
@Singleton
public class PutAuthor implements RestModifyView<ChangeResource, AccountInput> {

  PermissionBackend permissionBackend;
  Provider<CurrentUser> userProvider;
  GitRepositoryManager repositoryManager;
  BatchUpdate.Factory updateFactory;
  PatchSetUtil psUtil;
  PatchSetInserter.Factory psInserterFactory;

  @Inject
  PutAuthor(
      PermissionBackend permissionBackend,
      Provider<CurrentUser> userProvider,
      GitRepositoryManager repositoryManager,
      BatchUpdate.Factory updateFactory,
      PatchSetUtil psUtil,
      PatchSetInserter.Factory psInserterFactory) {
    this.permissionBackend = permissionBackend;
    this.userProvider = userProvider;
    this.repositoryManager = repositoryManager;
    this.updateFactory = updateFactory;
    this.psUtil = psUtil;
    this.psInserterFactory = psInserterFactory;
  }

  @Override
  public Response<String> apply(ChangeResource resource, AccountInput input)
      throws PermissionBackendException, IOException, ConfigInvalidException, RestApiException,
          UpdateException, ResourceConflictException {
    CurrentUser user = resource.getUser();

    PermissionBackend.ForRef perm =
        permissionBackend.user(user).ref(resource.getChange().getDest());
    perm.check(RefPermission.FORGE_AUTHOR);

    if (Strings.isNullOrEmpty(input.email)) {
      throw new BadRequestException("Need email address");
    }
    if (Strings.isNullOrEmpty(input.name)) {
      throw new BadRequestException("Need name");
    }
    PatchSet ps = psUtil.current(resource.getNotes());
    if (ps == null) {
      throw new ResourceConflictException("current revision is missing");
    }

    // NOSUBMIT - how much validatation should we do on the email format against JGit?
    // NOSUBMIT - we could fish out name/preferred email using account resolver?
    try (Repository repository = repositoryManager.openRepository(resource.getProject());
        RevWalk revWalk = new RevWalk(repository);
        ObjectInserter objectInserter = repository.newObjectInserter()) {
      RevCommit patchSetCommit = revWalk.parseCommit(ps.commitId());

      PersonIdent current = patchSetCommit.getAuthorIdent();
      if (current.getName().equals(input.name) && current.getEmailAddress().equals(input.email)) {
        return Response.ok("ok");
      }

      Timestamp ts = TimeUtil.nowTs();
      TimeZone tz = TimeZone.getDefault();

      Date now = new Date();
      //  NOSUBMIT: should we let this set date  + time? That would require a different input.
      PersonIdent newAuthor = new PersonIdent(input.name, input.email, now, tz);

      try (BatchUpdate bu =
          updateFactory.create(resource.getChange().getProject(), userProvider.get(), ts)) {
        // Ensure that BatchUpdate will update the same repo
        bu.setRepository(repository, new RevWalk(objectInserter.newReader()), objectInserter);

        PatchSet.Id psId = ChangeUtil.nextPatchSetId(repository, ps.id());

        CommitBuilder builder = new CommitBuilder();
        builder.setTreeId(patchSetCommit.getTree());
        builder.setParentIds(patchSetCommit.getParents());

        builder.setAuthor(newAuthor);
        // NOSUBMIT: it would be easy to also do provide SetCommitter. Useful?
        builder.setCommitter(userProvider.get().asIdentifiedUser().newCommitterIdent(now, tz));
        builder.setMessage(patchSetCommit.getFullMessage());
        ObjectId newCommitId = objectInserter.insert(builder);
        objectInserter.flush();

        PatchSetInserter inserter =
            psInserterFactory.create(resource.getNotes(), psId, newCommitId);
        inserter.setMessage(String.format("Patch Set %s: Author was updated.", psId.getId()));
        inserter.setDescription("Put author");
        // NOSUBMIT - PutMessage does notify handling. Should we do this here too?
        bu.addOp(resource.getChange().getId(), inserter);
        bu.execute();
      }
    }
    return Response.ok("ok");
  }
}
