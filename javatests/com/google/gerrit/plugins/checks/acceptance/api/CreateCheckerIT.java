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

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperations.PerCheckerOperations;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerInput;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CreateCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

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
    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = repositoryName.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.uuid).isNotNull();
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isNull();
    assertThat(info.url).isNull();
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(info.createdOn).isNotNull();
    assertThat(info.updatedOn).isEqualTo(info.createdOn);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName)).containsExactly(info.uuid);
  }

  @Test
  public void createCheckerWithDescription() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.description = "some description";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
  }

  @Test
  public void createCheckerWithUrl() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.url = "http://example.com/my-checker";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
  }

  @Test
  public void createCheckerNameIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = " my-checker ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.name).isEqualTo("my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
  }

  @Test
  public void createCheckerDescriptionIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.description = " some description ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo("some description");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
  }

  @Test
  public void createCheckerUrlIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.url = " http://example.com/my-checker ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo("http://example.com/my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
  }

  @Test
  public void createCheckerRepositoryIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = " " + allProjects.get() + " ";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.repository).isEqualTo(allProjects.get());

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.createdOn, perCheckerOps.get().refState());
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
    input.repository = allProjects.get();
    CheckerInfo info1 = checkersApi.create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    CheckerInfo info2 = checkersApi.create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createCheckerWithoutNameFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyNameFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "";
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyNameAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = " ";
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithoutRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyRepositoryAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithNonExistingRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = "non-existing";

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("repository non-existing not found");
    checkersApi.create(input);
  }

  @Test
  public void createDisabledChecker() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = allProjects.get();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
  }

  @Test
  public void createMultipleCheckers() throws Exception {
    Project.NameKey repositoryName1 = projectOperations.newProject().create();
    Project.NameKey repositoryName2 = projectOperations.newProject().create();

    String checkerUuid1 = checkerOperations.newChecker().repository(repositoryName1).create();
    String checkerUuid2 = checkerOperations.newChecker().repository(repositoryName1).create();
    String checkerUuid3 = checkerOperations.newChecker().repository(repositoryName1).create();
    String checkerUuid4 = checkerOperations.newChecker().repository(repositoryName2).create();
    String checkerUuid5 = checkerOperations.newChecker().repository(repositoryName2).create();

    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(
            CheckersByRepositoryNotes.computeRepositorySha1(repositoryName1),
            CheckersByRepositoryNotes.computeRepositorySha1(repositoryName2));
    assertThat(checkerOperations.checkersOf(repositoryName1))
        .containsExactly(checkerUuid1, checkerUuid2, checkerUuid3);
    assertThat(checkerOperations.checkersOf(repositoryName2))
        .containsExactly(checkerUuid4, checkerUuid5);
  }

  @Test
  public void createCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.repository = allProjects.get();

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checks not permitted");
    checkersApi.create(input);
  }
}
