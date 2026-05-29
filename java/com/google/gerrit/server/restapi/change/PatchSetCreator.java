// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** A utility class for creating a patch set on an existing change. */
@Singleton
public class PatchSetCreator {
  private final Provider<IdentifiedUser> ident;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeJson.Factory jsonFactory;
  private final ZoneId serverZoneId;

  @Inject
  PatchSetCreator(
      Provider<IdentifiedUser> ident,
      BatchUpdate.Factory batchUpdateFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      @GerritPersonIdent PersonIdent myIdent,
      ChangeJson.Factory jsonFactory) {
    this.ident = ident;
    this.batchUpdateFactory = batchUpdateFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.serverZoneId = myIdent.getZoneId();
    this.jsonFactory = jsonFactory;
  }

  public ChangeInfo createPatchSetWithSuppliedTree(
      Project.NameKey project,
      ChangeData destChange,
      RevCommit latestPatchset,
      List<RevCommit> parents,
      @Nullable AccountInput author,
      List<ListChangesOption> outputOptions,
      Repository repo,
      ObjectInserter oi,
      CodeReviewRevWalk revWalk,
      ObjectId commitTree,
      String commitMessage,
      @Nullable List<String> groups,
      @Nullable ImmutableListMultimap<String, String> validationOptions)
      throws IOException, RestApiException, UpdateException {
    requireNonNull(destChange);
    requireNonNull(latestPatchset);
    requireNonNull(parents);
    requireNonNull(outputOptions);

    Instant now = TimeUtil.now();
    PersonIdent committerIdent =
        Optional.ofNullable(latestPatchset.getCommitterIdent())
            .map(
                id ->
                    ident
                        .get()
                        .newCommitterIdent(id.getEmailAddress(), now, serverZoneId)
                        .orElseGet(() -> ident.get().newCommitterIdent(now, serverZoneId)))
            .orElseGet(() -> ident.get().newCommitterIdent(now, serverZoneId));
    PersonIdent authorIdent =
        author == null
            ? committerIdent
            : new PersonIdent(author.name, author.email, now, serverZoneId);

    ObjectId appliedCommit =
        CommitUtil.createCommitWithTree(
            oi, authorIdent, committerIdent, parents, commitMessage, commitTree);
    CodeReviewCommit commit = revWalk.parseCommit(appliedCommit);
    oi.flush();

    Change resultChange;
    try (BatchUpdate bu = batchUpdateFactory.create(project, ident.get(), TimeUtil.now())) {
      bu.setRepository(repo, revWalk, oi);
      resultChange =
          insertPatchSet(
              bu,
              repo,
              patchSetInserterFactory,
              destChange.notes(),
              commit,
              groups,
              validationOptions);
    } catch (NoSuchChangeException | RepositoryNotFoundException e) {
      throw new ResourceConflictException(e.getMessage());
    }

    return jsonFactory.create(outputOptions).format(resultChange);
  }

  public void validateChangeCanBeAppended(@Nullable ChangeData destChange, BranchNameKey destBranch)
      throws PreconditionFailedException {
    if (destChange == null) {
      throw new PreconditionFailedException(
          "cannot write a patch set without a destination change.");
    }

    if (destChange.change().isClosed()) {
      throw new PreconditionFailedException(
          String.format(
              "patch:apply with Change-Id %s could not update the existing change %d "
                  + "in destination branch %s of project %s, because the change was closed (%s)",
              destChange.getId(),
              destChange.getId().get(),
              destBranch.branch(),
              destBranch.project(),
              destChange.change().getStatus().name()));
    }
  }

  private static Change insertPatchSet(
      BatchUpdate bu,
      Repository git,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeNotes destNotes,
      CodeReviewCommit commit,
      @Nullable List<String> groups,
      @Nullable ImmutableListMultimap<String, String> validationOptions)
      throws IOException, UpdateException, RestApiException {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      Change destChange = destNotes.getChange();
      PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
      PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, commit);
      inserter.setMessage(buildMessageForPatchSet(psId));
      if (groups != null) {
        inserter.setGroups(groups);
      }
      if (validationOptions != null) {
        inserter.setValidationOptions(validationOptions);
      }
      bu.addOp(destChange.getId(), inserter);
      bu.execute();
      return inserter.getChange();
    }
  }

  private static String buildMessageForPatchSet(PatchSet.Id psId) {
    return String.format("Uploaded patch set %s.", psId.get());
  }
}
