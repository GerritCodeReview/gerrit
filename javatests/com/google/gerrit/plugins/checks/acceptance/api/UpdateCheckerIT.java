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

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.testing.CommitSubject.assertCommit;

import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperations.PerCheckerOperations;
import com.google.gerrit.plugins.checks.acceptance.testsuite.TestChecker;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerInput;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SkipProjectClone
public class UpdateCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("checks", "api", "enabled", true);
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
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").repository(allProjects).create();
    TestChecker checker = checkerOperations.checker(checkerUuid).get();

    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";
    input.description = "A description.";
    input.url = "http://example.com/my-checker";
    input.repository = repositoryName.get();

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.uuid).isEqualTo(checkerUuid);
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.url).isEqualTo(input.url);
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.createdOn).isEqualTo(checker.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(),
        "Update checker\n\nRename from my-checker to my-renamed-checker",
        info.updatedOn,
        perCheckerOps.get().refState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName)).containsExactly(info.uuid);
  }

  @Test
  public void updateCheckerName() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(),
        "Update checker\n\nRename from my-checker to my-renamed-checker",
        info.updatedOn,
        perCheckerOps.get().refState());
  }

  @Test
  public void cannotSetCheckerNameToEmptyString() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    checkersApi.id(checkerUuid).update(checkerInput);
  }

  @Test
  public void cannotSetCheckerNameToStringWhichIsEmptyAfterTrim() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    checkersApi.id(checkerUuid).update(checkerInput);
  }

  @Test
  public void updateCheckerNameToNameThatIsAlreadyUsed() throws Exception {
    checkerOperations.newChecker().name("other-checker").create();

    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "other-checker";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(),
        "Update checker\n\nRename from my-checker to other-checker",
        info.updatedOn,
        perCheckerOps.get().refState());
  }

  @Test
  public void addCheckerDescription() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.description = "A description.";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void updateCheckerDescription() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").description("A description.").create();

    CheckerInput input = new CheckerInput();
    input.description = "A new description.";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void unsetCheckerDescription() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").description("A description.").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.description = "";

    CheckerInfo info = checkersApi.id(checkerUuid).update(checkerInput);
    assertThat(info.description).isNull();

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void checkerDescriptionIsTrimmed() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.description = " A description. ";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo("A description.");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void addCheckerUrl() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.url = "http://example.com/my-checker";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void updateCheckerUrl() throws Exception {
    String checkerUuid =
        checkerOperations
            .newChecker()
            .name("my-checker")
            .url("http://example.com/my-checker")
            .create();

    CheckerInput input = new CheckerInput();
    input.url = "http://example.com/my-checker-foo";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void unsetCheckerUrl() throws Exception {
    String checkerUuid =
        checkerOperations
            .newChecker()
            .name("my-checker")
            .url("http://example.com/my-checker")
            .create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.url = "";

    CheckerInfo info = checkersApi.id(checkerUuid).update(checkerInput);
    assertThat(info.url).isNull();

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void checkerUrlIsTrimmed() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.url = " http://example.com/my-checker ";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.url).isEqualTo("http://example.com/my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
  }

  @Test
  public void updateRepository() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").repository(allProjects).create();

    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.repository = repositoryName.get();

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.repository).isEqualTo(input.repository);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updatedOn, perCheckerOps.get().refState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName)).containsExactly(info.uuid);
  }

  @Test
  public void cannotSetRepositoryToEmptyString() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.repository = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository cannot be unset");
    checkersApi.id(checkerUuid).update(checkerInput);
  }

  @Test
  public void cannotSetRepositoryToStringWhichIsEmptyAfterTrim() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.repository = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository cannot be unset");
    checkersApi.id(checkerUuid).update(checkerInput);
  }

  @Test
  public void cannotSetNonExistingRepository() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.repository = "non-existing";

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("repository non-existing not found");
    checkersApi.id(checkerUuid).update(checkerInput);
  }

  @Test
  public void cannotSetUrlToInvalidUrl() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.url = "ftp://example.com/my-checker";
    exception.expect(BadRequestException.class);
    exception.expectMessage("only http/https URLs supported: ftp://example.com/my-checker");
    checkersApi.id(checkerUuid).update(input);
  }

  @Test
  public void disableAndReenable() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").repository(allProjects).create();
    assertThat(checkerOperations.checkersOf(allProjects)).containsExactly(checkerUuid);

    CheckerInput input = new CheckerInput();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();

    input = new CheckerInput();
    input.status = CheckerStatus.ENABLED;
    info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).containsExactly(checkerUuid);
  }

  @Test
  public void updateRepositoryDuringDisable() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").repository(allProjects).create();

    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.repository = repositoryName.get();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();
  }

  @Test
  public void updateRepositoryDuringEnable() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("my-checker").repository(allProjects).create();

    Project.NameKey repositoryName = projectOperations.newProject().create();
    assertThat(checkerOperations.checkersOf(allProjects)).containsExactly(checkerUuid);
    assertThat(checkerOperations.checkersOf(repositoryName)).isEmpty();

    CheckerInput input = new CheckerInput();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();
    assertThat(checkerOperations.checkersOf(repositoryName)).isEmpty();

    input = new CheckerInput();
    input.status = CheckerStatus.ENABLED;
    input.repository = repositoryName.get();
    info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();
    assertThat(checkerOperations.checkersOf(repositoryName)).containsExactly(checkerUuid);
  }

  @Test
  public void updateCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    requestScopeOperations.setApiUser(user.getId());

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checks not permitted");
    checkersApi.id(checkerUuid).update(input);
  }
}
