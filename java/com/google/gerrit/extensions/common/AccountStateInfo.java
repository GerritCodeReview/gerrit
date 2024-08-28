// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import java.util.List;
import java.util.Map;

/**
 * Representation of an account state in the REST API.
 *
 * <p>This class determines the JSON format of account states in the REST API.
 */
public class AccountStateInfo {
  /** The account details. */
  public AccountDetailInfo account;

  /** The global capabilities of the account. */
  public Map<String, Object> capabilities;

  /** The groups that contain the account as a member. */
  public List<GroupInfo> groups;

  /** The external IDs of the account. */
  public List<AccountExternalIdInfo> externalIds;

  /** Account metadata populated by plugins. */
  public List<AccountMetadataInfo> metadata;
}
