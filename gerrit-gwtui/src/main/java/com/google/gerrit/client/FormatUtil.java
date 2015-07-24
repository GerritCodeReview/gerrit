// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gerrit.client.info.AccountPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;

import java.util.Date;

/** Misc. formatting functions. */
public class FormatUtil {
  private static DateFormatter dateFormatter;

  public static void setPreferences(AccountPreferencesInfo prefs) {
    dateFormatter = new DateFormatter(prefs);
  }

  /** Format a date using a really short format. */
  public static String shortFormat(Date dt) {
    ensureInited();
    return dateFormatter.shortFormat(dt);
  }

  /** Format a date using a really short format. */
  public static String shortFormatDayTime(Date dt) {
    ensureInited();
    return dateFormatter.shortFormatDayTime(dt);
  }

  /** Format a date using the locale's medium length format. */
  public static String mediumFormat(Date dt) {
    ensureInited();
    return dateFormatter.mediumFormat(dt);
  }

  private static void ensureInited() {
    if (dateFormatter == null) {
      setPreferences(Gerrit.getUserPreferences());
    }
  }

  /** Format a date using git log's relative date format. */
  public static String relativeFormat(Date dt) {
    return RelativeDateFormatter.format(dt);
  }

  /**
   * Formats an account as a name and an email address.
   * <p>
   * Example output:
   * <ul>
   * <li>{@code A U. Thor &lt;author@example.com&gt;}: full populated</li>
   * <li>{@code A U. Thor (12)}: missing email address</li>
   * <li>{@code Anonymous Coward &lt;author@example.com&gt;}: missing name</li>
   * <li>{@code Anonymous Coward (12)}: missing name and email address</li>
   * </ul>
   */
  public static String nameEmail(AccountInfo info) {
    String name = info.name();
    if (name == null || name.trim().isEmpty()) {
      name = Gerrit.info().user().anonymousCowardName();
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
   * <p>
   * If the account has a full name, it returns only the full name. Otherwise it
   * returns a longer form that includes the email address.
   */
  public static String name(Account acct) {
    return name(asInfo(acct));
  }

  /**
   * Formats an account name.
   * <p>
   * If the account has a full name, it returns only the full name. Otherwise it
   * returns a longer form that includes the email address.
   */
  public static String name(AccountInfo ai) {
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

  public static AccountInfo asInfo(Account acct) {
    if (acct == null) {
      return AccountInfo.create(0, null, null, null);
    }
    return AccountInfo.create(
        acct.getId() != null ? acct.getId().get() : 0,
        acct.getFullName(),
        acct.getPreferredEmail(),
        acct.getUserName());
  }

  public static AccountInfo asInfo(com.google.gerrit.common.data.AccountInfo acct) {
    if (acct == null) {
      return AccountInfo.create(0, null, null, null);
    }
    return AccountInfo.create(
        acct.getId() != null ? acct.getId().get() : 0,
        acct.getFullName(),
        acct.getPreferredEmail(),
        acct.getUsername());
  }
}
