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

package com.google.gerrit.acceptance.api.checker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.testing.CommitSubject.assertCommit;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations.PerCheckerOperations;
import com.google.gerrit.acceptance.testsuite.checker.TestChecker;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.api.checkers.CheckerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class UpdateCheckerIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private CheckerOperations checkerOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("checker", "api", "enabled", true);
    return cfg;
  }

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void updateMultipleCheckerPropertiesAtOnce() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();
    TestChecker checker = checkerOperations.checker(checkerUuid).get();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";
    input.description = "A description.";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(input);
    assertThat(info.uuid).isEqualTo(checkerUuid);
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.createdOn).isEqualTo(checker.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(),
        "Update checker\n\nRename from my-checker to my-renamed-checker",
        info.updatedOn,
        perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo("[checker]\n\tname = my-renamed-checker\n\tdescription = A description.\n");
  }

  @Test
  public void updateCheckerName() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(),
        "Update checker\n\nRename from my-checker to my-renamed-checker",
        info.updatedOn,
        perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText()).isEqualTo("[checker]\n\tname = my-renamed-checker\n");
  }

  @Test
  public void cannotSetCheckerNameToEmptyString() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.checkers().id(checkerUuid).update(checkerInput);
  }

  @Test
  public void cannotSetCheckerNameToStringWhichIsEmptyAfterTrim() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.checkers().id(checkerUuid).update(checkerInput);
  }

  @Test
  public void updateCheckerNameToNameThatIsAlreadyUsed() throws Exception {
    checkerOperations.newChecker().name("other-checker").create();

    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "other-checker";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(),
        "Update checker\n\nRename from my-checker to other-checker",
        info.updatedOn,
        perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText()).isEqualTo("[checker]\n\tname = other-checker\n");
  }

  @Test
  public void addCheckerDescription() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.description = "A description.";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo("[checker]\n\tname = my-checker\n\tdescription = A description.\n");
  }

  @Test
  public void updateCheckerDescription() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").description("A description.").create();

    CheckerInput input = new CheckerInput();
    input.description = "A new description.";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo("[checker]\n\tname = my-checker\n\tdescription = A new description.\n");
  }

  @Test
  public void unsetCheckerDescription() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").description("A description.").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.description = "";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(checkerInput);
    assertThat(info.description).isNull();

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText()).isEqualTo("[checker]\n\tname = my-checker\n");
  }

  @Test
  public void checkerDescriptionIsTrimmed() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.description = " A description. ";

    CheckerInfo info = gApi.checkers().id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo("A description.");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
    assertThat(perCheckerOps.configText())
        .isEqualTo("[checker]\n\tname = my-checker\n\tdescription = A description.\n");
  }

  @Test
  public void updateCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    requestScopeOperations.setApiUser(user.getId());

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    exception.expect(AuthException.class);
    exception.expectMessage("administrate checkers not permitted");
    gApi.checkers().id(checkerUuid).update(input);
  }
}
