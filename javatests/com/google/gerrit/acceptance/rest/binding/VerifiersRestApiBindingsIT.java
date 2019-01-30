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

package com.google.gerrit.acceptance.rest.binding;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import org.junit.Test;

public class VerifiersRestApiBindingsIT extends AbstractDaemonTest {
  private static final ImmutableList<RestCall> VERIFIER_ENDPOINTS =
      ImmutableList.of(RestCall.get("/verifiers/%s"));

  @Test
  public void verifierEndpoints() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = " my-verifier ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    String verifierUuid = info.uuid;

    RestApiCallHelper.execute(adminRestSession, VERIFIER_ENDPOINTS, verifierUuid);
  }
}
