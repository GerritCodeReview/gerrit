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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.edit.tree.TreeCreator;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

/**
 * The implementation of {@link ChangeOperations}.
 *
 * <p>There is only one implementation of {@link ChangeOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class ChangeOperationsImpl implements ChangeOperations {
  private final Sequences seq;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchsetInserterFactory;
  private final GitRepositoryManager repositoryManager;
  private final AccountResolver resolver;
  private final IdentifiedUser.GenericFactory userFactory;
  private final PersonIdent serverIdent;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ProjectCache projectCache;
  private final ChangeFinder changeFinder;
  private final PerPatchsetOperationsImpl.Factory perPatchsetOperationsFactory;
  private final PerCommentOperationsImpl.Factory perCommentOperationsFactory;
  private final PerDraftCommentOperationsImpl.Factory perDraftCommentOperationsFactory;
  private final PerRobotCommentOperationsImpl.Factory perRobotCommentOperationsFactory;

  @Inject
  public ChangeOperationsImpl(
      Sequences seq,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchsetInserterFactory,
      GitRepositoryManager repositoryManager,
      AccountResolver resolver,
      IdentifiedUser.GenericFactory userFactory,
      @GerritPersonIdent PersonIdent serverIdent,
      BatchUpdate.Factory batchUpdateFactory,
      ProjectCache projectCache,
      ChangeFinder changeFinder,
      PerPatchsetOperationsImpl.Factory perPatchsetOperationsFactory,
      PerCommentOperationsImpl.Factory perCommentOperationsFactory,
      PerDraftCommentOperationsImpl.Factory perDraftCommentOperationsFactory,
      PerRobotCommentOperationsImpl.Factory perRobotCommentOperationsFactory) {
    this.seq = seq;
    this.changeInserterFactory = changeInserterFactory;
    this.patchsetInserterFactory = patchsetInserterFactory;
    this.repositoryManager = repositoryManager;
    this.resolver = resolver;
    this.userFactory = userFactory;
    this.serverIdent = serverIdent;
    this.batchUpdateFactory = batchUpdateFactory;
    this.projectCache = projectCache;
    this.changeFinder = changeFinder;
    this.perPatchsetOperationsFactory = perPatchsetOperationsFactory;
    this.perCommentOperationsFactory = perCommentOperationsFactory;
    this.perDraftCommentOperationsFactory = perDraftCommentOperationsFactory;
    this.perRobotCommentOperationsFactory = perRobotCommentOperationsFactory;
  }

  @Override
  public PerChangeOperations change(Change.Id changeId) {
    return new PerChangeOperationsImpl(changeId);
  }

  @Override
  public TestChangeCreation.Builder newChange() {
    return TestChangeCreation.builder(this::createChange);
  }

  private Change.Id createChange(TestChangeCreation changeCreation) throws Exception {
    try (RefUpdateContext ctx = openTestRefUpdateContext()) {
      Change.Id changeId = Change.id(seq.nextChangeId());
      Project.NameKey project = getTargetProject(changeCreation);

      try (Repository repository = repositoryManager.openRepository(project);
          ObjectInserter objectInserter = repository.newObjectInserter();
          RevWalk revWalk = new RevWalk(objectInserter.newReader())) {
        Instant now = TimeUtil.now();
        IdentifiedUser changeOwner = getChangeOwner(changeCreation);
        PersonIdent author = getAuthorIdent(now, changeCreation);
        PersonIdent committer = getCommitterIdent(now, changeCreation);
        ObjectId commitId =
            createCommit(repository, revWalk, objectInserter, changeCreation, author, committer);

        String refName = RefNames.fullName(changeCreation.branch());
        ChangeInserter inserter = getChangeInserter(changeId, refName, commitId);
        inserter.setGroups(getGroups(changeCreation));
        changeCreation.topic().ifPresent(t -> inserter.setTopic(t));
        inserter.setApprovals(changeCreation.approvals());

        try (BatchUpdate batchUpdate = batchUpdateFactory.create(project, changeOwner, now)) {
          batchUpdate.setRepository(repository, revWalk, objectInserter);
          batchUpdate.insertChange(inserter);
          batchUpdate.execute();
        }
        return changeId;
      }
    }
  }

  private Project.NameKey getTargetProject(TestChangeCreation changeCreation) {
    if (changeCreation.project().isPresent()) {
      return changeCreation.project().get();
    }

    return getArbitraryProject();
  }

  private Project.NameKey getArbitraryProject() {
    Project.NameKey allProjectsName = projectCache.getAllProjects().getNameKey();
    Project.NameKey allUsersName = projectCache.getAllUsers().getNameKey();
    Optional<Project.NameKey> arbitraryProject =
        projectCache.all().stream()
            .filter(
                name ->
                    !Objects.equals(name, allProjectsName) && !Objects.equals(name, allUsersName))
            .findFirst();
    checkState(
        arbitraryProject.isPresent(),
        "At least one repository must be available on the Gerrit server");
    return arbitraryProject.get();
  }

  private IdentifiedUser getChangeOwner(TestChangeCreation changeCreation)
      throws IOException, ConfigInvalidException {
    if (changeCreation.owner().isPresent()) {
      return userFactory.create(changeCreation.owner().get());
    }

    return getArbitraryUser();
  }

  private PersonIdent getAuthorIdent(Instant when, TestChangeCreation changeCreation)
      throws IOException, ConfigInvalidException {
    if (changeCreation.authorIdent().isPresent()) {
      return new PersonIdent(changeCreation.authorIdent().get(), when);
    }

    return (changeCreation.author().isPresent()
            ? userFactory.create(changeCreation.author().get())
            : getChangeOwner(changeCreation))
        .newCommitterIdent(when, serverIdent.getZoneId());
  }

  private PersonIdent getCommitterIdent(Instant when, TestChangeCreation changeCreation)
      throws IOException, ConfigInvalidException {
    if (changeCreation.committerIdent().isPresent()) {
      return new PersonIdent(changeCreation.committerIdent().get(), when);
    }

    return (changeCreation.committer().isPresent()
            ? userFactory.create(changeCreation.committer().get())
            : getChangeOwner(changeCreation))
        .newCommitterIdent(when, serverIdent.getZoneId());
  }

  private IdentifiedUser getArbitraryUser() throws ConfigInvalidException, IOException {
    ImmutableSet<Account.Id> foundAccounts = resolver.resolveIgnoreVisibility("").asIdSet();
    checkState(
        !foundAccounts.isEmpty(),
        "At least one user account must be available on the Gerrit server");
    return userFactory.create(foundAccounts.iterator().next());
  }

  private ObjectId createCommit(
      Repository repository,
      RevWalk revWalk,
      ObjectInserter objectInserter,
      TestChangeCreation changeCreation,
      PersonIdent author,
      PersonIdent committer)
      throws IOException, BadRequestException {
    ImmutableList<ObjectId> parentCommits = getParentCommits(repository, revWalk, changeCreation);
    TreeCreator treeCreator =
        getTreeCreator(objectInserter, parentCommits, changeCreation.mergeStrategy());
    ObjectId tree = createNewTree(repository, treeCreator, changeCreation.treeModifications());
    String commitMessage = correctCommitMessage(changeCreation.commitMessage());
    return createCommit(objectInserter, tree, parentCommits, author, committer, commitMessage);
  }

  private ImmutableList<String> getGroups(TestChangeCreation changeCreation) {
    return changeCreation
        .parents()
        .map(parents -> getGroups(parents))
        .orElseGet(() -> ImmutableList.of());
  }

  private ImmutableList<String> getGroups(ImmutableList<TestCommitIdentifier> parents) {
    return parents.stream()
        .map(parent -> getGroups(parent))
        .flatMap(groups -> groups.stream())
        .collect(toImmutableList());
  }

  private ImmutableList<String> getGroups(TestCommitIdentifier parentCommit) {
    switch (parentCommit.getKind()) {
      case BRANCH:
        return ImmutableList.of();
      case CHANGE_ID:
        return getGroupsFromChange(parentCommit.changeId());
      case COMMIT_SHA_1:
        return ImmutableList.of();
      case PATCHSET_ID:
        return getGroupsFromPatchset(parentCommit.patchsetId());
      default:
        throw new IllegalStateException(
            String.format("No parent behavior implemented for %s.", parentCommit.getKind()));
    }
  }

  private ImmutableList<String> getGroupsFromChange(Change.Id changeId) {
    Optional<ChangeNotes> changeNotes = changeFinder.findOne(changeId);

    if (changeNotes.isPresent() && changeNotes.get().getChange().isClosed()) {
      return ImmutableList.of();
    }

    return changeNotes
        .map(ChangeNotes::getCurrentPatchSet)
        .map(PatchSet::groups)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Change %s not found and hence can't be used as parent.", changeId)));
  }

  private ImmutableList<String> getGroupsFromPatchset(PatchSet.Id patchsetId) {
    Optional<ChangeNotes> changeNotes = changeFinder.findOne(patchsetId.changeId());

    if (changeNotes.isPresent() && changeNotes.get().getChange().isClosed()) {
      return ImmutableList.of();
    }

    return changeNotes
        .map(ChangeNotes::getPatchSets)
        .map(patchsets -> patchsets.get(patchsetId))
        .map(PatchSet::groups)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Patchset %s not found and hence can't be used as parent.", patchsetId)));
  }

  private ImmutableList<ObjectId> getParentCommits(
      Repository repository, RevWalk revWalk, TestChangeCreation changeCreation) {
    return changeCreation
        .parents()
        .map(parents -> resolveParents(repository, revWalk, parents))
        .orElseGet(() -> asImmutableList(getTip(repository, changeCreation.branch())));
  }

  private ImmutableList<ObjectId> resolveParents(
      Repository repository, RevWalk revWalk, ImmutableList<TestCommitIdentifier> parents) {
    return parents.stream()
        .map(parent -> resolveCommit(repository, revWalk, parent))
        .collect(toImmutableList());
  }

  private ObjectId resolveCommit(
      Repository repository, RevWalk revWalk, TestCommitIdentifier parentCommit) {
    switch (parentCommit.getKind()) {
      case BRANCH:
        return resolveBranchTip(repository, parentCommit.branch());
      case CHANGE_ID:
        return resolveChange(parentCommit.changeId());
      case COMMIT_SHA_1:
        return resolveCommitFromSha1(revWalk, parentCommit.commitSha1());
      case PATCHSET_ID:
        return resolvePatchset(parentCommit.patchsetId());
      default:
        throw new IllegalStateException(
            String.format("No parent behavior implemented for %s.", parentCommit.getKind()));
    }
  }

  private static ObjectId resolveBranchTip(Repository repository, String branchName) {
    return getTip(repository, branchName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Tip of branch %s not found and hence can't be used as parent.",
                        branchName)));
  }

  private static Optional<ObjectId> getTip(Repository repository, String branch) {
    try {
      Optional<Ref> ref = Optional.ofNullable(repository.findRef(branch));
      return ref.map(Ref::getObjectId);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private ObjectId resolveChange(Change.Id changeId) {
    Optional<ChangeNotes> changeNotes = changeFinder.findOne(changeId);
    return changeNotes
        .map(ChangeNotes::getCurrentPatchSet)
        .map(PatchSet::commitId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Change %s not found and hence can't be used as parent.", changeId)));
  }

  private static RevCommit resolveCommitFromSha1(RevWalk revWalk, ObjectId commitSha1) {
    try {
      return revWalk.parseCommit(commitSha1);
    } catch (Exception e) {
      throw new IllegalStateException(
          String.format("Commit %s not found and hence can't be used as parent/base.", commitSha1),
          e);
    }
  }

  private ObjectId resolvePatchset(PatchSet.Id patchsetId) {
    Optional<ChangeNotes> changeNotes = changeFinder.findOne(patchsetId.changeId());
    return changeNotes
        .map(ChangeNotes::getPatchSets)
        .map(patchsets -> patchsets.get(patchsetId))
        .map(PatchSet::commitId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Patchset %s not found and hence can't be used as parent.", patchsetId)));
  }

  private static <T> ImmutableList<T> asImmutableList(Optional<T> value) {
    return value.stream().collect(toImmutableList());
  }

  private static TreeCreator getTreeCreator(
      RevWalk revWalk, ObjectId customBaseCommit, ImmutableList<ObjectId> parentCommits) {
    RevCommit commit = resolveCommitFromSha1(revWalk, customBaseCommit);
    // Use actual parents; relevant for example when a file is restored (->
    // RestoreFileModification).
    return TreeCreator.basedOnTree(commit.getTree(), parentCommits);
  }

  private static TreeCreator getTreeCreator(
      ObjectInserter objectInserter,
      ImmutableList<ObjectId> parentCommits,
      MergeStrategy mergeStrategy) {
    if (parentCommits.isEmpty()) {
      return TreeCreator.basedOnEmptyTree();
    }
    ObjectId baseTreeId = merge(objectInserter, parentCommits, mergeStrategy);
    return TreeCreator.basedOnTree(baseTreeId, parentCommits);
  }

  private static ObjectId merge(
      ObjectInserter objectInserter,
      ImmutableList<ObjectId> parentCommits,
      MergeStrategy mergeStrategy) {
    try {
      Merger merger = mergeStrategy.newMerger(objectInserter, new Config());
      boolean mergeSuccessful = merger.merge(parentCommits.toArray(new AnyObjectId[0]));
      if (!mergeSuccessful) {
        throw new IllegalStateException(
            "Conflicts encountered while merging the specified parents. Use"
                + " mergeOfButBaseOnFirst() instead to avoid these conflicts and define any"
                + " other desired file contents with file().content().");
      }
      return merger.getResultTreeId();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Creating the merge commits of the specified parents failed for an unknown reason.", e);
    }
  }

  private static ObjectId createNewTree(
      Repository repository,
      TreeCreator treeCreator,
      ImmutableList<TreeModification> treeModifications)
      throws IOException {
    treeCreator.addTreeModifications(treeModifications);
    return treeCreator.createNewTreeAndGetId(repository);
  }

  private String correctCommitMessage(String desiredCommitMessage) throws BadRequestException {
    String commitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(desiredCommitMessage);

    if (ChangeIdUtil.indexOfChangeId(commitMessage, "\n") == -1) {
      ObjectId id = CommitMessageUtil.generateChangeId();
      commitMessage = ChangeIdUtil.insertId(commitMessage, id);
    }

    return commitMessage;
  }

  private ObjectId createCommit(
      ObjectInserter objectInserter,
      ObjectId tree,
      ImmutableList<ObjectId> parentCommitIds,
      PersonIdent author,
      PersonIdent committer,
      String commitMessage)
      throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(tree);
    builder.setParentIds(parentCommitIds);
    builder.setAuthor(author);
    builder.setCommitter(committer);
    builder.setMessage(commitMessage);
    ObjectId newCommitId = objectInserter.insert(builder);
    objectInserter.flush();
    return newCommitId;
  }

  private ChangeInserter getChangeInserter(Change.Id changeId, String refName, ObjectId commitId) {
    ChangeInserter inserter = changeInserterFactory.create(changeId, commitId, refName);
    inserter.setMessage(String.format("Uploaded patchset %d.", inserter.getPatchSetId().get()));
    return inserter;
  }

  private class PerChangeOperationsImpl implements PerChangeOperations {

    private final Change.Id changeId;

    public PerChangeOperationsImpl(Change.Id changeId) {
      this.changeId = changeId;
    }

    @Override
    public boolean exists() {
      return changeFinder.findOne(changeId).isPresent();
    }

    @Override
    public TestChange get() {
      return toTestChange(getChangeNotes().getChange());
    }

    private ChangeNotes getChangeNotes() {
      Optional<ChangeNotes> changeNotes = changeFinder.findOne(changeId);
      checkState(changeNotes.isPresent(), "Tried to get non-existing test change.");
      return changeNotes.get();
    }

    private TestChange toTestChange(Change change) {
      return TestChange.builder()
          .numericChangeId(change.getId())
          .changeId(change.getKey().get())
          .build();
    }

    @Override
    public TestPatchsetCreation.Builder newPatchset() {
      return TestPatchsetCreation.builder(this::createPatchset);
    }

    private PatchSet.Id createPatchset(TestPatchsetCreation patchsetCreation)
        throws IOException, RestApiException, UpdateException {
      try (RefUpdateContext ctx = openTestRefUpdateContext()) {
        ChangeNotes changeNotes = getChangeNotes();
        Project.NameKey project = changeNotes.getProjectName();
        try (Repository repository = repositoryManager.openRepository(project);
            ObjectInserter objectInserter = repository.newObjectInserter();
            RevWalk revWalk = new RevWalk(objectInserter.newReader())) {
          Instant now = TimeUtil.now();
          PersonIdent authorIdent = getAuthorIdent(now, patchsetCreation);
          PersonIdent committerIdent = getCommitterIdent(now, patchsetCreation);
          ObjectId newPatchsetCommit =
              createPatchsetCommit(
                  repository,
                  revWalk,
                  objectInserter,
                  changeNotes,
                  patchsetCreation,
                  authorIdent,
                  committerIdent,
                  now);

          PatchSet.Id patchsetId =
              ChangeUtil.nextPatchSetId(repository, changeNotes.getCurrentPatchSet().id());
          PatchSetInserter patchSetInserter =
              getPatchSetInserter(changeNotes, newPatchsetCommit, patchsetId);

          Account.Id uploaderId =
              patchsetCreation.uploader().orElse(changeNotes.getChange().getOwner());
          IdentifiedUser uploader = userFactory.create(uploaderId);
          try (BatchUpdate batchUpdate = batchUpdateFactory.create(project, uploader, now)) {
            batchUpdate.setRepository(repository, revWalk, objectInserter);
            batchUpdate.addOp(changeId, patchSetInserter);
            batchUpdate.execute();
          }
          return patchsetId;
        }
      }
    }

    @Nullable
    private PersonIdent getAuthorIdent(Instant when, TestPatchsetCreation patchsetCreation) {
      if (patchsetCreation.authorIdent().isPresent()) {
        return new PersonIdent(patchsetCreation.authorIdent().get(), when);
      }

      if (patchsetCreation.author().isPresent()) {
        return userFactory
            .create(patchsetCreation.author().get())
            .newCommitterIdent(when, serverIdent.getZoneId());
      }

      return null;
    }

    @Nullable
    private PersonIdent getCommitterIdent(Instant when, TestPatchsetCreation patchsetCreation) {
      if (patchsetCreation.committerIdent().isPresent()) {
        return new PersonIdent(patchsetCreation.committerIdent().get(), when);
      }

      if (patchsetCreation.committer().isPresent()) {
        return userFactory
            .create(patchsetCreation.committer().get())
            .newCommitterIdent(when, serverIdent.getZoneId());
      }

      return null;
    }

    private ObjectId createPatchsetCommit(
        Repository repository,
        RevWalk revWalk,
        ObjectInserter objectInserter,
        ChangeNotes changeNotes,
        TestPatchsetCreation patchsetCreation,
        @Nullable PersonIdent author,
        @Nullable PersonIdent committer,
        Instant now)
        throws IOException, BadRequestException {
      ObjectId oldPatchsetCommitId = changeNotes.getCurrentPatchSet().commitId();
      RevCommit oldPatchsetCommit = repository.parseCommit(oldPatchsetCommitId);

      ImmutableList<ObjectId> parentCommitIds =
          getParents(repository, revWalk, patchsetCreation, oldPatchsetCommit);
      TreeCreator treeCreator = getTreeCreator(revWalk, oldPatchsetCommit, parentCommitIds);
      ObjectId tree = createNewTree(repository, treeCreator, patchsetCreation.treeModifications());

      String commitMessage =
          correctCommitMessage(
              changeNotes.getChange().getKey().get(),
              patchsetCreation.commitMessage().orElseGet(oldPatchsetCommit::getFullMessage));

      return createCommit(
          objectInserter,
          tree,
          parentCommitIds,
          Optional.ofNullable(author).orElse(getAuthor(oldPatchsetCommit)),
          Optional.ofNullable(committer).orElse(getCommitter(oldPatchsetCommit, now)),
          commitMessage);
    }

    private String correctCommitMessage(String oldChangeId, String desiredCommitMessage)
        throws BadRequestException {
      String commitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(desiredCommitMessage);

      // Remove initial 'I' and treat the rest as ObjectId. This is not the cleanest approach but
      // unfortunately, we don't seem to have other utility code which takes the string-based
      // change-id and ensures that it is part of the commit message.
      ObjectId id = ObjectId.fromString(oldChangeId.substring(1));
      commitMessage = ChangeIdUtil.insertId(commitMessage, id, false);

      return commitMessage;
    }

    private PersonIdent getAuthor(RevCommit oldPatchsetCommit) {
      return Optional.ofNullable(oldPatchsetCommit.getAuthorIdent()).orElse(serverIdent);
    }

    private PersonIdent getCommitter(RevCommit oldPatchsetCommit, Instant now) {
      PersonIdent oldPatchsetCommitter =
          Optional.ofNullable(oldPatchsetCommit.getCommitterIdent()).orElse(serverIdent);
      if (asSeconds(now) == asSeconds(oldPatchsetCommitter.getWhenAsInstant())) {
        /* We need to ensure that the resulting commit SHA-1 is different from the old patchset.
         * In real situations, this automatically happens as two patchsets won't have exactly the
         * same commit timestamp even when the tree and commit message are the same. In tests,
         * we can easily end up with the same timestamp as Git uses second precision for timestamps.
         * We could of course require that tests must use TestTimeUtil#setClockStep but
         * that would be an unnecessary nuisance for test writers. Hence, go with a simple solution
         * here and simply add a second. */
        now = now.plusSeconds(1);
      }
      return new PersonIdent(oldPatchsetCommitter, now);
    }

    private long asSeconds(Instant date) {
      return date.getEpochSecond();
    }

    private ImmutableList<ObjectId> getParents(
        Repository repository,
        RevWalk revWalk,
        TestPatchsetCreation patchsetCreation,
        RevCommit oldPatchsetCommit) {
      return patchsetCreation
          .parents()
          .map(parents -> resolveParents(repository, revWalk, parents))
          .orElseGet(
              () -> Arrays.stream(oldPatchsetCommit.getParents()).collect(toImmutableList()));
    }

    private PatchSetInserter getPatchSetInserter(
        ChangeNotes changeNotes, ObjectId newPatchsetCommit, PatchSet.Id patchsetId) {
      PatchSetInserter patchSetInserter =
          patchsetInserterFactory.create(changeNotes, patchsetId, newPatchsetCommit);
      patchSetInserter.setCheckAddPatchSetPermission(false);
      patchSetInserter.setMessage(String.format("Uploaded patchset %d.", patchsetId.get()));
      return patchSetInserter;
    }

    @Override
    public PerPatchsetOperations patchset(PatchSet.Id patchsetId) {
      return perPatchsetOperationsFactory.create(getChangeNotes(), patchsetId);
    }

    @Override
    public PerPatchsetOperations currentPatchset() {
      ChangeNotes changeNotes = getChangeNotes();
      return perPatchsetOperationsFactory.create(
          changeNotes, changeNotes.getChange().currentPatchSetId());
    }

    @Override
    public PerCommentOperations comment(String commentUuid) {
      ChangeNotes changeNotes = getChangeNotes();
      return perCommentOperationsFactory.create(changeNotes, commentUuid);
    }

    @Override
    public PerDraftCommentOperations draftComment(String commentUuid) {
      ChangeNotes changeNotes = getChangeNotes();
      return perDraftCommentOperationsFactory.create(changeNotes, commentUuid);
    }

    @Override
    public PerRobotCommentOperations robotComment(String commentUuid) {
      ChangeNotes changeNotes = getChangeNotes();
      return perRobotCommentOperationsFactory.create(changeNotes, commentUuid);
    }
  }
}
