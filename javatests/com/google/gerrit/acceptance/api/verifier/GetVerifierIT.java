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
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class GetVerifierIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private VerifierOperations verifierOperations;

  @Test
  public void getVerifier() throws Exception {
    String name = "my-verifier";
    String uuid = verifierOperations.newVerifier().name(name).create();

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
    String uuid = verifierOperations.newVerifier().name(name).description(description).create();

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
    verifierOperations.newVerifier().name(name).create();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    gApi.verifiers().id(name);
  }

  @Test
  public void getVerifierWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    String name = "my-verifier";
    String uuid = verifierOperations.newVerifier().name(name).create();

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + uuid);
    gApi.verifiers().id(uuid);
  }
}
