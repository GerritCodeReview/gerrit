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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.Optional;
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
    assertThat(verifier1.verifierUuid()).isNotEqualTo(verifier2.verifierUuid());
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

    assertThat(foundVerifier.verifierUuid()).isEqualTo(verifierUuid);
    assertThat(foundVerifier.name()).isEqualTo("unique verifier not created via test API");
  }

  @Test
  public void uuidOfExistingVerifierCanBeRetrieved() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    String foundVerifierUuid = verifierOperations.verifier(verifierUuid).get().verifierUuid();

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

  private VerifierInput createArbitraryVerifierInput() {
    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = name("test-verifier");
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
