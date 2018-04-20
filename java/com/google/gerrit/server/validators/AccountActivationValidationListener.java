// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.account.AccountState;

/**
 * Validator that is invoked when an account activated or deactivated via the Gerrit REST API or the
 * Java extension API.
 */
@ExtensionPoint
public interface AccountActivationValidationListener {
  /**
   * Called when an account should be activated to allow validation of the account activation.
   *
   * @param account the account that should be activated
   * @throws ValidationException if validation fails
   */
  void validateActivation(AccountState account) throws ValidationException;

  /**
   * Called when an account should be deactivated to allow validation of the account deactivation.
   *
   * @param account the account that should be deactivated
   * @throws ValidationException if validation fails
   */
  void validateDeactivation(AccountState account) throws ValidationException;
}
