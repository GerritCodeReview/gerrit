// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.ComparisonChain;

public class AccountExternalIdInfo implements Comparable<AccountExternalIdInfo> {
  public String externalId;
  public int accountId;
  public String email;
  public String password;
  public boolean trusted;
  public boolean canDelete;

  @Override
  public int compareTo(AccountExternalIdInfo a) {
    return ComparisonChain.start()
          .compare(a.accountId, accountId)
          .compare(a.externalId, externalId)
          .result();
  }

  @Override
  public String toString() {
    return "AccountExternalIdInfo{" +
        "external_id=" + externalId +
        ", account_id=" + accountId +
        ", email=" + email +
        ", password=" + password +
        ", trusted=" + trusted +
        ", can_delete=" + canDelete +
        '}';
  }
}
