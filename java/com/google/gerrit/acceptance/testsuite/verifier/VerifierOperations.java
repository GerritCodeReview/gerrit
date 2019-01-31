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

/**
 * An aggregation of operations on verifiers for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface VerifierOperations {
  /**
   * Starts the fluent chain for querying or modifying a verifier. Please see the methods of {@link
   * PerVerifierOperations} for details on possible operations.
   *
   * @return an aggregation of operations on a specific verifier
   */
  PerVerifierOperations verifier(String verifierUuid);

  /**
   * Starts the fluent chain to create a verifier. The returned builder can be used to specify the
   * attributes of the new verifier. To create the verifier for real, {@link
   * TestVerifierCreation.Builder#create()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * String createdVerifierUuid = verifierOperations
   *     .newVerifier()
   *     .name("my-verifier")
   *     .description("A simple verifier.")
   *     .create();
   * </pre>
   *
   * <p><strong>Note:</strong> If another verifier with the provided name already exists, the
   * creation of the verifier will succeed since verifier names are not unique.
   *
   * @return a builder to create the new verifier
   */
  TestVerifierCreation.Builder newVerifier();

  /** An aggregation of methods on a specific verifier. */
  interface PerVerifierOperations {

    /**
     * Checks whether the verifier exists.
     *
     * @return {@code true} if the verifier exists
     */
    boolean exists();

    /**
     * Retrieves the verifier.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested verifier
     * doesn't exist. If you want to check for the existence of a verifier, use {@link #exists()}
     * instead.
     *
     * @return the corresponding {@code TestVerifier}
     */
    TestVerifier get();
  }
}
