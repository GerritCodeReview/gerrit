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

package com.google.gerrit.server.account;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.common.AccountStateInfo;

/**
 * Extension point to retrieve account state that should be included into the account state that is
 * returned by the {@link com.google.gerrit.server.restapi.account.GetState} REST endpoint.
 */
@ExtensionPoint
public interface AccountStateProvider {
  /** Returns metadata to populate {@link AccountStateInfo#metadata}. */
  public ImmutableMap<String, String> getMetadata(Account.Id accountId);
}
