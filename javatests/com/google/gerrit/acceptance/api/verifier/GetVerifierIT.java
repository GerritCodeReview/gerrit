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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class GetVerifierIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getVerifier() throws Exception {
    String name = "my-verifier";
    String uuid = createVerifier(name);

    VerifierInfo info = gApi.verifiers().id(uuid).get();
    assertThat(info.uuid).isEqualTo(uuid);
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getVerifierWithDescription() throws Exception {
    String name = "my-verifier";
    String description = "some description";
    String uuid = createVerifier(name, description);

    VerifierInfo info = gApi.verifiers().id(uuid).get();
    assertThat(info.uuid).isEqualTo(uuid);
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isEqualTo(description);
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getNonExistingVerifierFails() throws Exception {
    String name = "non-existing";

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    gApi.verifiers().id(name);
  }

  @Test
  public void getVerifierByNameFails() throws Exception {
    String name = "my-verifier";
    createVerifier(name);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    gApi.verifiers().id(name);
  }

  @Test
  public void getVerifierWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    String name = "my-verifier";
    String uuid = createVerifier(name);

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + uuid);
    gApi.verifiers().id(uuid);
  }

  private String createVerifier(String name) throws RestApiException {
    return createVerifier(name, null);
  }

  private String createVerifier(String name, @Nullable String description) throws RestApiException {
    // TODO(ekempin): create test API for verifiers and use it here
    VerifierInput input = new VerifierInput();
    input.name = name;
    input.description = description;
    VerifierInfo info = gApi.verifiers().create(input).get();
    return info.uuid;
  }
}
