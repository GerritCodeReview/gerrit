// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.server.permissions.PermissionBackendException;
import java.util.Set;

/**
 * Directory of user account information.
 *
 * <p>Implementations supply data to Gerrit about user accounts.
 */
public abstract class AccountDirectory {
  /** Fields to be populated for a REST API response. */
  public enum FillOptions {
    /** Full name or username. */
    NAME,

    /** Preferred email address to contact the user at. */
    EMAIL,

    /** All secondary email addresses of the user. */
    SECONDARY_EMAILS,

    /** User profile images. */
    AVATARS,

    /** Unique user identity to login to Gerrit, may be deprecated. */
    USERNAME,

    /** Numeric account ID, may be deprecated. */
    ID,

    /** The user-settable status of this account (e.g. busy, OOO, available) */
    STATUS,

    /** The state of the account (e.g. active or inactive) */
    STATE,

    /** Human friendly display name presented in the web interface chosen by the user. */
    DISPLAY_NAME
  }

  public abstract void fillAccountInfo(Iterable<? extends AccountInfo> in, Set<FillOptions> options)
      throws PermissionBackendException;
}
