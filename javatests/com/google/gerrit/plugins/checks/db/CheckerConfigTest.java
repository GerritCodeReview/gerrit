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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.checks.testing.CheckerConfigSubject.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.common.truth.StringSubject;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
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
  private final Project.NameKey checkerRepository = new Project.NameKey("my-repo");
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

    CheckerConfig checkerConfig = loadChecker(checkerUuid);
    assertThat(checkerConfig).hasUuid(checkerUuid);
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

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasName(checkerName);
    assertThat(checkerConfig).configStringList("name").containsExactly(checkerName);
  }

  @Test
  public void nameOfCheckerUpdateOverridesCheckerCreation() throws Exception {
    String anotherName = "another-name";

    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setName(checkerName).build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setName(anotherName).build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasName(anotherName);
    assertThat(checkerConfig).configStringList("name").containsExactly(anotherName);
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
        CheckerCreation.builder()
            .setCheckerUuid(checkerUuid)
            .setName(checkerName)
            .setRepository(checkerRepository)
            .build();
    createChecker(checkerCreation);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasDescriptionThat().isEmpty();
    assertThat(checkerConfig).configStringList("description").isEmpty();
  }

  @Test
  public void specifiedDescriptionIsRespectedForNewChecker() throws Exception {
    String description = "This is a test checker.";

    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription(description).build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasDescriptionThat().value().isEqualTo(description);
    assertThat(checkerConfig).configStringList("description").containsExactly(description);
  }

  @Test
  public void emptyDescriptionForNewCheckerIsIgnored() throws Exception {
    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("").build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasDescriptionThat().isEmpty();
    assertThat(checkerConfig).configStringList("description").isEmpty();
  }

  @Test
  public void urlDefaultsToOptionalEmpty() throws Exception {
    CheckerCreation checkerCreation =
        CheckerCreation.builder()
            .setCheckerUuid(checkerUuid)
            .setName(checkerName)
            .setRepository(checkerRepository)
            .build();
    createChecker(checkerCreation);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasUrlThat().isEmpty();
    assertThat(checkerConfig).configStringList("url").isEmpty();
  }

  @Test
  public void specifiedUrlIsRespectedForNewChecker() throws Exception {
    String url = "http://example.com/my-checker";

    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setUrl(url).build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasUrlThat().value().isEqualTo(url);
    assertThat(checkerConfig).configStringList("url").containsExactly(url);
  }

  @Test
  public void emptyUrlForNewCheckerIsIgnored() throws Exception {
    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setUrl("").build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasUrlThat().isEmpty();
    assertThat(checkerConfig).configStringList("url").isEmpty();
  }

  @Test
  public void specifiedRepositoryIsRespectedForNewChecker() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setRepository(checkerRepository).build();
    createChecker(checkerCreation);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasRepository(checkerRepository);
    assertThat(checkerConfig)
        .configStringList("repository")
        .containsExactly(checkerRepository.get());
  }

  @Test
  public void repositoryOfCheckerUpdateOverridesCheckerCreation() throws Exception {
    Project.NameKey anotherRepository = new Project.NameKey("another-repo");

    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setRepository(checkerRepository).build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setRepository(anotherRepository).build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasRepository(anotherRepository);
    assertThat(checkerConfig)
        .configStringList("repository")
        .containsExactly(anotherRepository.get());
  }

  @Test
  public void repositoryOfNewCheckerMustNotBeEmpty() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setRepository(new Project.NameKey("")).build();
    CheckerConfig checkerConfig =
        CheckerConfig.createForNewChecker(projectName, repository, checkerCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage(
          String.format("Repository of the checker %s must be defined", checkerUuid));
      checkerConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void createdOnDefaultsToNow() throws Exception {
    // Git timestamps are only precise to the second.
    Timestamp testStart = TimeUtil.truncateToSecond(TimeUtil.nowTs());

    createArbitraryChecker(checkerUuid);
    CheckerConfig checkerConfig = loadChecker(checkerUuid);
    assertThat(checkerConfig).hasCreatedOnThat().isAtLeast(testStart);
  }

  @Test
  public void specifiedCreatedOnIsRespectedForNewChecker() throws Exception {
    Timestamp createdOn = toTimestamp(LocalDate.of(2017, Month.DECEMBER, 11).atTime(13, 44, 10));

    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setUpdatedOn(createdOn).build();
    createChecker(checkerCreation, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerCreation.getCheckerUuid());
    assertThat(checkerConfig).hasCreatedOnThat().isEqualTo(createdOn);
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
        CheckerCreation.builder()
            .setCheckerUuid(checkerUuid)
            .setName(checkerName)
            .setRepository(checkerRepository)
            .build();
    createChecker(checkerCreation);

    String newName = "new-name";
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setName(newName).build();
    updateChecker(checkerUuid, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerUuid);
    assertThat(checkerConfig).hasName(newName);
    assertThat(checkerConfig).configStringList("name").containsExactly(newName);

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

    CheckerConfig checkerConfig = loadChecker(checkerUuid);
    assertThat(checkerConfig).hasDescriptionThat().value().isEqualTo(newDescription);
    assertThat(checkerConfig).configStringList("description").containsExactly(newDescription);
  }

  @Test
  public void descriptionCanBeRemoved() throws Exception {
    createArbitraryChecker(checkerUuid);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("").build();
    CheckerConfig checkerConfig = updateChecker(checkerUuid, checkerUpdate);
    assertThat(checkerConfig).hasDescriptionThat().isEmpty();
    assertThat(checkerConfig).configStringList("description").isEmpty();
  }

  @Test
  public void urlCanBeUpdated() throws Exception {
    createArbitraryChecker(checkerUuid);
    String newUrl = "http://example.com/my-checker";

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setUrl(newUrl).build();
    updateChecker(checkerUuid, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerUuid);
    assertThat(checkerConfig).hasUrlThat().value().isEqualTo(newUrl);
    assertThat(checkerConfig).configStringList("url").containsExactly(newUrl);
  }

  @Test
  public void urlCanBeRemoved() throws Exception {
    createArbitraryChecker(checkerUuid);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setUrl("").build();
    CheckerConfig checkerConfig = updateChecker(checkerUuid, checkerUpdate);
    assertThat(checkerConfig).hasUrlThat().isEmpty();
    assertThat(checkerConfig).configStringList("url").isEmpty();
  }

  @Test
  public void repositoryCanBeUpdated() throws Exception {
    CheckerCreation checkerCreation =
        CheckerCreation.builder()
            .setCheckerUuid(checkerUuid)
            .setName(checkerName)
            .setRepository(checkerRepository)
            .build();
    createChecker(checkerCreation);

    Project.NameKey newRepository = new Project.NameKey("another-repo");
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setRepository(newRepository).build();
    updateChecker(checkerUuid, checkerUpdate);

    CheckerConfig checkerConfig = loadChecker(checkerUuid);
    assertThat(checkerConfig).hasRepository(newRepository);
    assertThat(checkerConfig).configStringList("repository").containsExactly(newRepository.get());

    assertThatCommitMessage(checkerUuid).isEqualTo("Update checker");
  }

  @Test
  public void repositoryCannotBeRemoved() throws Exception {
    createArbitraryChecker(checkerUuid);

    CheckerUpdate checkerUpdate =
        CheckerUpdate.builder().setRepository(new Project.NameKey("")).build();

    exception.expect(IOException.class);
    exception.expectMessage(
        String.format("Repository of the checker %s must be defined", checkerUuid));
    updateChecker(checkerUuid, checkerUpdate);
  }

  @Test
  public void createDisabledChecker() throws Exception {
    CheckerCreation checkerCreation = getPrefilledCheckerCreationBuilder().build();
    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setStatus(CheckerStatus.DISABLED).build();
    CheckerConfig checker = createChecker(checkerCreation, checkerUpdate);

    assertThat(checker).hasStatus(CheckerStatus.DISABLED);
    assertThat(checker).configStringList("status").containsExactly("disabled");
    assertThatCommitMessage(checkerUuid).isEqualTo("Create checker");
  }

  @Test
  public void updateStatusToSameStatus() throws Exception {
    CheckerConfig checker = createArbitraryChecker(checkerUuid);
    assertThat(checker).hasStatus(CheckerStatus.ENABLED);
    assertThat(checker).configStringList("status").containsExactly("enabled");

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setStatus(CheckerStatus.ENABLED).build();
    checker = updateChecker(checkerUuid, checkerUpdate);
    assertThat(checker).hasStatus(CheckerStatus.ENABLED);
    assertThat(checker).configStringList("status").containsExactly("enabled");
  }

  @Test
  public void disableAndReenable() throws Exception {
    createArbitraryChecker(checkerUuid);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setStatus(CheckerStatus.DISABLED).build();
    CheckerConfig checker = updateChecker(checkerUuid, checkerUpdate);
    assertThat(checker).hasStatus(CheckerStatus.DISABLED);
    assertThat(checker).configStringList("status").containsExactly("disabled");

    checkerUpdate = CheckerUpdate.builder().setStatus(CheckerStatus.ENABLED).build();
    checker = updateChecker(checkerUuid, checkerUpdate);
    assertThat(checker).hasStatus(CheckerStatus.ENABLED);
    assertThat(checker).configStringList("status").containsExactly("enabled");
  }

  @Test
  public void refStateIsCorrectlySet() throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid(checkerUuid).build();
    CheckerConfig newChecker = createChecker(checkerCreation);
    ObjectId expectedRefStateAfterCreation = getCheckerRefState(checkerUuid);
    assertThat(newChecker).hasRefStateThat().isEqualTo(expectedRefStateAfterCreation);

    CheckerConfig checker = loadChecker(checkerUuid);
    assertThat(checker).hasRefStateThat().isEqualTo(expectedRefStateAfterCreation);

    CheckerUpdate checkerUpdate = CheckerUpdate.builder().setDescription("A description.").build();
    CheckerConfig updatedChecker = updateChecker(checkerUuid, checkerUpdate);
    ObjectId expectedRefStateAfterUpdate = getCheckerRefState(checkerUuid);
    assertThat(expectedRefStateAfterUpdate).isNotEqualTo(expectedRefStateAfterCreation);
    assertThat(updatedChecker).hasRefStateThat().isEqualTo(expectedRefStateAfterUpdate);
  }

  private CheckerConfig createArbitraryChecker(String checkerUuid) throws Exception {
    CheckerCreation checkerCreation =
        getPrefilledCheckerCreationBuilder().setCheckerUuid(checkerUuid).build();
    return createChecker(checkerCreation);
  }

  private CheckerCreation.Builder getPrefilledCheckerCreationBuilder() {
    return CheckerCreation.builder()
        .setCheckerUuid(checkerUuid)
        .setName(checkerName)
        .setRepository(checkerRepository);
  }

  private CheckerConfig createChecker(CheckerCreation checkerCreation) throws Exception {
    CheckerConfig checkerConfig =
        CheckerConfig.createForNewChecker(projectName, repository, checkerCreation);
    commit(checkerConfig);
    return loadChecker(checkerCreation.getCheckerUuid());
  }

  private CheckerConfig createChecker(CheckerCreation checkerCreation, CheckerUpdate checkerUpdate)
      throws Exception {
    CheckerConfig checkerConfig =
        CheckerConfig.createForNewChecker(projectName, repository, checkerCreation);
    checkerConfig.setCheckerUpdate(checkerUpdate);
    commit(checkerConfig);
    return loadChecker(checkerCreation.getCheckerUuid());
  }

  private CheckerConfig updateChecker(String checkerUuid, CheckerUpdate checkerUpdate)
      throws Exception {
    CheckerConfig checkerConfig =
        CheckerConfig.loadForChecker(projectName, repository, checkerUuid);
    checkerConfig.setCheckerUpdate(checkerUpdate);
    commit(checkerConfig);
    return loadChecker(checkerUuid);
  }

  private CheckerConfig loadChecker(String uuid) throws Exception {
    return CheckerConfig.loadForChecker(projectName, repository, uuid);
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

  private static Timestamp toTimestamp(LocalDateTime localDateTime) {
    return Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }
}
