// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.inject.Inject;

public class AccountFormatter {

  public interface Factory {
    AccountFormatter create();
  }

  private final String anonymousCowardName;

  @Inject
  public AccountFormatter(final @AnonymousCowardName String anonymousCowardName) {
    this.anonymousCowardName = anonymousCowardName;
  }

  public String formatWithFullnameEmailAndId(final Account a) {
    final StringBuilder sb = new StringBuilder();
    if (a.getFullName() != null && !a.getFullName().isEmpty()) {
      sb.append(a.getFullName());
    } else {
      sb.append(anonymousCowardName);
    }
    if (a.getPreferredEmail() != null && !a.getPreferredEmail().isEmpty()) {
      sb.append(" <" + a.getPreferredEmail() + ">");
    }
    sb.append(" (" + a.getId() + ")");
    return sb.toString();
  }
}
