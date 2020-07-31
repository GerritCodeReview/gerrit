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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
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
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
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
      ChangeFinder changeFinder) {
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
    Change.Id changeId = Change.id(seq.nextChangeId());
    Project.NameKey project = getTargetProject(changeCreation);

    try (Repository repository = repositoryManager.openRepository(project);
        ObjectInserter objectInserter = repository.newObjectInserter();
        RevWalk revWalk = new RevWalk(objectInserter.newReader())) {
      Timestamp now = TimeUtil.nowTs();
      IdentifiedUser changeOwner = getChangeOwner(changeCreation);
      PersonIdent authorAndCommitter =
          changeOwner.newCommitterIdent(now, serverIdent.getTimeZone());
      ObjectId commitId =
          createCommit(repository, revWalk, objectInserter, changeCreation, authorAndCommitter);

      String refName = RefNames.fullName(changeCreation.branch());
      ChangeInserter inserter = getChangeInserter(changeId, refName, commitId);

      try (BatchUpdate batchUpdate = batchUpdateFactory.create(project, changeOwner, now)) {
        batchUpdate.setRepository(repository, revWalk, objectInserter);
        batchUpdate.insertChange(inserter);
        batchUpdate.execute();
      }
      return changeId;
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
      PersonIdent authorAndCommitter)
      throws IOException, BadRequestException {
    Optional<ObjectId> branchTip = getTip(repository, changeCreation.branch());

    ObjectId tree =
        createNewTree(
            repository,
            revWalk,
            branchTip.orElse(ObjectId.zeroId()),
            changeCreation.treeModifications());

    String commitMessage = correctCommitMessage(changeCreation.commitMessage());

    ImmutableList<ObjectId> parentCommitIds = Streams.stream(branchTip).collect(toImmutableList());
    return createCommit(
        objectInserter,
        tree,
        parentCommitIds,
        authorAndCommitter,
        authorAndCommitter,
        commitMessage);
  }

  private Optional<ObjectId> getTip(Repository repository, String branch) throws IOException {
    Optional<Ref> ref = Optional.ofNullable(repository.findRef(branch));
    return ref.map(Ref::getObjectId);
  }

  private static ObjectId createNewTree(
      Repository repository,
      RevWalk revWalk,
      ObjectId baseCommitId,
      ImmutableList<TreeModification> treeModifications)
      throws IOException {
    TreeCreator treeCreator = getTreeCreator(revWalk, baseCommitId);
    treeCreator.addTreeModifications(treeModifications);
    return treeCreator.createNewTreeAndGetId(repository);
  }

  private static TreeCreator getTreeCreator(RevWalk revWalk, ObjectId baseCommitId)
      throws IOException {
    if (ObjectId.zeroId().equals(baseCommitId)) {
      return TreeCreator.basedOnEmptyTree();
    }
    RevCommit baseCommit = revWalk.parseCommit(baseCommitId);
    return TreeCreator.basedOn(baseCommit);
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
      ChangeNotes changeNotes = getChangeNotes();
      Project.NameKey project = changeNotes.getProjectName();
      try (Repository repository = repositoryManager.openRepository(project);
          ObjectInserter objectInserter = repository.newObjectInserter();
          RevWalk revWalk = new RevWalk(objectInserter.newReader())) {
        Timestamp now = TimeUtil.nowTs();
        ObjectId newPatchsetCommit =
            createPatchsetCommit(
                repository, revWalk, objectInserter, changeNotes, patchsetCreation, now);

        PatchSet.Id patchsetId =
            ChangeUtil.nextPatchSetId(repository, changeNotes.getCurrentPatchSet().id());
        PatchSetInserter patchSetInserter =
            getPatchSetInserter(changeNotes, newPatchsetCommit, patchsetId);

        IdentifiedUser changeOwner = userFactory.create(changeNotes.getChange().getOwner());
        try (BatchUpdate batchUpdate = batchUpdateFactory.create(project, changeOwner, now)) {
          batchUpdate.setRepository(repository, revWalk, objectInserter);
          batchUpdate.addOp(changeId, patchSetInserter);
          batchUpdate.execute();
        }
        return patchsetId;
      }
    }

    private ObjectId createPatchsetCommit(
        Repository repository,
        RevWalk revWalk,
        ObjectInserter objectInserter,
        ChangeNotes changeNotes,
        TestPatchsetCreation patchsetCreation,
        Timestamp now)
        throws IOException {
      ObjectId oldPatchsetCommitId = changeNotes.getCurrentPatchSet().commitId();
      RevCommit oldPatchsetCommit = repository.parseCommit(oldPatchsetCommitId);

      ObjectId tree =
          createNewTree(
              repository, revWalk, oldPatchsetCommitId, patchsetCreation.treeModifications());

      String commitMessage = oldPatchsetCommit.getFullMessage();

      ImmutableList<ObjectId> parentCommitIds = getParents(oldPatchsetCommit);
      PersonIdent author = getAuthor(oldPatchsetCommit);
      PersonIdent committer = getCommitter(oldPatchsetCommit, now);
      return createCommit(objectInserter, tree, parentCommitIds, author, committer, commitMessage);
    }

    private PersonIdent getAuthor(RevCommit oldPatchsetCommit) {
      return Optional.ofNullable(oldPatchsetCommit.getAuthorIdent()).orElse(serverIdent);
    }

    private PersonIdent getCommitter(RevCommit oldPatchsetCommit, Timestamp now) {
      PersonIdent oldPatchsetCommitter =
          Optional.ofNullable(oldPatchsetCommit.getCommitterIdent()).orElse(serverIdent);
      if (asSeconds(now) == asSeconds(oldPatchsetCommitter.getWhen())) {
        /* We need to ensure that the resulting commit SHA-1 is different from the old patchset.
         * In real situations, this automatically happens as two patchsets won't have exactly the
         * same commit timestamp even when the tree and commit message are the same. In tests,
         * we can easily end up with the same timestamp as Git uses second precision for timestamps.
         * We could of course require that tests must use TestTimeUtil#setClockStep but
         * that would be an unnecessary nuisance for test writers. Hence, go with a simple solution
         * here and simply add a second. */
        now = Timestamp.from(now.toInstant().plusSeconds(1));
      }
      return new PersonIdent(oldPatchsetCommitter, now);
    }

    private long asSeconds(Date date) {
      return date.getTime() / 1000;
    }

    private ImmutableList<ObjectId> getParents(RevCommit oldPatchsetCommit) {
      return Arrays.stream(oldPatchsetCommit.getParents())
          .map(ObjectId::toObjectId)
          .collect(toImmutableList());
    }

    private PatchSetInserter getPatchSetInserter(
        ChangeNotes changeNotes, ObjectId newPatchsetCommit, PatchSet.Id patchsetId) {
      PatchSetInserter patchSetInserter =
          patchsetInserterFactory.create(changeNotes, patchsetId, newPatchsetCommit);
      patchSetInserter.setCheckAddPatchSetPermission(false);
      patchSetInserter.setMessage(String.format("Uploaded patchset %d.", patchsetId.get()));
      return patchSetInserter;
    }
  }
}
