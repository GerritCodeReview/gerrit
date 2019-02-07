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

package com.google.gerrit.server.verifier.db;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.common.truth.StringSubject;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierCreation;
import com.google.gerrit.server.verifier.VerifierUpdate;
import com.google.gerrit.server.verifier.VerifierUuid;
import com.google.gerrit.server.verifier.testing.VerifierSubject;
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

public class VerifierConfigTest extends GerritBaseTests {
  private Project.NameKey projectName;
  private Repository repository;
  private TestRepository<?> testRepository;

  private final String verifierName = "my-verifier";
  private final String verifierUuid = VerifierUuid.make(verifierName);
  private final Project.NameKey verifierRepository = new Project.NameKey("my-repo");
  private final TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

  @Before
  public void setUp() throws Exception {
    projectName = new Project.NameKey("Test Repository");
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
    testRepository = new TestRepository<>(repository);
  }

  @Test
  public void correctCommitMessageForVerifierCreation() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setVerifierUuid(verifierUuid).build();
    createVerifier(verifierCreation);
    assertThatCommitMessage(verifierUuid).isEqualTo("Create verifier");
  }

  @Test
  public void specifiedVerifierUuidIsRespectedForNewVerifier() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setVerifierUuid(verifierUuid).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().hasUuid(verifierUuid);
  }

  @Test
  public void invalidVerifierUuidIsRejectedForNewVerifier() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setVerifierUuid("not-a-SHA1").build();

    exception.expect(IllegalStateException.class);
    exception.expectMessage("invalid verifier UUID");
    createVerifier(verifierCreation);
  }

  @Test
  public void specifiedNameIsRespectedForNewVerifier() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setName(verifierName).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasName(verifierName);
  }

  @Test
  public void nameOfVerifierUpdateOverridesVerifierCreation() throws Exception {
    String anotherName = "another-name";

    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setName(verifierName).build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setName(anotherName).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasName(anotherName);
  }

  @Test
  public void nameOfNewVerifierMustNotBeEmpty() throws Exception {
    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().setName("").build();
    VerifierConfig verifierConfig =
        VerifierConfig.createForNewVerifier(projectName, repository, verifierCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage(
          String.format("Name of the verifier %s must be defined", verifierUuid));
      verifierConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void descriptionDefaultsToOptionalEmpty() throws Exception {
    VerifierCreation verifierCreation =
        VerifierCreation.builder()
            .setVerifierUuid(verifierUuid)
            .setName(verifierName)
            .setRepository(verifierRepository)
            .build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasDescriptionThat().isEmpty();
  }

  @Test
  public void specifiedDescriptionIsRespectedForNewVerifier() throws Exception {
    String description = "This is a test verifier.";

    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setDescription(description).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasDescriptionThat().value().isEqualTo(description);
  }

  @Test
  public void emptyDescriptionForNewVerifierIsIgnored() throws Exception {
    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setDescription("").build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasDescriptionThat().isEmpty();
  }

  @Test
  public void urlDefaultsToOptionalEmpty() throws Exception {
    VerifierCreation verifierCreation =
        VerifierCreation.builder()
            .setVerifierUuid(verifierUuid)
            .setName(verifierName)
            .setRepository(verifierRepository)
            .build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasUrlThat().isEmpty();
  }

  @Test
  public void specifiedUrlIsRespectedForNewVerifier() throws Exception {
    String url = "http://example.com/my-verifier";

    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setUrl(url).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasUrlThat().value().isEqualTo(url);
  }

  @Test
  public void emptyUrlForNewVerifierIsIgnored() throws Exception {
    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setUrl("").build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasUrlThat().isEmpty();
  }

  @Test
  public void specifiedRepositoryIsRespectedForNewVerifier() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setRepository(verifierRepository).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasRepository(verifierRepository);
  }

  @Test
  public void repositoryOfVerifierUpdateOverridesVerifierCreation() throws Exception {
    Project.NameKey anotherRepository = new Project.NameKey("another-repo");

    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setRepository(verifierRepository).build();
    VerifierUpdate verifierUpdate =
        VerifierUpdate.builder().setRepository(anotherRepository).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasRepository(anotherRepository);
  }

  @Test
  public void repositoryOfNewVerifierMustNotBeEmpty() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setRepository(new Project.NameKey("")).build();
    VerifierConfig verifierConfig =
        VerifierConfig.createForNewVerifier(projectName, repository, verifierCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage(
          String.format("Repository of the verifier %s must be defined", verifierUuid));
      verifierConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void createdOnDefaultsToNow() throws Exception {
    // Git timestamps are only precise to the second.
    Timestamp testStart = TimeUtil.truncateToSecond(TimeUtil.nowTs());

    createArbitraryVerifier(verifierUuid);
    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().hasCreatedOnThat().isAtLeast(testStart);
  }

  @Test
  public void specifiedCreatedOnIsRespectedForNewVerifier() throws Exception {
    Timestamp createdOn = toTimestamp(LocalDate.of(2017, Month.DECEMBER, 11).atTime(13, 44, 10));

    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setUpdatedOn(createdOn).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().hasCreatedOnThat().isEqualTo(createdOn);
  }

  @Test
  public void nameInConfigMayNotBeUndefined() throws Exception {
    populateVerifierConfig(verifierUuid, "[verifier]");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage(String.format("name of verifier %s not set", verifierUuid));
    loadVerifier(verifierUuid);
  }

  @Test
  public void correctCommitMessageForVerifierUpdate() throws Exception {
    createArbitraryVerifier(verifierUuid);
    assertThatCommitMessage(verifierUuid).isEqualTo("Create verifier");

    VerifierUpdate verifierUpdate =
        VerifierUpdate.builder().setDescription("A description.").build();
    updateVerifier(verifierUuid, verifierUpdate);
    assertThatCommitMessage(verifierUuid).isEqualTo("Update verifier");
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    VerifierCreation verifierCreation =
        VerifierCreation.builder()
            .setVerifierUuid(verifierUuid)
            .setName(verifierName)
            .setRepository(verifierRepository)
            .build();
    createVerifier(verifierCreation);

    String newName = "new-name";
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setName(newName).build();
    updateVerifier(verifierUuid, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().hasName(newName);

    assertThatCommitMessage(verifierUuid)
        .isEqualTo("Update verifier\n\nRename from " + verifierName + " to " + newName);
  }

  @Test
  public void nameCannotBeRemoved() throws Exception {
    createArbitraryVerifier(verifierUuid);

    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setName("").build();

    exception.expect(IOException.class);
    exception.expectMessage(String.format("Name of the verifier %s must be defined", verifierUuid));
    updateVerifier(verifierUuid, verifierUpdate);
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    createArbitraryVerifier(verifierUuid);
    String newDescription = "New description";

    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setDescription(newDescription).build();
    updateVerifier(verifierUuid, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().hasDescriptionThat().value().isEqualTo(newDescription);
  }

  @Test
  public void descriptionCanBeRemoved() throws Exception {
    createArbitraryVerifier(verifierUuid);

    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setDescription("").build();
    Optional<Verifier> verifier = updateVerifier(verifierUuid, verifierUpdate);

    assertThatVerifier(verifier).value().hasDescriptionThat().isEmpty();
  }

  @Test
  public void urlCanBeUpdated() throws Exception {
    createArbitraryVerifier(verifierUuid);
    String newUrl = "http://example.com/my-verifier";

    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setUrl(newUrl).build();
    updateVerifier(verifierUuid, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().hasUrlThat().value().isEqualTo(newUrl);
  }

  @Test
  public void urlCanBeRemoved() throws Exception {
    createArbitraryVerifier(verifierUuid);

    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setUrl("").build();
    Optional<Verifier> verifier = updateVerifier(verifierUuid, verifierUpdate);

    assertThatVerifier(verifier).value().hasUrlThat().isEmpty();
  }

  @Test
  public void repositoryCanBeUpdated() throws Exception {
    VerifierCreation verifierCreation =
        VerifierCreation.builder()
            .setVerifierUuid(verifierUuid)
            .setName(verifierName)
            .setRepository(verifierRepository)
            .build();
    createVerifier(verifierCreation);

    Project.NameKey newRepository = new Project.NameKey("another-repo");
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setRepository(newRepository).build();
    updateVerifier(verifierUuid, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().hasRepository(newRepository);

    assertThatCommitMessage(verifierUuid).isEqualTo("Update verifier");
  }

  @Test
  public void repositoryCannotBeRemoved() throws Exception {
    createArbitraryVerifier(verifierUuid);

    VerifierUpdate verifierUpdate =
        VerifierUpdate.builder().setRepository(new Project.NameKey("")).build();

    exception.expect(IOException.class);
    exception.expectMessage(
        String.format("Repository of the verifier %s must be defined", verifierUuid));
    updateVerifier(verifierUuid, verifierUpdate);
  }

  @Test
  public void refStateIsCorrectlySet() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setVerifierUuid(verifierUuid).build();
    Optional<Verifier> newVerifier = createVerifier(verifierCreation);
    ObjectId expectedRefStateAfterCreation = getVerifierRefState(verifierUuid);
    assertThatVerifier(newVerifier)
        .value()
        .hasRefStateThat()
        .isEqualTo(expectedRefStateAfterCreation);

    Optional<Verifier> loadedVerifier = loadVerifier(verifierUuid);
    assertThatVerifier(loadedVerifier)
        .value()
        .hasRefStateThat()
        .isEqualTo(expectedRefStateAfterCreation);

    VerifierUpdate verifierUpdate =
        VerifierUpdate.builder().setDescription("A description.").build();
    Optional<Verifier> updatedVerifier = updateVerifier(verifierUuid, verifierUpdate);
    ObjectId expectedRefStateAfterUpdate = getVerifierRefState(verifierUuid);
    assertThat(expectedRefStateAfterUpdate).isNotEqualTo(expectedRefStateAfterCreation);
    assertThatVerifier(updatedVerifier)
        .value()
        .hasRefStateThat()
        .isEqualTo(expectedRefStateAfterUpdate);
  }

  private void createArbitraryVerifier(String verifierUuid) throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setVerifierUuid(verifierUuid).build();
    createVerifier(verifierCreation);
  }

  private VerifierCreation.Builder getPrefilledVerifierCreationBuilder() {
    return VerifierCreation.builder()
        .setVerifierUuid(verifierUuid)
        .setName(verifierName)
        .setRepository(verifierRepository);
  }

  private Optional<Verifier> createVerifier(VerifierCreation verifierCreation) throws Exception {
    VerifierConfig verifierConfig =
        VerifierConfig.createForNewVerifier(projectName, repository, verifierCreation);
    commit(verifierConfig);
    return verifierConfig.getLoadedVerifier();
  }

  private Optional<Verifier> createVerifier(
      VerifierCreation verifierCreation, VerifierUpdate verifierUpdate) throws Exception {
    VerifierConfig verifierConfig =
        VerifierConfig.createForNewVerifier(projectName, repository, verifierCreation);
    verifierConfig.setVerifierUpdate(verifierUpdate);
    commit(verifierConfig);
    return verifierConfig.getLoadedVerifier();
  }

  private Optional<Verifier> updateVerifier(String verifierUuid, VerifierUpdate verifierUpdate)
      throws Exception {
    VerifierConfig verifierConfig =
        VerifierConfig.loadForVerifier(projectName, repository, verifierUuid);
    verifierConfig.setVerifierUpdate(verifierUpdate);
    commit(verifierConfig);
    return verifierConfig.getLoadedVerifier();
  }

  private Optional<Verifier> loadVerifier(String uuid) throws Exception {
    VerifierConfig verifierConfig = VerifierConfig.loadForVerifier(projectName, repository, uuid);
    return verifierConfig.getLoadedVerifier();
  }

  private void commit(VerifierConfig verifierConfig) throws IOException {
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      verifierConfig.commit(metaDataUpdate);
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

  private void populateVerifierConfig(String uuid, String fileContent) throws Exception {
    testRepository
        .branch(RefNames.refsVerifiers(uuid))
        .commit()
        .message("Prepopulate verifier.config")
        .add(VerifierConfig.VERIFIER_CONFIG_FILE, fileContent)
        .create();
  }

  private ObjectId getVerifierRefState(String verifierUuid) throws IOException {
    return repository.exactRef(RefNames.refsVerifiers(verifierUuid)).getObjectId();
  }

  private StringSubject assertThatCommitMessage(String verifierUuid) throws IOException {
    try (RevWalk rw = new RevWalk(repository)) {
      RevCommit commit = rw.parseCommit(getVerifierRefState(verifierUuid));
      return assertThat(commit.getFullMessage()).named("commit message");
    }
  }

  private static OptionalSubject<VerifierSubject, Verifier> assertThatVerifier(
      Optional<Verifier> loadedVerifier) {
    return assertThat(loadedVerifier, VerifierSubject::assertThat);
  }

  private static Timestamp toTimestamp(LocalDateTime localDateTime) {
    return Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }
}
