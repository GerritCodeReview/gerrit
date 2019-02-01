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
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.verifier.TestVerifier;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations.PerVerifierOperations;
import com.google.gerrit.extensions.api.verifiers.UpdateVerifierOption;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class UpdateVerifierIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private VerifierOperations verifierOperations;

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void updateVerifierName() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInput input = new VerifierInput();
    input.name = "my-renamed-verifier";

    VerifierInfo info =
        gApi.verifiers().id(verifierUuid).update(input, EnumSet.of(UpdateVerifierOption.NAME));
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(verifier.description().orElse(null));
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVeriferOps.commit(),
        "Update verifier\n\nRename from my-verifier to my-renamed-verifier",
        info.updatedOn,
        perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText()).isEqualTo("[verifier]\n\tname = my-renamed-verifier\n");
  }

  @Test
  public void cannotUnsetVerifierName() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.verifiers()
        .id(verifierUuid)
        .update(new VerifierInput(), EnumSet.of(UpdateVerifierOption.NAME));
  }

  @Test
  public void cannotSetVerifierNameToEmptyString() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.verifiers().id(verifierUuid).update(verifierInput, EnumSet.of(UpdateVerifierOption.NAME));
  }

  @Test
  public void cannotSetVerifierNameToStringWhichIsEmptyAfterTrim() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name cannot be unset");
    gApi.verifiers().id(verifierUuid).update(verifierInput, EnumSet.of(UpdateVerifierOption.NAME));
  }

  @Test
  public void updateVerifierNameToNameThatIsAlreadyUsed() throws Exception {
    verifierOperations.newVerifier().name("other-verifier").create();

    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInput input = new VerifierInput();
    input.name = "other-verifier";

    VerifierInfo info =
        gApi.verifiers().id(verifierUuid).update(input, EnumSet.of(UpdateVerifierOption.NAME));
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(verifier.description().orElse(null));
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVeriferOps.commit(),
        "Update verifier\n\nRename from my-verifier to other-verifier",
        info.updatedOn,
        perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText()).isEqualTo("[verifier]\n\tname = other-verifier\n");
  }

  @Test
  public void cannotUpdateVerifierNameWithoutSettingNameOption() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.name = "my-renamed-verifier";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is set although NAME option is not present");
    gApi.verifiers()
        .id(verifierUuid)
        .update(verifierInput, EnumSet.noneOf(UpdateVerifierOption.class));
  }

  @Test
  public void addVerifierDescription() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInput input = new VerifierInput();
    input.description = "A description.";

    VerifierInfo info =
        gApi.verifiers()
            .id(verifierUuid)
            .update(input, EnumSet.of(UpdateVerifierOption.DESCRIPTION));
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(verifier.name());
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVeriferOps.commit(), "Update verifier", info.updatedOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo("[verifier]\n\tname = my-verifier\n\tdescription = A description.\n");
  }

  @Test
  public void updateVerifierDescription() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("my-verifier").description("A description.").create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInput input = new VerifierInput();
    input.description = "A new description.";

    VerifierInfo info =
        gApi.verifiers()
            .id(verifierUuid)
            .update(input, EnumSet.of(UpdateVerifierOption.DESCRIPTION));
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(verifier.name());
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVeriferOps.commit(), "Update verifier", info.updatedOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo("[verifier]\n\tname = my-verifier\n\tdescription = A new description.\n");
  }

  @Test
  public void unsetVerifierDescription() throws Exception {
    String verifierUuid =
        verifierOperations.newVerifier().name("my-verifier").description("A description.").create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInfo info =
        gApi.verifiers()
            .id(verifierUuid)
            .update(new VerifierInput(), EnumSet.of(UpdateVerifierOption.DESCRIPTION));
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(verifier.name());
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVeriferOps.commit(), "Update verifier", info.updatedOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText()).isEqualTo("[verifier]\n\tname = my-verifier\n");
  }

  @Test
  public void cannotUpdateVerifierDescriptionWithoutSettingDescriptionOption() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    VerifierInput verifierInput = new VerifierInput();
    verifierInput.description = "A description.";

    exception.expect(BadRequestException.class);
    exception.expectMessage("description is set although DESCRIPTION option is not present");
    gApi.verifiers()
        .id(verifierUuid)
        .update(verifierInput, EnumSet.noneOf(UpdateVerifierOption.class));
  }

  @Test
  public void updateMultipleVerifierPropertiesAtOnce() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();
    TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();

    VerifierInput input = new VerifierInput();
    input.name = "my-renamed-verifier";
    input.description = "A description.";

    VerifierInfo info =
        gApi.verifiers()
            .id(verifierUuid)
            .update(input, EnumSet.of(UpdateVerifierOption.NAME, UpdateVerifierOption.DESCRIPTION));
    assertThat(info.uuid).isEqualTo(verifierUuid);
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.createdOn).isEqualTo(verifier.createdOn());
    assertThat(info.createdOn).isLessThan(info.updatedOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(verifierUuid);
    assertCommit(
        perVeriferOps.commit(),
        "Update verifier\n\nRename from my-verifier to my-renamed-verifier",
        info.updatedOn,
        perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo("[verifier]\n\tname = my-renamed-verifier\n\tdescription = A description.\n");
  }

  @Test
  public void updateVerifierWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("my-verifier").create();

    requestScopeOperations.setApiUser(user.getId());

    VerifierInput input = new VerifierInput();
    input.name = "my-renamed-verifier";

    exception.expect(AuthException.class);
    exception.expectMessage("administrate verifiers not permitted");
    gApi.verifiers().id(verifierUuid).update(input, EnumSet.of(UpdateVerifierOption.NAME));
  }
}
