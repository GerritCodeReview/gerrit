// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Notified whenever an account got activated or deactivated.
 *
 * <p>This listener is called only after an account got (de)activated and hence cannot cancel the
 * (de)activation. See {@link
 * com.google.gerrit.server.validators.AccountActivationValidationListener} for a listener that can
 * cancel a (de)activation.
 */
@ExtensionPoint
public interface AccountActivationListener {
  /**
   * Invoked after an account got activated
   *
   * @param id of the account
   */
  default void onAccountActivated(int id) {}

  /**
   * Invoked after an account got deactivated
   *
   * @param id of the account
   */
  default void onAccountDeactivated(int id) {}
}
