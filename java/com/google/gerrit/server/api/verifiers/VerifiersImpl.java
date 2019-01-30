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
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.api.verifiers.Verifiers;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.restapi.verifier.CreateVerifier;
import com.google.gerrit.server.restapi.verifier.VerifiersCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class VerifiersImpl implements Verifiers {
  private final VerifierApiImpl.Factory api;
  private final CreateVerifier createVerifier;
  private final VerifiersCollection verifiers;

  @Inject
  VerifiersImpl(
      VerifierApiImpl.Factory api, CreateVerifier createVerifier, VerifiersCollection verifiers) {
    this.api = api;
    this.createVerifier = createVerifier;
    this.verifiers = verifiers;
  }

  @Override
  public VerifierApi id(String id) throws RestApiException {
    try {
      return api.create(verifiers.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve verifier " + id, e);
    }
  }

  @Override
  public VerifierApi create(VerifierInput input) throws RestApiException {
    try {
      VerifierInfo info =
          createVerifier
              .apply(TopLevelResource.INSTANCE, IdString.fromDecoded(input.name), input)
              .value();
      return id(info.uuid);
    } catch (Exception e) {
      throw asRestApiException("Cannot create verifier " + input.name, e);
    }
  }
}
