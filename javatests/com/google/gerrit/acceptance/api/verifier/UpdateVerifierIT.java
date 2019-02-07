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

package com.google.gerrit.acceptance.api.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.testing.CommitSubject.assertCommit;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.verifier.TestVerifier;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations.PerVerifierOperations;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
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
public class UpdateVerifierIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private VerifierOperations verifierOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("verifier", "api", "enabled", true);
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
  public void updateMultipleVerifierPropertiesAtOnce() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("my-verifier").repository(allProjects).create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInput input = new VerifierInput();
    input.name = "my-renamed-verifier";
    input.description = "A description.";
    input.url = "http://example.com/my-verifier";
    input.repository = projectOperations.newProject().create().get();

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.url).isEqualTo(input.url);
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier\n\nRename from my-verifier to my-renamed-verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n"
                + "\tname = my-renamed-verifier\n"
                + "\trepository = "
                + input.repository
                + "\n"
                + "\tdescription = A description.\n"
                + "\turl = http://example.com/my-verifier\n");
  }

  @Test
  public void updateVerifierName() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput input = new VerifierInput();
    input.name = "my-renamed-verifier";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier\n\nRename from my-verifier to my-renamed-verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo("[verifier]\n\tname = my-renamed-verifier\n\trepository = All-Projects\n");
  }

  @Test
  public void cannotSetVerifierNameToEmptyString() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.verifiers().id(verifierUuid).update(verifierInput);
  }

  @Test
  public void cannotSetVerifierNameToStringWhichIsEmptyAfterTrim() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.verifiers().id(verifierUuid).update(verifierInput);
  }

  @Test
  public void updateVerifierNameToNameThatIsAlreadyUsed() throws Exception {
    verifierOperations.newVerifier().name("other-verifier").create();

    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput input = new VerifierInput();
    input.name = "other-verifier";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier\n\nRename from my-verifier to other-verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo("[verifier]\n\tname = other-verifier\n\trepository = All-Projects\n");
  }

  @Test
  public void addVerifierDescription() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput input = new VerifierInput();
    input.description = "A description.";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n\tname = my-verifier\n"
                + "\trepository = All-Projects\n"
                + "\tdescription = A description.\n");
  }

  @Test
  public void updateVerifierDescription() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("my-verifier").description("A description.").create();

    VerifierInput input = new VerifierInput();
    input.description = "A new description.";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n\tname = my-verifier\n"
                + "\trepository = All-Projects\n"
                + "\tdescription = A new description.\n");
  }

  @Test
  public void unsetVerifierDescription() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("my-verifier").description("A description.").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.description = "";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(verifierInput);
    assertThat(info.description).isNull();

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo("[verifier]\n\tname = my-verifier\n\trepository = All-Projects\n");
  }

  @Test
  public void verifierDescriptionIsTrimmed() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput input = new VerifierInput();
    input.description = " A description. ";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.description).isEqualTo("A description.");

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n\tname = my-verifier\n"
                + "\trepository = All-Projects\n"
                + "\tdescription = A description.\n");
  }

  @Test
  public void addVerifierUrl() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput input = new VerifierInput();
    input.url = "http://example.com/my-verifier";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.url).isEqualTo(input.url);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n\tname = my-verifier\n"
                + "\trepository = All-Projects\n"
                + "\turl = http://example.com/my-verifier\n");
  }

  @Test
  public void updateVerifierUrl() throws Exception {
    String verifierUuid =
        verifierOperations
            .newVerifier()
            .name("my-verifier")
            .url("http://example.com/my-verifier")
            .create();

    VerifierInput input = new VerifierInput();
    input.url = "http://example.com/my-verifier-foo";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.url).isEqualTo(input.url);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n\tname = my-verifier\n"
                + "\trepository = All-Projects\n"
                + "\turl = http://example.com/my-verifier-foo\n");
  }

  @Test
  public void unsetVerifierUrl() throws Exception {
    String verifierUuid =
        verifierOperations
            .newVerifier()
            .name("my-verifier")
            .url("http://example.com/my-verifier")
            .create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.url = "";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(verifierInput);
    assertThat(info.url).isNull();

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo("[verifier]\n\tname = my-verifier\n\trepository = All-Projects\n");
  }

  @Test
  public void verifierUrlIsTrimmed() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput input = new VerifierInput();
    input.url = " http://example.com/my-verifier ";

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.url).isEqualTo("http://example.com/my-verifier");

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo(
            "[verifier]\n\tname = my-verifier\n"
                + "\trepository = All-Projects\n"
                + "\turl = http://example.com/my-verifier\n");
  }

  @Test
  public void updateRepository() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("my-verifier").repository(allProjects).create();

    VerifierInput input = new VerifierInput();
    input.repository = projectOperations.newProject().create().get();

    VerifierInfo info = gApi.verifiers().id(verifierUuid).update(input);
    assertThat(info.repository).isEqualTo(input.repository);

    PerVerifierOperations perVerifierOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVerifierOps.commit(),
        "Update verifier",
        info.updatedOn,
        perVerifierOps.get().refState());
    assertThat(perVerifierOps.configText())
        .isEqualTo("[verifier]\n\tname = my-verifier\n\trepository = " + input.repository + "\n");
  }

  @Test
  public void cannotSetRepositoryToEmptyString() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.repository = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository cannot be unset");
    gApi.verifiers().id(verifierUuid).update(verifierInput);
  }

  @Test
  public void cannotSetRepositoryToStringWhichIsEmptyAfterTrim() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.repository = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository cannot be unset");
    gApi.verifiers().id(verifierUuid).update(verifierInput);
  }

  @Test
  public void cannotSetNonExistingRepository() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.repository = "non-existing";

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("repository non-existing not found");
    gApi.verifiers().id(verifierUuid).update(verifierInput);
  }

  @Test
  public void updateVerifierWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    requestScopeOperations.setApiUser(user.getId());

    VerifierInput input = new VerifierInput();
    input.name = "my-renamed-verifier";

    exception.expect(AuthException.class);
    exception.expectMessage("administrate verifiers not permitted");
    gApi.verifiers().id(verifierUuid).update(input);
  }
}
