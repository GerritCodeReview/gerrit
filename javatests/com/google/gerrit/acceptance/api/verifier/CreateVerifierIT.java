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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class CreateVerifierIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void createVerifier() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.uuid).isNotNull();
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void createVerifierWithDescription() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.description = "some description";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.description).isEqualTo(input.description);
  }

  @Test
  public void createVerifierNameIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = " my-verifier ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.name).isEqualTo("my-verifier");
  }

  @Test
  public void createVerifierDescriptionIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.description = " some description ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.description).isEqualTo("some description");
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
