// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccountsSection;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.ConfiguredMimeTypes;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Mandatory project policy, independent of optional commit-validation listeners. */
@Singleton
public class SignedCommitPolicy {
  private final CommitSignatureVerifier verifier;
  private final ProjectState.Factory projectStateFactory;
  private final ProjectCache projectCache;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  SignedCommitPolicy(
      CommitSignatureVerifier verifier,
      ProjectState.Factory projectStateFactory,
      ProjectCache projectCache,
      ProjectConfig.Factory projectConfigFactory) {
    this.verifier = verifier;
    this.projectStateFactory = projectStateFactory;
    this.projectCache = projectCache;
    this.projectConfigFactory = projectConfigFactory;
  }

  public boolean isRequired(BranchNameKey branch) {
    return isContentBranch(branch.branch())
        && projectCache
            .get(branch.project())
            .orElseThrow(illegalState(branch.project()))
            .is(BooleanProjectConfig.REQUIRE_SIGNED_COMMITS);
  }

  /** Metadata, config, NoteDb and other non-branch refs are not content history. */
  public static boolean isContentBranch(String refName) {
    return refName.startsWith(Constants.R_HEADS);
  }

  /** Applies current content policy and checks effective policy enablement in config updates. */
  public void checkRefUpdate(Project.NameKey project, ObjectReader reader, ReceiveCommand command)
      throws IOException, ResourceConflictException {
    checkBranchUpdate(project, reader, command);
    if (RefNames.REFS_CONFIG.equals(command.getRefName())
        && !command.getNewId().equals(ObjectId.zeroId())
        && !command.getNewId().equals(command.getOldId())) {
      ProjectConfig config = projectConfigFactory.create(project);
      try (RevWalk walk = new RevWalk(reader)) {
        config.load(project, walk, command.getNewId());
        checkConfigurationChange(
            projectCache.get(project).orElseThrow(illegalState(project)), config.getCacheable());
      } catch (ConfigInvalidException e) {
        throw new ResourceConflictException("invalid project configuration", e);
      }
    }
  }

  /**
   * Checks all commits newly reachable from this destination, never excluding commits just because
   * another branch, tag, patch-set or edit ref already reaches them.
   */
  public void checkBranchUpdate(
      Project.NameKey project, ObjectReader reader, ReceiveCommand command)
      throws IOException, ResourceConflictException {
    if (!isContentBranch(command.getRefName())
        || command.getNewId().equals(ObjectId.zeroId())
        || command.getNewId().equals(command.getOldId())) {
      return;
    }
    BranchNameKey branch = BranchNameKey.create(project, command.getRefName());
    if (!isRequired(branch)) {
      return;
    }
    try (RevWalk walk = new RevWalk(reader)) {
      walk.markStart(walk.parseCommit(command.getNewId()));
      if (!command.getOldId().equals(ObjectId.zeroId())) {
        walk.markUninteresting(walk.parseCommit(command.getOldId()));
      }
      for (RevCommit commit; (commit = walk.next()) != null; ) {
        requireVerified(branch, commit, reader.open(commit, OBJ_COMMIT).getBytes());
      }
    }
  }

  /** Checks a review/replacement object, while permitting unchanged post-commit reviews. */
  public void checkReviewCommit(BranchNameKey branch, RepoView repoView, ObjectId commitId)
      throws IOException, ResourceConflictException {
    if (!isRequired(branch)) {
      return;
    }
    ObjectReader reader = repoView.getRevWalk().getObjectReader();
    // Grandfather only the existing destination history, not pending updates or other refs.
    ObjectId destination = repoView.getOriginalRef(branch.branch()).orElse(null);
    if (destination != null) {
      try (RevWalk walk = new RevWalk(reader)) {
        if (walk.isMergedInto(walk.parseCommit(commitId), walk.parseCommit(destination))) {
          return;
        }
      }
    }
    requireVerified(branch, commitId, reader.open(commitId, OBJ_COMMIT).getBytes());
  }

  /** Verifies a finalized generated payload before any commit object is inserted. */
  public void checkGeneratedCommit(BranchNameKey branch, byte[] rawCommit)
      throws IOException, ResourceConflictException {
    if (isRequired(branch)) {
      try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
        requireVerified(branch, formatter.idFor(OBJ_COMMIT, rawCommit), rawCommit);
      }
    }
  }

  /** Preflight for initial project content, before the repository or metadata refs are created. */
  public void checkInitialCommit(Project project, String refName, byte[] rawCommit)
      throws IOException, ResourceConflictException {
    if (isContentBranch(refName)
        && newProjectState(project).is(BooleanProjectConfig.REQUIRE_SIGNED_COMMITS)) {
      try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
        requireVerified(
            BranchNameKey.create(project.getNameKey(), refName),
            formatter.idFor(OBJ_COMMIT, rawCommit),
            rawCommit);
      }
    }
  }

  private void requireVerified(BranchNameKey branch, ObjectId commit, byte[] rawCommit)
      throws IOException, ResourceConflictException {
    if (!verifier.isEnabled()) {
      throw new ResourceConflictException(
          "project "
              + branch.project().get()
              + " requires verified OpenPGP commit signatures; commit signature verification is"
              + " unavailable");
    }
    CommitSignatureInfo result = verifier.verify(rawCommit);
    if (result.status != CommitSignatureInfo.Status.VERIFIED) {
      throw new ResourceConflictException(
          "commit "
              + commit.name()
              + " requires verified OpenPGP commit signatures ("
              + result.status
              + (result.reason != null ? ": " + result.reason : "")
              + ")");
    }
  }

  public void checkConfigurationChange(ProjectState before, CachedProjectConfig after)
      throws ResourceConflictException {
    if (!before.is(BooleanProjectConfig.REQUIRE_SIGNED_COMMITS)
        && projectStateFactory.create(after).is(BooleanProjectConfig.REQUIRE_SIGNED_COMMITS)) {
      requireAvailable();
    }
  }

  public void checkNewProject(Project project) throws ResourceConflictException {
    if (newProjectState(project).is(BooleanProjectConfig.REQUIRE_SIGNED_COMMITS)) {
      requireAvailable();
    }
  }

  private ProjectState newProjectState(Project project) {
    return projectStateFactory.create(
        CachedProjectConfig.builder()
            .setProject(project)
            .setAccountsSection(AccountsSection.create(ImmutableList.of()))
            .setMimeTypes(ConfiguredMimeTypes.create(ImmutableList.of()))
            .setMaxObjectSizeLimit(0)
            .setCheckReceivedObjects(true)
            .build());
  }

  private void requireAvailable() throws ResourceConflictException {
    if (!verifier.isEnabled()) {
      throw new ResourceConflictException(
          "requireSignedCommits requires available commit signature verification");
    }
  }
}
