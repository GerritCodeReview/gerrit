// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class DeleteSubmitRequirementIT extends AbstractDaemonTest {

  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void anonymous() throws Exception {
    createSubmitRequirement("code-review");

    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("code-review").delete());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void nonExisting() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("non-existing").delete());
    assertThat(thrown).hasMessageThat().contains("Not found: non-existing");
  }

  @Test
  public void delete() throws Exception {
    createSubmitRequirement("code-review");
    createSubmitRequirement("verified");

    List<SubmitRequirementInfo> infos =
        gApi.projects().name(project.get()).submitRequirements().get();
    assertThat(names(infos)).containsExactly("code-review", "verified");

    gApi.projects().name(project.get()).submitRequirement("code-review").delete();
    infos = gApi.projects().name(project.get()).submitRequirements().get();
    assertThat(names(infos)).containsExactly("verified");
  }

  private SubmitRequirementInfo createSubmitRequirement(String srName) throws RestApiException {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = srName;
    input.submittabilityExpression = "label:dummy=+2";

    return gApi.projects().name(project.get()).submitRequirement(srName).create(input).get();
  }

  private List<String> names(List<SubmitRequirementInfo> infos) {
    return infos.stream().map(sr -> sr.name).collect(Collectors.toList());
  }
}
