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

import com.google.gerrit.extensions.api.verifiers.UpdateVerifierOption;
import com.google.gerrit.extensions.api.verifiers.VerifierApi;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.restapi.verifier.GetVerifier;
import com.google.gerrit.server.restapi.verifier.UpdateVerifier;
import com.google.gerrit.server.restapi.verifier.VerifierResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.EnumSet;

class VerifierApiImpl implements VerifierApi {
  interface Factory {
    VerifierApiImpl create(VerifierResource rsrc);
  }

  private final GetVerifier getVerifier;
  private final Provider<UpdateVerifier> updateVerifierProvider;
  private final VerifierResource rsrc;

  @Inject
  VerifierApiImpl(
      GetVerifier getVerifier,
      Provider<UpdateVerifier> updateVerifier,
      @Assisted VerifierResource rsrc) {
    this.getVerifier = getVerifier;
    this.updateVerifierProvider = updateVerifier;
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

  @Override
  public VerifierInfo update(VerifierInput input, EnumSet<UpdateVerifierOption> options)
      throws RestApiException {
    try {
      UpdateVerifier updateVerifier = updateVerifierProvider.get();
      options.forEach(o -> updateVerifier.addOption(o));
      return updateVerifier.apply(rsrc, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot update verifier", e);
    }
  }
}
