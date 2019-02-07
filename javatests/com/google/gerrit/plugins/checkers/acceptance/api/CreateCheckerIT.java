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

package com.google.gerrit.plugins.checkers.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.testing.CommitSubject.assertCommit;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checkers.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checkers.acceptance.testsuite.CheckerOperations.PerCheckerOperations;
import com.google.gerrit.plugins.checkers.api.CheckerInfo;
import com.google.gerrit.plugins.checkers.api.CheckerInput;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CreateCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void createChecker() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.uuid).isNotNull();
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isNull();
    assertThat(info.url).isNull();
    assertThat(info.createdOn).isNotNull();
    assertThat(info.updatedOn).isEqualTo(info.createdOn);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText()).isEqualTo("[checker]\n\tname = my-checker\n");
  }

  @Test
  public void createCheckerWithDescription() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.description = "some description";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo("[checker]\n\tname = my-checker\n\tdescription = some description\n");
  }

  @Test
  public void createCheckerWithUrl() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.url = "http://example.com/my-checker";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo(
            "[checker]\n" + "\tname = my-checker\n" + "\turl = http://example.com/my-checker\n");
  }

  @Test
  public void createCheckerNameIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = " my-checker ";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.name).isEqualTo("my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText()).isEqualTo("[checker]\n\tname = my-checker\n");
  }

  @Test
  public void createCheckerDescriptionIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.description = " some description ";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo("some description");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo("[checker]\n\tname = my-checker\n\tdescription = some description\n");
  }

  @Test
  public void createCheckerUrlIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.url = " http://example.com/my-checker ";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo("http://example.com/my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo(
            "[checker]\n" + "\tname = my-checker\n" + "\turl = http://example.com/my-checker\n");
  }

  @Test
  public void createCheckerWithInvalidUrlFails() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.url = "ftp://example.com/my-checker";
    exception.expect(BadRequestException.class);
    exception.expectMessage("only http/https URLs supported: ftp://example.com/my-checker");
    checkersApi.id(checkerUuid).update(input);
  }

  @Test
  public void createCheckersWithSameName() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    CheckerInfo info1 = checkersApi.create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    CheckerInfo info2 = checkersApi.create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createCheckerWithoutNameFails() throws Exception {
    CheckerInput input = new CheckerInput();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyNameFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyNameAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    CheckerInput input = new CheckerInput();
    input.name = "my-checker";

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checkers not permitted");
    checkersApi.create(input);
  }
}
