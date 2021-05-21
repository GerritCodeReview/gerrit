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
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;

@NoHttpd
public class ListSubmitRequirementsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirements().get());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void notAllowed() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirements().get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void list() throws Exception {
    createSubmitRequirement("sr-1");
    createSubmitRequirement("sr-2");

    List<SubmitRequirementInfo> infos =
        gApi.projects().name(project.get()).submitRequirements().get();

    assertThat(names(infos)).containsExactly("sr-1", "sr-2");
  }

  @Test
  public void listWithInheritance() throws Exception {
    createSubmitRequirement(allProjects.get(), "base-sr");
    createSubmitRequirement(project.get(), "sr-1");
    createSubmitRequirement(project.get(), "sr-2");

    List<SubmitRequirementInfo> infos =
        gApi.projects().name(project.get()).submitRequirements().withInherited(false).get();

    assertThat(names(infos)).containsExactly("sr-1", "sr-2");

    infos = gApi.projects().name(project.get()).submitRequirements().withInherited(true).get();

    assertThat(names(infos)).containsExactly("base-sr", "sr-1", "sr-2");
  }

  @Ignore
  @Test
  public void defaultSubmitRequirements() throws Exception {
    // TODO(ghareeb): add after implementing default submit requirements
  }

  private SubmitRequirementInfo createSubmitRequirement(String srName) throws RestApiException {
    return createSubmitRequirement(project.get(), srName);
  }

  private SubmitRequirementInfo createSubmitRequirement(String project, String srName)
      throws RestApiException {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = srName;
    input.submittabilityExpression = "label:dummy=+2";

    return gApi.projects().name(project).submitRequirement(srName).create(input).get();
  }

  private List<String> names(List<SubmitRequirementInfo> infos) {
    return infos.stream().map(sr -> sr.name).collect(Collectors.toList());
  }
}
