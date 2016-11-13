// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.client.info.AccountInfo;

public class AccountFormatter {
  private final String anonymousCowardName;

  public AccountFormatter(String anonymousCowardName) {
    this.anonymousCowardName = anonymousCowardName;
  }

  /**
   * Formats an account as a name and an email address.
   *
   * <p>Example output:
   *
   * <ul>
   *   <li>{@code A U. Thor &lt;author@example.com&gt;}: full populated
   *   <li>{@code A U. Thor (12)}: missing email address
   *   <li>{@code Anonymous Coward &lt;author@example.com&gt;}: missing name
   *   <li>{@code Anonymous Coward (12)}: missing name and email address
   * </ul>
   */
  public String nameEmail(AccountInfo info) {
    String name = info.name();
    if (name == null || name.trim().isEmpty()) {
      name = anonymousCowardName;
    }

    StringBuilder b = new StringBuilder().append(name);
    if (info.email() != null) {
      b.append(" <").append(info.email()).append(">");
    } else if (info._accountId() > 0) {
      b.append(" (").append(info._accountId()).append(")");
    }
    return b.toString();
  }

  /**
   * Formats an account name.
   *
   * <p>If the account has a full name, it returns only the full name. Otherwise it returns a longer
   * form that includes the email address.
   */
  public String name(AccountInfo ai) {
    if (ai.name() != null && !ai.name().trim().isEmpty()) {
      return ai.name();
    }
    String email = ai.email();
    if (email != null) {
      int at = email.indexOf('@');
      return 0 < at ? email.substring(0, at) : email;
    }
    return nameEmail(ai);
  }
}
