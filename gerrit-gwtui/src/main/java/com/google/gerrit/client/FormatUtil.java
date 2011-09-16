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

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gwt.i18n.client.DateTimeFormat;

import java.util.Date;

/** Misc. formatting functions. */
public class FormatUtil {
  private static final long ONE_YEAR = 182L * 24 * 60 * 60 * 1000;

  private static DateTimeFormat sTime = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.TIME_SHORT);
  private static DateTimeFormat sDate = DateTimeFormat.getFormat("MMM d");
  private static DateTimeFormat mDate = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.DATE_MEDIUM);
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
    String fmt_mDate = pref.getDateFormat().getLongFormat();

    sTime = DateTimeFormat.getFormat(fmt_sTime);
    sDate = DateTimeFormat.getFormat(pref.getDateFormat().getShortFormat());
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

  /** Format an account as a name and email address. */
  public static String nameEmail(final Account acct) {
    return nameEmail(new AccountInfo(acct));
  }

  /**
   * Formats an account as an name and an email address.
   * <p>
   * Example output:
   * <ul>
   * <li><code>A U. Thor &lt;author@example.com&gt;</code>: full populated</li>
   * <li><code>A U. Thor (12)</code>: missing email address</li>
   * <li><code>Anonymous Coward &lt;author@example.com&gt;</code>: missing name</li>
   * <li><code>Anonymous Coward (12)</code>: missing name and email address</li>
   * </ul>
   */
  public static String nameEmail(final AccountInfo acct) {
    String name = acct.getFullName();
    if (name == null) {
      name = Gerrit.getConfig().getAnonymousCowardName();
    }

    final StringBuilder b = new StringBuilder();
    b.append(name);
    if (acct.getPreferredEmail() != null) {
      b.append(" <");
      b.append(acct.getPreferredEmail());
      b.append(">");
    } else if (acct.getId() != null) {
      b.append(" (");
      b.append(acct.getId().get());
      b.append(")");
    }
    return b.toString();
  }

  /**
   * Formats an account name.
   * <p>
   * If the account has a full name, it returns only the full name. Otherwise it
   * returns a longer form that includes the email address.
   */
  public static String name(final AccountInfo ai) {
    if (ai.getFullName() != null) {
      return ai.getFullName();
    }
    if (ai.getPreferredEmail() != null) {
      return ai.getPreferredEmail();
    }
    return nameEmail(ai);
  }
}
