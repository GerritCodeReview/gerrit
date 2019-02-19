// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checkers.db;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.common.truth.StringSubject;
import com.google.gerrit.plugins.checkers.Checker;
import com.google.gerrit.plugins.checkers.CheckerCreation;
import com.google.gerrit.plugins.checkers.CheckerRef;
import com.google.gerrit.plugins.checkers.CheckerUpdate;
import com.google.gerrit.plugins.checkers.CheckerUuid;
import com.google.gerrit.plugins.checkers.testing.CheckerSubject;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.truth.OptionalSubject;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class CheckerConfigTest extends GerritBaseTests {
  private Project.NameKey projectName;
  private Repository repository;
  private TestRepository<?> testRepository;

  private final String checkerName = "my-checker";
  private final String checkerUuid = CheckerUuid.make(checkerName);
  private final TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

  @Before
  public void setUp() throws Exception {
    projectName = new Project.NameKey("Test Repository");
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
    testRepository = new TestRepository<>(repository);
  }

  @Test
  public void correctCommitMessageForCheckerCreation() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid(checkerUuid).build();
    createChecker(checkerCreation);
    assertThatCommitMessage(checkerUuid).isEqualTo("Create checker");
  }

  @Test
  public void specifiedCheckerUuidIsRespectedForNewChecker() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid(checkerUuid).build();
    createChecker(checkerCreation);

    Optional<Checker> checker = loadChecker(checkerUuid);
    assertThatChecker(checker).value().hasUuid(checkerUuid);
  }

  @Test
  public void invalidCheckerUuidIsRejectedForNewChecker() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid("not-a-SHA1").build();

    exception.expect(IllegalStateException.class);
    exception.expectMessage("invalid checker UUID");
    createChecker(checkerCreation);
  }

  @Test
  public void specifiedNameIsRespectedForNewChecker() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setName(checkerName).build();
    createChecker(checkerCreation);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasName(checkerName);
  }

  @Test
  public void nameOfCheckerUpdateOverridesCheckerCreation() throws Exception {
    String anotherName = "another-name";

    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setName(checkerName).build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setName(anotherName).build();
    createChecker(checkerCreation, checkerUpdate);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasName(anotherName);
  }

  @Test
  public void nameOfNewCheckerMustNotBeEmpty() throws Exception {
    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().setName("").build();
    CheckerConfig checkerConfig =
        CheckerConfig.createForNewChecker(projectName, repository, checkerCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage(String.format("Name of the checker %s must be defined", checkerUuid));
      checkerConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void descriptionDefaultsToOptionalEmpty() throws Exception {
    CheckerCreation checkerCreation =
        CheckerCreation.builder().setCheckerUuid(checkerUuid).setName(checkerName).build();
    createChecker(checkerCreation);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasDescriptionThat().isEmpty();
  }

  @Test
  public void specifiedDescriptionIsRespectedForNewChecker() throws Exception {
    String description = "This is a test checker.";

    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription(description).build();
    createChecker(checkerCreation, checkerUpdate);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasDescriptionThat().value().isEqualTo(description);
  }

  @Test
  public void emptyDescriptionForNewCheckerIsIgnored() throws Exception {
    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("").build();
    createChecker(checkerCreation, checkerUpdate);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasDescriptionThat().isEmpty();
  }

  @Test
  public void createdOnDefaultsToNow() throws Exception {
    // Git timestamps are only precise to the second.
    Timestamp testStart = TimeUtil.truncateToSecond(TimeUtil.nowTs());

    CheckerCreation checkerCreation =
        CheckerCreation.builder().setCheckerUuid(checkerUuid).setName(checkerName).build();
    createChecker(checkerCreation);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasCreatedOnThat().isAtLeast(testStart);
  }

  @Test
  public void specifiedCreatedOnIsRespectedForNewChecker() throws Exception {
    Timestamp createdOn = toTimestamp(LocalDate.of(2017, Month.DECEMBER, 11).atTime(13, 44, 10));

    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setUpdatedOn(createdOn).build();
    createChecker(checkerCreation, checkerUpdate);

    Optional<Checker> checker = loadChecker(checkerCreation.getCheckerUuid());
    assertThatChecker(checker).value().hasCreatedOnThat().isEqualTo(createdOn);
  }

  @Test
  public void nameInConfigMayNotBeUndefined() throws Exception {
    populateCheckerConfig(checkerUuid, "[checker]");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage(String.format("name of checker %s not set", checkerUuid));
    loadChecker(checkerUuid);
  }

  @Test
  public void correctCommitMessageForCheckerUpdate() throws Exception {
    createArbitraryChecker(checkerUuid);
    assertThatCommitMessage(checkerUuid).isEqualTo("Create checker");

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("A description.").build();
    updateChecker(checkerUuid, checkerUpdate);
    assertThatCommitMessage(checkerUuid).isEqualTo("Update checker");
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    CheckerCreation checkerCreation =
        CheckerCreation.builder().setCheckerUuid(checkerUuid).setName(checkerName).build();
    createChecker(checkerCreation);

    String newName = "new-name";
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setName(newName).build();
    updateChecker(checkerUuid, checkerUpdate);

    Optional<Checker> checker = loadChecker(checkerUuid);
    assertThatChecker(checker).value().hasName(newName);

    assertThatCommitMessage(checkerUuid)
        .isEqualTo("Update checker\n\nRename from " + checkerName + " to " + newName);
  }

  @Test
  public void nameCannotBeRemoved() throws Exception {
    createArbitraryChecker(checkerUuid);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setName("").build();

    exception.expect(IOException.class);
    exception.expectMessage(String.format("Name of the checker %s must be defined", checkerUuid));
    updateChecker(checkerUuid, checkerUpdate);
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    createArbitraryChecker(checkerUuid);
    String newDescription = "New description";

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription(newDescription).build();
    updateChecker(checkerUuid, checkerUpdate);

    Optional<Checker> checker = loadChecker(checkerUuid);
    assertThatChecker(checker).value().hasDescriptionThat().value().isEqualTo(newDescription);
  }

  @Test
  public void descriptionCanBeRemoved() throws Exception {
    createArbitraryChecker(checkerUuid);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("").build();
    Optional<Checker> checker = updateChecker(checkerUuid, checkerUpdate);

    assertThatChecker(checker).value().hasDescriptionThat().isEmpty();
  }

  @Test
  public void refStateIsCorrectlySet() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid(checkerUuid).build();
    Optional<Checker> newChecker = createChecker(checkerCreation);
    ObjectId expectedRefStateAfterCreation = getCheckerRefState(checkerUuid);
    assertThatChecker(newChecker)
        .value()
        .hasRefStateThat()
        .isEqualTo(expectedRefStateAfterCreation);

    Optional<Checker> loadedChecker = loadChecker(checkerUuid);
    assertThatChecker(loadedChecker)
        .value()
        .hasRefStateThat()
        .isEqualTo(expectedRefStateAfterCreation);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("A description.").build();
    Optional<Checker> updatedChecker = updateChecker(checkerUuid, checkerUpdate);
    ObjectId expectedRefStateAfterUpdate = getCheckerRefState(checkerUuid);
    assertThat(expectedRefStateAfterUpdate).isNotEqualTo(expectedRefStateAfterCreation);
    assertThatChecker(updatedChecker)
        .value()
        .hasRefStateThat()
        .isEqualTo(expectedRefStateAfterUpdate);
  }

  private void createArbitraryChecker(String checkerUuid) throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid(checkerUuid).build();
    createChecker(checkerCreation);
  }

  private CheckerCreation.Builder getPrefilledCheckerCreationBuilder() {
    return CheckerCreation.builder().setCheckerUuid(checkerUuid).setName(checkerName);
  }

  private Optional<Checker> createChecker(CheckerCreation checkerCreation) throws Exception {
    CheckerConfig checkerConfig =
        CheckerConfig.createForNewChecker(projectName, repository, checkerCreation);
    commit(checkerConfig);
    return checkerConfig.getLoadedChecker();
  }

  private Optional<Checker> createChecker(
      CheckerCreation checkerCreation, CheckerUpdate checkerUpdate) throws Exception {
    CheckerConfig checkerConfig =
        CheckerConfig.createForNewChecker(projectName, repository, checkerCreation);
    checkerConfig.setCheckerUpdate(checkerUpdate);
    commit(checkerConfig);
    return checkerConfig.getLoadedChecker();
  }

  private Optional<Checker> updateChecker(String checkerUuid, CheckerUpdate checkerUpdate)
      throws Exception {
    CheckerConfig checkerConfig =
        CheckerConfig.loadForChecker(projectName, repository, checkerUuid);
    checkerConfig.setCheckerUpdate(checkerUpdate);
    commit(checkerConfig);
    return checkerConfig.getLoadedChecker();
  }

  private Optional<Checker> loadChecker(String uuid) throws Exception {
    CheckerConfig checkerConfig = CheckerConfig.loadForChecker(projectName, repository, uuid);
    return checkerConfig.getLoadedChecker();
  }

  private void commit(CheckerConfig checkerConfig) throws IOException {
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      checkerConfig.commit(metaDataUpdate);
    }
  }

  private MetaDataUpdate createMetaDataUpdate() {
    PersonIdent serverIdent =
        new PersonIdent(
            "Gerrit Server", "noreply@gerritcodereview.com", TimeUtil.nowTs(), timeZone);

    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(
            GitReferenceUpdated.DISABLED, new Project.NameKey("Test Repository"), repository);
    metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
    metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
    return metaDataUpdate;
  }

  private void populateCheckerConfig(String uuid, String fileContent) throws Exception {
    testRepository
        .branch(CheckerRef.refsCheckers(uuid))
        .commit()
        .message("Prepopulate checker.config")
        .add(CheckerConfig.CHECKER_CONFIG_FILE, fileContent)
        .create();
  }

  private ObjectId getCheckerRefState(String checkerUuid) throws IOException {
    return repository.exactRef(CheckerRef.refsCheckers(checkerUuid)).getObjectId();
  }

  private StringSubject assertThatCommitMessage(String checkerUuid) throws IOException {
    try (RevWalk rw = new RevWalk(repository)) {
      RevCommit commit = rw.parseCommit(getCheckerRefState(checkerUuid));
      return assertThat(commit.getFullMessage()).named("commit message");
    }
  }

  private static OptionalSubject<CheckerSubject, Checker> assertThatChecker(
      Optional<Checker> loadedChecker) {
    return assertThat(loadedChecker, CheckerSubject::assertThat);
  }

  private static Timestamp toTimestamp(LocalDateTime localDateTime) {
    return Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }
}
