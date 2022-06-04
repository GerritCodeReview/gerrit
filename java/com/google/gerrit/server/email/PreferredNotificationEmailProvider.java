// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.email;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Provides the preferred notification email address for the specified account. Invoked by Gerrit
 * when preferred email requests are made.
 */
@ExtensionPoint
public interface PreferredNotificationEmailProvider {

  /**
   * Get preferred notification email address.
   *
   * @param account The account for which to load a preferred notification email address.
   * @return The preferred notification email address for the specified account.
   */
  String getPreferredNotificationEmail(Account account);
}
