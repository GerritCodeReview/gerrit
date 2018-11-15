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

package com.google.gerrit.plugin.client;

import com.google.gerrit.client.AccountFormatter;
import com.google.gerrit.client.DateFormatter;
import com.google.gerrit.client.RelativeDateFormatter;
import com.google.gerrit.client.info.AccountInfo;
import java.util.Date;

public class FormatUtil {
  private static final AccountFormatter accountFormatter =
      new AccountFormatter(Plugin.get().getServerInfo().user().anonymousCowardName());

  /** Format a date using a really short format. */
  public static String shortFormat(Date dt) {
    return createDateFormatter().shortFormat(dt);
  }

  /** Format a date using a really short format. */
  public static String shortFormatDayTime(Date dt) {
    return createDateFormatter().shortFormatDayTime(dt);
  }

  /** Format a date using the locale's medium length format. */
  public static String mediumFormat(Date dt) {
    return createDateFormatter().mediumFormat(dt);
  }

  private static DateFormatter createDateFormatter() {
    return new DateFormatter(Plugin.get().getUserPreferences());
  }

  /** Format a date using git log's relative date format. */
  public static String relativeFormat(Date dt) {
    return RelativeDateFormatter.format(dt);
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
  public static String nameEmail(AccountInfo info) {
    return accountFormatter.nameEmail(info);
  }

  /**
   * Formats an account name.
   *
   * <p>If the account has a full name, it returns only the full name. Otherwise it returns a longer
   * form that includes the email address.
   */
  public static String name(AccountInfo info) {
    return accountFormatter.name(info);
  }
}
