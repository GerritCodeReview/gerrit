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
import java.util.Objects;

public class AccountExternalIdInfo implements Comparable<AccountExternalIdInfo> {
  public String identity;
  public String emailAddress;
  public Boolean trusted;
  public Boolean canDelete;

  @Override
  public int compareTo(AccountExternalIdInfo a) {
    return ComparisonChain.start()
        .compare(a.identity, identity)
        .compare(a.emailAddress, emailAddress)
        .result();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AccountExternalIdInfo) {
      AccountExternalIdInfo a = (AccountExternalIdInfo) o;
      return (Objects.equals(a.identity, identity))
          && (Objects.equals(a.emailAddress, emailAddress))
          && (Objects.equals(a.trusted, trusted))
          && (Objects.equals(a.canDelete, canDelete));
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(identity, emailAddress, trusted, canDelete);
  }
}
