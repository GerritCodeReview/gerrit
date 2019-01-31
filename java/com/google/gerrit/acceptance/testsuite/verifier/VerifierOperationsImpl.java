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

package com.google.gerrit.acceptance.testsuite.verifier;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierUUID;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gerrit.server.verifier.db.VerifierCreation;
import com.google.gerrit.server.verifier.db.VerifierUpdate;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * The implementation of {@code VerifierOperations}.
 *
 * <p>There is only one implementation of {@code VerifierOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class VerifierOperationsImpl implements VerifierOperations {
  private final Verifiers verifiers;
  private final VerifiersUpdate verifiersUpdate;

  @Inject
  public VerifierOperationsImpl(
      Verifiers verifiers, @ServerInitiated VerifiersUpdate verifiersUpdate) {
    this.verifiers = verifiers;
    this.verifiersUpdate = verifiersUpdate;
  }

  @Override
  public PerVerifierOperations verifier(String verifierUuid) {
    return new PerVerifierOperationsImpl(verifierUuid);
  }

  @Override
  public TestVerifierCreation.Builder newVerifier() {
    return TestVerifierCreation.builder(this::createNewVerifier);
  }

  private String createNewVerifier(TestVerifierCreation testVerifierCreation)
      throws ConfigInvalidException, IOException {
    VerifierCreation verifierCreation = toVerifierCreation(testVerifierCreation);
    VerifierUpdate verifierUpdate = toVerifierUpdate(testVerifierCreation);
    Verifier verifier = verifiersUpdate.createVerifier(verifierCreation, verifierUpdate);
    return verifier.getUuid();
  }

  private VerifierCreation toVerifierCreation(TestVerifierCreation verifierCreation) {
    String verifierUuid = VerifierUUID.make("test-verifier");
    String verifierName = verifierCreation.name().orElse("verifier-with-uuid-" + verifierUuid);
    return VerifierCreation.builder().setVerifierUuid(verifierUuid).setName(verifierName).build();
  }

  private static VerifierUpdate toVerifierUpdate(TestVerifierCreation verifierCreation) {
    VerifierUpdate.Builder builder = VerifierUpdate.builder();
    verifierCreation.name().ifPresent(builder::setName);
    verifierCreation.description().ifPresent(builder::setDescription);
    return builder.build();
  }

  private class PerVerifierOperationsImpl implements PerVerifierOperations {
    private final String verifierUuid;

    PerVerifierOperationsImpl(String verifierUuid) {
      this.verifierUuid = verifierUuid;
    }

    @Override
    public boolean exists() {
      return getVerifier(verifierUuid).isPresent();
    }

    @Override
    public TestVerifier get() {
      Optional<Verifier> verifier = getVerifier(verifierUuid);
      checkState(verifier.isPresent(), "Tried to get non-existing test verifier");
      return toTestVerifier(verifier.get());
    }

    private Optional<Verifier> getVerifier(String verifierUuid) {
      try {
        return verifiers.get(verifierUuid);
      } catch (IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }

    private TestVerifier toTestVerifier(Verifier verifier) {
      return TestVerifier.builder()
          .verifierUuid(verifier.getUuid())
          .name(verifier.getName())
          .description(verifier.getDescription())
          .createdOn(verifier.getCreatedOn())
          .build();
    }
  }
}
