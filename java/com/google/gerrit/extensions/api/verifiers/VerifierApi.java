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

package com.google.gerrit.extensions.api.verifiers;

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface VerifierApi {
  /** @return verifier info. */
  VerifierInfo get() throws RestApiException;

  /**
   * Updates a verifier.
   *
   * <p>This method supports partial updates of the verifier property set. Only properties that are
   * set in the given input are updated. Properties that are not set in the input (that have `null`
   * as value) are not touched.
   *
   * <p>Unsetting properties:
   *
   * <ul>
   *   <li>{@code name}: Cannot be unset. Attempting to set it to an empty string ("") or a string
   *       that is empty after trim is rejected as bad request.
   *   <li>{@code description}: Can be unset by setting an empty string ("") for it.
   *   <li>{@code url}: Can be unset by setting an empty string ("") for it.
   * </ul>
   *
   * @param input input with updated properties
   * @return updated verifier info
   */
  VerifierInfo update(VerifierInput input) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements VerifierApi {
    @Override
    public VerifierInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public VerifierInfo update(VerifierInput input) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
