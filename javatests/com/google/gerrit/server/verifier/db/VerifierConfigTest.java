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

import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.verifier.Verifier;
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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class VerifierConfigTest extends GerritBaseTests {
  private Project.NameKey projectName;
  private Repository repository;
  private TestRepository<?> testRepository;

  private final String verifierUuid = "verifier-XYZ";
  private final String verifierName = "my-verifier";
  private final TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

  @Before
  public void setUp() throws Exception {
    projectName = new Project.NameKey("Test Repository");
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
    testRepository = new TestRepository<>(repository);
  }

  @Test
  public void specifiedVerifierUuidIsRespectedForNewVerifier() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setVerifierUuid(verifierUuid).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierUuid);
    assertThatVerifier(verifier).value().uuid().isEqualTo(verifierUuid);
  }

  @Test
  public void specifiedNameIsRespectedForNewVerifier() throws Exception {
    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setName(verifierName).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().name().isEqualTo(verifierName);
  }

  @Test
  public void nameOfVerifierUpdateOverridesVerifierCreation() throws Exception {
    String anotherName = "another-name";

    VerifierCreation verifierCreation =
        getPrefilledVerifierCreationBuilder().setName(verifierName).build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setName(anotherName).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().name().isEqualTo(anotherName);
  }

  @Test
  public void nameOfNewVerifierMustNotBeEmpty() throws Exception {
    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().setName("").build();
    VerifierConfig verifierConfig =
        VerifierConfig.createForNewVerifier(projectName, repository, verifierCreation);

    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      exception.expectCause(instanceOf(ConfigInvalidException.class));
      exception.expectMessage("Name of the verifier " + verifierUuid);
      verifierConfig.commit(metaDataUpdate);
    }
  }

  @Test
  public void descriptionDefaultsToOptionalEmpty() throws Exception {
    VerifierCreation verifierCreation =
        VerifierCreation.builder().setVerifierUuid(verifierUuid).setName(verifierName).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().description().isEmpty();
  }

  @Test
  public void specifiedDescriptionIsRespectedForNewVerifier() throws Exception {
    String description = "This is a test verifier.";

    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setDescription(description).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().description().value().isEqualTo(description);
  }

  @Test
  public void emptyDescriptionForNewVerifierIsIgnored() throws Exception {
    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setDescription("").build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().description().isEmpty();
  }

  @Test
  public void createdOnDefaultsToNow() throws Exception {
    // Git timestamps are only precise to the second.
    Timestamp testStart = TimeUtil.truncateToSecond(TimeUtil.nowTs());

    VerifierCreation verifierCreation =
        VerifierCreation.builder().setVerifierUuid(verifierUuid).setName(verifierName).build();
    createVerifier(verifierCreation);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().createdOn().isAtLeast(testStart);
  }

  @Test
  public void specifiedCreatedOnIsRespectedForNewVerifier() throws Exception {
    Timestamp createdOn = toTimestamp(LocalDate.of(2017, Month.DECEMBER, 11).atTime(13, 44, 10));

    VerifierCreation verifierCreation = getPrefilledVerifierCreationBuilder().build();
    VerifierUpdate verifierUpdate = VerifierUpdate.builder().setUpdatedOn(createdOn).build();
    createVerifier(verifierCreation, verifierUpdate);

    Optional<Verifier> verifier = loadVerifier(verifierCreation.getVerifierUuid());
    assertThatVerifier(verifier).value().createdOn().isEqualTo(createdOn);
  }

  @Test
  public void nameInConfigMayNotBeUndefined() throws Exception {
    populateVerifierConfig(verifierUuid, "[verifier]");

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage(String.format("name of verifier %s not set", verifierUuid));
    loadVerifier(verifierUuid);
  }

  private VerifierCreation.Builder getPrefilledVerifierCreationBuilder() {
    return VerifierCreation.builder().setVerifierUuid(verifierUuid).setName(verifierName);
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

  private static OptionalSubject<VerifierSubject, Verifier> assertThatVerifier(
      Optional<Verifier> loadedVerifier) {
    return assertThat(loadedVerifier, VerifierSubject::assertThat);
  }

  private static Timestamp toTimestamp(LocalDateTime localDateTime) {
    return Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }
}
