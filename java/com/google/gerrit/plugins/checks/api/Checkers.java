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

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;

public interface Checkers {
  /**
   * Look up a checker by ID.
   *
   * <p><strong>Note:</strong> This method eagerly reads the checker. Methods that mutate the
   * checker do not necessarily re-read the checker. Therefore, calling a getter method on an
   * instance after calling a mutation method on that same instance is not guaranteed to reflect the
   * mutation. It is not recommended to store references to {@code checkerApi} instances.
   *
   * @param id any identifier supported by the REST API, including checker UUID.
   * @return API for accessing the checker.
   * @throws RestApiException if an error occurred.
   */
  CheckerApi id(String id) throws RestApiException;

  /** Create a new checker. */
  CheckerApi create(CheckerInput input) throws RestApiException;

  /** Returns a list of all checkers, sorted by UUID. */
  List<CheckerInfo> all() throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements Checkers {
    @Override
    public CheckerApi id(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CheckerApi create(CheckerInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<CheckerInfo> all() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
