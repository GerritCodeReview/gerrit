// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.api.accounts;

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface Accounts {
  /**
   * Look up an account by ID.
   * <p>
   * <strong>Note:</strong> This method eagerly reads the account. Methods that
   * mutate the account do not necessarily re-read the account. Therefore, calling
   * a getter method on an instance after calling a mutation method on that same
   * instance is not guaranteed to reflect the mutation. It is not recommended
   * to store references to {@code AccountApi} instances.
   *
   * @param id any identifier supported by the REST API, including numeric ID,
   *     email, or username.
   * @return API for accessing the account.
   * @throws RestApiException if an error occurred.
   */
  AccountApi id(String id) throws RestApiException;

  /**
   * Look up the account of the current in-scope user.
   *
   * @see #id(String)
   */
  AccountApi self() throws RestApiException;

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements Accounts {
    @Override
    public AccountApi id(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public AccountApi self() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
