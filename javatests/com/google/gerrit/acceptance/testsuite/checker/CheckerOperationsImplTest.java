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

package com.google.gerrit.acceptance.testsuite.checker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.api.checkers.CheckerInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.Optional;
import org.junit.Test;

public class CheckerOperationsImplTest extends AbstractDaemonTest {
  @Inject private CheckerOperationsImpl checkerOperations;

  @Test
  public void checkerCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInfo foundChecker = getCheckerFromServer(checkerUuid);
    assertThat(foundChecker.uuid).isEqualTo(checkerUuid);
    assertThat(foundChecker.name).isNotEmpty();
    assertThat(foundChecker.description).isNull();
    assertThat(foundChecker.createdOn).isNotNull();
  }

  @Test
  public void twoCheckersWithoutAnyParametersDoNotClash() throws Exception {
    String checkerUuid1 = checkerOperations.newChecker().create();
    String checkerUuid2 = checkerOperations.newChecker().create();

    TestChecker checker1 = checkerOperations.checker(checkerUuid1).get();
    TestChecker checker2 = checkerOperations.checker(checkerUuid2).get();
    assertThat(checker1.uuid()).isNotEqualTo(checker2.uuid());
  }

  @Test
  public void checkerCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInfo foundChecker = getCheckerFromServer(checkerUuid);
    assertThat(foundChecker.uuid).isEqualTo(checkerUuid);
  }

  @Test
  public void specifiedNameIsRespectedForCheckerCreation() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("XYZ-123-this-name-must-be-unique").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.name).isEqualTo("XYZ-123-this-name-must-be-unique");
  }

  @Test
  public void specifiedDescriptionIsRespectedForCheckerCreation() throws Exception {
    String checkerUuid = checkerOperations.newChecker().description("A simple checker.").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.description).isEqualTo("A simple checker.");
  }

  @Test
  public void requestingNoDescriptionIsPossibleForCheckerCreation() throws Exception {
    String checkerUuid = checkerOperations.newChecker().clearDescription().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.description).isNull();
  }

  @Test
  public void existingCheckerCanBeCheckedForExistence() throws Exception {
    String checkerUuid = createCheckerInServer(createArbitraryCheckerInput());

    boolean exists = checkerOperations.checker(checkerUuid).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingCheckerCanBeCheckedForExistence() throws Exception {
    String notExistingCheckerUuid = "not-existing-checker";

    boolean exists = checkerOperations.checker(notExistingCheckerUuid).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingNotExistingCheckerFails() throws Exception {
    String notExistingCheckerUuid = "not-existing-checker";

    exception.expect(IllegalStateException.class);
    checkerOperations.checker(notExistingCheckerUuid).get();
  }

  @Test
  public void checkerNotCreatedByTestApiCanBeRetrieved() throws Exception {
    CheckerInput input = createArbitraryCheckerInput();
    input.name = "unique checker not created via test API";
    String checkerUuid = createCheckerInServer(input);

    TestChecker foundChecker = checkerOperations.checker(checkerUuid).get();

    assertThat(foundChecker.uuid()).isEqualTo(checkerUuid);
    assertThat(foundChecker.name()).isEqualTo("unique checker not created via test API");
  }

  @Test
  public void uuidOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    String foundCheckerUuid = checkerOperations.checker(checkerUuid).get().uuid();

    assertThat(foundCheckerUuid).isEqualTo(checkerUuid);
  }

  @Test
  public void nameOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("ABC-789-this-name-must-be-unique").create();

    String checkerName = checkerOperations.checker(checkerUuid).get().name();

    assertThat(checkerName).isEqualTo("ABC-789-this-name-must-be-unique");
  }

  @Test
  public void descriptionOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid =
        checkerOperations
            .newChecker()
            .description("This is a very detailed description of this checker.")
            .create();

    Optional<String> description = checkerOperations.checker(checkerUuid).get().description();

    assertThat(description).hasValue("This is a very detailed description of this checker.");
  }

  @Test
  public void emptyDescriptionOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid = checkerOperations.newChecker().clearDescription().create();

    Optional<String> description = checkerOperations.checker(checkerUuid).get().description();

    assertThat(description).isEmpty();
  }

  @Test
  public void createdOnOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerInfo checker = gApi.checkers().create(createArbitraryCheckerInput()).get();

    Timestamp createdOn = checkerOperations.checker(checker.uuid).get().createdOn();

    assertThat(createdOn).isEqualTo(checker.createdOn);
  }

  private CheckerInput createArbitraryCheckerInput() {
    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = name("test-checker");
    return checkerInput;
  }

  private CheckerInfo getCheckerFromServer(String checkerUuid) throws RestApiException {
    return gApi.checkers().id(checkerUuid).get();
  }

  private String createCheckerInServer(CheckerInput input) throws RestApiException {
    CheckerInfo checker = gApi.checkers().create(input).get();
    return checker.uuid;
  }
}
