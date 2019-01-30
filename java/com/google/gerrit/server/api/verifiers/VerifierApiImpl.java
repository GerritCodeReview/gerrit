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

package com.google.gerrit.server.api.verifiers;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.verifiers.VerifierApi;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.restapi.verifier.GetVerifier;
import com.google.gerrit.server.restapi.verifier.VerifierResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class VerifierApiImpl implements VerifierApi {
  interface Factory {
    VerifierApiImpl create(VerifierResource rsrc);
  }

  private final GetVerifier getVerifier;
  private final VerifierResource rsrc;

  @Inject
  VerifierApiImpl(GetVerifier getVerifier, @Assisted VerifierResource rsrc) {
    this.getVerifier = getVerifier;
    this.rsrc = rsrc;
  }

  @Override
  public VerifierInfo get() throws RestApiException {
    try {
      return getVerifier.apply(rsrc);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve verifier", e);
    }
  }
}
