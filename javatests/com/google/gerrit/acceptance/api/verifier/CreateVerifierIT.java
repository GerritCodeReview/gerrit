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
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations.PerVerifierOperations;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class CreateVerifierIT extends AbstractDaemonTest {
  @Inject private VerifierOperations verifierOperations;
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
  public void createVerifier() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.uuid).isNotNull();
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isNull();
    assertThat(info.url).isNull();
    assertThat(info.createdOn).isNotNull();
    assertThat(info.updatedOn).isEqualTo(info.createdOn);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(info.uuid);
    assertCommit(
        perVeriferOps.commit(), "Create verifier", info.createdOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText()).isEqualTo("[verifier]\n\tname = my-verifier\n");
  }

  @Test
  public void createVerifierWithDescription() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.description = "some description";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(info.uuid);
    assertCommit(
        perVeriferOps.commit(), "Create verifier", info.createdOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo(
            "[verifier]\n" + "\tname = my-verifier\n" + "\tdescription = some description\n");
  }

  @Test
  public void createVerifierWithUrl() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.url = "http://example.com/my-verifier";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.url).isEqualTo(input.url);

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(info.uuid);
    assertCommit(
        perVeriferOps.commit(), "Create verifier", info.createdOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo(
            "[verifier]\n" + "\tname = my-verifier\n" + "\turl = http://example.com/my-verifier\n");
  }

  @Test
  public void createVerifierNameIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = " my-verifier ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.name).isEqualTo("my-verifier");

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(info.uuid);
    assertCommit(
        perVeriferOps.commit(), "Create verifier", info.createdOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText()).isEqualTo("[verifier]\n\tname = my-verifier\n");
  }

  @Test
  public void createVerifierDescriptionIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.description = " some description ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.description).isEqualTo("some description");

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(info.uuid);
    assertCommit(
        perVeriferOps.commit(), "Create verifier", info.createdOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo(
            "[verifier]\n" + "\tname = my-verifier\n" + "\tdescription = some description\n");
  }

  @Test
  public void createVerifierUrlIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.url = " http://example.com/my-verifier ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.url).isEqualTo("http://example.com/my-verifier");

    PerVerifierOperations perVeriferOps = verifierOperations.verifier(info.uuid);
    assertCommit(
        perVeriferOps.commit(), "Create verifier", info.createdOn, perVeriferOps.get().refState());
    assertThat(perVeriferOps.configText())
        .isEqualTo(
            "[verifier]\n" + "\tname = my-verifier\n" + "\turl = http://example.com/my-verifier\n");
  }

  @Test
  public void createVerifiersWithSameName() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    VerifierInfo info1 = gApi.verifiers().create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    VerifierInfo info2 = gApi.verifiers().create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createVerifierWithoutNameFails() throws Exception {
    VerifierInput input = new VerifierInput();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.verifiers().create(input);
  }

  @Test
  public void createVerifierWithEmptyNameFails() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.verifiers().create(input);
  }

  @Test
  public void createVerifierWithEmptyNameAfterTrimFails() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.verifiers().create(input);
  }

  @Test
  public void createVerifierWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";

    exception.expect(AuthException.class);
    exception.expectMessage("administrate verifiers not permitted");
    gApi.verifiers().create(input);
  }
}
