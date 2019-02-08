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

package com.google.gerrit.acceptance.testsuite.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.verifier.VerifierUuid;
import com.google.gerrit.server.verifier.db.VerifierConfig;
import com.google.gerrit.server.verifier.db.VerifiersByRepositoryNotes;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class VerifierOperationsImplTest extends AbstractDaemonTest {
  @Inject private VerifierOperationsImpl verifierOperations;

  @Test
  public void verifierCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    VerifierInfo foundVerifier = getVerifierFromServer(verifierUuid);
    assertThat(foundVerifier.uuid).isEqualTo(verifierUuid);
    assertThat(foundVerifier.name).isNotEmpty();
    assertThat(foundVerifier.description).isNull();
    assertThat(foundVerifier.createdOn).isNotNull();
  }

  @Test
  public void twoVerifiersWithoutAnyParametersDoNotClash() throws Exception {
    String verifierUuid1 = verifierOperations.newVerifier().create();
    String verifierUuid2 = verifierOperations.newVerifier().create();

    TestVerifier verifier1 = verifierOperations.verifier(verifierUuid1).get();
    TestVerifier verifier2 = verifierOperations.verifier(verifierUuid2).get();
    assertThat(verifier1.uuid()).isNotEqualTo(verifier2.uuid());
  }

  @Test
  public void verifierCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    VerifierInfo foundVerifier = getVerifierFromServer(verifierUuid);
    assertThat(foundVerifier.uuid).isEqualTo(verifierUuid);
  }

  @Test
  public void specifiedNameIsRespectedForVerifierCreation() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("XYZ-123-this-name-must-be-unique").create();

    VerifierInfo verifier = getVerifierFromServer(verifierUuid);
    assertThat(verifier.name).isEqualTo("XYZ-123-this-name-must-be-unique");
  }

  @Test
  public void specifiedDescriptionIsRespectedForVerifierCreation() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().description("A simple verifier.").create();

    VerifierInfo verifier = getVerifierFromServer(verifierUuid);
    assertThat(verifier.description).isEqualTo("A simple verifier.");
  }

  @Test
  public void requestingNoDescriptionIsPossibleForVerifierCreation() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().clearDescription().create();

    VerifierInfo verifier = getVerifierFromServer(verifierUuid);
    assertThat(verifier.description).isNull();
  }

  @Test
  public void existingVerifierCanBeCheckedForExistence() throws Exception {
    String verifierUuid = createVerifierInServer(createArbitraryVerifierInput());

    boolean exists = verifierOperations.verifier(verifierUuid).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingVerifierCanBeCheckedForExistence() throws Exception {
    String notExistingVerifierUuid = "not-existing-verifier";

    boolean exists = verifierOperations.verifier(notExistingVerifierUuid).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingNotExistingVerifierFails() throws Exception {
    String notExistingVerifierUuid = "not-existing-verifier";

    exception.expect(IllegalStateException.class);
    verifierOperations.verifier(notExistingVerifierUuid).get();
  }

  @Test
  public void verifierNotCreatedByTestApiCanBeRetrieved() throws Exception {
    VerifierInput input = createArbitraryVerifierInput();
    input.name = "unique verifier not created via test API";
    String verifierUuid = createVerifierInServer(input);

    TestVerifier foundVerifier = verifierOperations.verifier(verifierUuid).get();

    assertThat(foundVerifier.uuid()).isEqualTo(verifierUuid);
    assertThat(foundVerifier.name()).isEqualTo("unique verifier not created via test API");
  }

  @Test
  public void uuidOfExistingVerifierCanBeRetrieved() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    String foundVerifierUuid = verifierOperations.verifier(verifierUuid).get().uuid();

    assertThat(foundVerifierUuid).isEqualTo(verifierUuid);
  }

  @Test
  public void nameOfExistingVerifierCanBeRetrieved() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("ABC-789-this-name-must-be-unique").create();

    String verifierName = verifierOperations.verifier(verifierUuid).get().name();

    assertThat(verifierName).isEqualTo("ABC-789-this-name-must-be-unique");
  }

  @Test
  public void descriptionOfExistingVerifierCanBeRetrieved() throws Exception {
    String verifierUuid =
        verifierOperations
            .newVerifier()
            .description("This is a very detailed description of this verifier.")
            .create();

    Optional<String> description = verifierOperations.verifier(verifierUuid).get().description();

    assertThat(description).hasValue("This is a very detailed description of this verifier.");
  }

  @Test
  public void emptyDescriptionOfExistingVerifierCanBeRetrieved() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().clearDescription().create();

    Optional<String> description = verifierOperations.verifier(verifierUuid).get().description();

    assertThat(description).isEmpty();
  }

  @Test
  public void createdOnOfExistingVerifierCanBeRetrieved() throws Exception {
    VerifierInfo verifier = gApi.verifiers().create(createArbitraryVerifierInput()).get();

    Timestamp createdOn = verifierOperations.verifier(verifier.uuid).get().createdOn();

    assertThat(createdOn).isEqualTo(verifier.createdOn);
  }

  @Test
  public void updateWithoutAnyParametersIsANoop() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    TestVerifier originalVerifier = verifierOperations.verifier(verifierUuid).get();

    verifierOperations.verifier(verifierUuid).forUpdate().update();

    TestVerifier updatedVerifier = verifierOperations.verifier(verifierUuid).get();
    assertThat(updatedVerifier).isEqualTo(originalVerifier);
  }

  @Test
  public void updateWritesToInternalVerifierSystem() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().description("original description").create();

    verifierOperations
        .verifier(verifierUuid)
        .forUpdate()
        .description("updated description")
        .update();

    String currentDescription = getVerifierFromServer(verifierUuid).description;
    assertThat(currentDescription).isEqualTo("updated description");
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("original name").create();

    verifierOperations.verifier(verifierUuid).forUpdate().name("updated name").update();

    String currentName = verifierOperations.verifier(verifierUuid).get().name();
    assertThat(currentName).isEqualTo("updated name");
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().description("original description").create();

    verifierOperations
        .verifier(verifierUuid)
        .forUpdate()
        .description("updated description")
        .update();

    Optional<String> currentDescription =
        verifierOperations.verifier(verifierUuid).get().description();
    assertThat(currentDescription).hasValue("updated description");
  }

  @Test
  public void descriptionCanBeCleared() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().description("original description").create();

    verifierOperations.verifier(verifierUuid).forUpdate().clearDescription().update();

    Optional<String> currentDescription =
        verifierOperations.verifier(verifierUuid).get().description();
    assertThat(currentDescription).isEmpty();
  }

  @Test
  public void getCommit() throws Exception {
    VerifierInfo verifier = gApi.verifiers().create(createArbitraryVerifierInput()).get();

    RevCommit commit = verifierOperations.verifier(verifier.uuid).commit();
    assertThat(commit).isEqualTo(readVerifierCommitSha1(verifier.uuid));
  }

  private ObjectId readVerifierCommitSha1(String verifierUuid) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects)) {
      return repo.exactRef(RefNames.refsVerifiers(verifierUuid)).getObjectId();
    }
  }

  @Test
  public void getConfigText() throws Exception {
    VerifierInfo verifier = gApi.verifiers().create(createArbitraryVerifierInput()).get();

    String configText = verifierOperations.verifier(verifier.uuid).configText();
    assertThat(configText).isEqualTo(readVerifierConfigFile(verifier.uuid));
  }

  private String readVerifierConfigFile(String verifierUuid) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref verifierRef = repo.exactRef(RefNames.refsVerifiers(verifierUuid));
      RevCommit commit = rw.parseCommit(verifierRef.getObjectId());
      try (TreeWalk tw =
          TreeWalk.forPath(or, VerifierConfig.VERIFIER_CONFIG_FILE, commit.getTree())) {
        return new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8);
      }
    }
  }

  @Test
  public void asInfo() throws Exception {
    String verifierUuid =
        verifierOperations
            .newVerifier()
            .name("my-verifier")
            .description("A description.")
            .url("http://example.com/my-verifier")
            .create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();
    VerifierInfo verifierInfo = verifierOperations.verifier(verifierUuid).asInfo();
    assertThat(verifierInfo.uuid).isEqualTo(verifier.uuid());
    assertThat(verifierInfo.name).isEqualTo(verifier.name());
    assertThat(verifierInfo.description).isEqualTo(verifier.description().get());
    assertThat(verifierInfo.url).isEqualTo(verifier.url().get());
    assertThat(verifierInfo.createdOn).isEqualTo(verifier.createdOn());
    assertThat(verifierInfo.updatedOn).isEqualTo(verifier.updatedOn());
  }

  @Test
  public void getVerifiersOfRepository() throws Exception {
    String verifierUuid1 = VerifierUuid.make("my-verifier1");
    String verifierUuid2 = VerifierUuid.make("my-verifier2");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      new TestRepository<>(repo)
          .branch(RefNames.REFS_META_VERIFIERS)
          .commit()
          .add(
              VerifiersByRepositoryNotes.computeRepositorySha1(project).getName(),
              Joiner.on('\n').join(verifierUuid1, verifierUuid2))
          .create();
    }

    assertThat(verifierOperations.verifiersOf(project))
        .containsExactly(verifierUuid1, verifierUuid2);
  }

  @Test
  public void getVerifiersOfRepositoryWithoutVerifiers() throws Exception {
    assertThat(verifierOperations.verifiersOf(project)).isEmpty();
  }

  @Test
  public void getVerifiersOfNonExistingRepositor() throws Exception {
    assertThat(verifierOperations.verifiersOf(new Project.NameKey("non-existing"))).isEmpty();
  }

  @Test
  public void getSha1sOfRepositoriesWithVerifiers() throws Exception {
    String verifierUuid1 = VerifierUuid.make("my-verifier1");
    String verifierUuid2 = VerifierUuid.make("my-verifier2");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      new TestRepository<>(repo)
          .branch(RefNames.REFS_META_VERIFIERS)
          .commit()
          .add(VerifiersByRepositoryNotes.computeRepositorySha1(project).getName(), verifierUuid1)
          .add(
              VerifiersByRepositoryNotes.computeRepositorySha1(allProjects).getName(),
              verifierUuid2)
          .create();
    }

    assertThat(verifierOperations.sha1sOfRepositoriesWithVerifiers())
        .containsExactly(
            VerifiersByRepositoryNotes.computeRepositorySha1(project),
            VerifiersByRepositoryNotes.computeRepositorySha1(allProjects));
  }

  private VerifierInput createArbitraryVerifierInput() {
    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = name("test-verifier");
    verifierInput.repository = allProjects.get();
    return verifierInput;
  }

  private VerifierInfo getVerifierFromServer(String verifierUuid) throws RestApiException {
    return gApi.verifiers().id(verifierUuid).get();
  }

  private String createVerifierInServer(VerifierInput input) throws RestApiException {
    VerifierInfo verifier = gApi.verifiers().create(input).get();
    return verifier.uuid;
  }
}
