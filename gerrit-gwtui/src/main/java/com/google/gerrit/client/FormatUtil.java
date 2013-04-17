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

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gwt.i18n.client.DateTimeFormat;

import java.util.Date;

/** Misc. formatting functions. */
public class FormatUtil {
  private static final long ONE_YEAR = 182L * 24 * 60 * 60 * 1000;

  private static DateTimeFormat sTime;
  private static DateTimeFormat sDate;
  private static DateTimeFormat sdtFmt;
  private static DateTimeFormat mDate;
  private static DateTimeFormat dtfmt;

  public static void setPreferences(AccountGeneralPreferences pref) {
    if (pref == null) {
      if (Gerrit.isSignedIn()) {
        pref = Gerrit.getUserAccount().getGeneralPreferences();
      } else {
        pref = new AccountGeneralPreferences();
        pref.resetToDefaults();
      }
    }

    String fmt_sTime = pref.getTimeFormat().getFormat();
    String fmt_sDate = pref.getDateFormat().getShortFormat();
    String fmt_mDate = pref.getDateFormat().getLongFormat();

    sTime = DateTimeFormat.getFormat(fmt_sTime);
    sDate = DateTimeFormat.getFormat(fmt_sDate);
    sdtFmt = DateTimeFormat.getFormat(fmt_sDate + " " + fmt_sTime);
    mDate = DateTimeFormat.getFormat(fmt_mDate);
    dtfmt = DateTimeFormat.getFormat(fmt_mDate + " " + fmt_sTime);
  }

  /** Format a date using a really short format. */
  public static String shortFormat(Date dt) {
    if (dt == null) {
      return "";
    }

    ensureInited();
    final Date now = new Date();
    dt = new Date(dt.getTime());
    if (mDate.format(now).equals(mDate.format(dt))) {
      // Same day as today, report only the time.
      //
      return sTime.format(dt);

    } else if (Math.abs(now.getTime() - dt.getTime()) < ONE_YEAR) {
      // Within the last year, show a shorter date.
      //
      return sDate.format(dt);

    } else {
      // Report only date and year, its far away from now.
      //
      return mDate.format(dt);
    }
  }

  /** Format a date using a really short format. */
  public static String shortFormatDayTime(Date dt) {
    if (dt == null) {
      return "";
    }

    ensureInited();
    final Date now = new Date();
    dt = new Date(dt.getTime());
    if (mDate.format(now).equals(mDate.format(dt))) {
      // Same day as today, report only the time.
      //
      return sTime.format(dt);

    } else if (Math.abs(now.getTime() - dt.getTime()) < ONE_YEAR) {
      // Within the last year, show a shorter date.
      //
      return sdtFmt.format(dt);

    } else {
      // Report only date and year, its far away from now.
      //
      return mDate.format(dt);
    }
  }

  /** Format a date using the locale's medium length format. */
  public static String mediumFormat(final Date dt) {
    if (dt == null) {
      return "";
    }
    ensureInited();
    return dtfmt.format(new Date(dt.getTime()));
  }

  private static void ensureInited() {
    if (dtfmt == null) {
      setPreferences(null);
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
   * <li><code>A U. Thor &lt;author@example.com&gt;</code>: full populated</li>
   * <li><code>A U. Thor (12)</code>: missing email address</li>
   * <li><code>Anonymous Coward &lt;author@example.com&gt;</code>: missing name</li>
   * <li><code>Anonymous Coward (12)</code>: missing name and email address</li>
   * </ul>
   */
  public static String nameEmail(AccountInfo info) {
    String name = info.name();
    if (name == null || name.trim().isEmpty()) {
      name = Gerrit.getConfig().getAnonymousCowardName();
    }

    StringBuilder b = new StringBuilder().append(name);
    if (info.email() != null) {
      b.append(" <").append(info.email()).append(">");
    } else if (info._account_id() > 0) {
      b.append(" (").append(info._account_id()).append(")");
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

  private static AccountInfo asInfo(Account acct) {
    if (acct == null) {
      return AccountInfo.create(0, null, null);
    }
    return AccountInfo.create(
        acct.getId() != null ? acct.getId().get() : 0,
        acct.getFullName(),
        acct.getPreferredEmail());
  }

  public static AccountInfo asInfo(com.google.gerrit.common.data.AccountInfo acct) {
    if (acct == null) {
      return AccountInfo.create(0, null, null);
    }
    return AccountInfo.create(
        acct.getId() != null ? acct.getId().get() : 0,
        acct.getFullName(),
        acct.getPreferredEmail());
  }
}
