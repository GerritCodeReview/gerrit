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

package com.google.gerrit.extensions.client;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;

/** Preferences about a single user. */
public class GeneralPreferencesInfo {

  /** Default number of items to display per page. */
  public static final int DEFAULT_PAGESIZE = 25;

  public enum DateFormat {
    /** US style dates: Apr 27, Feb 14, 2010 */
    STD("MMM d", "MMM d, yyyy"),

    /** US style dates: 04/27, 02/14/10 */
    US("MM/dd", "MM/dd/yy"),

    /** ISO style dates: 2010-02-14 */
    ISO("MM-dd", "yyyy-MM-dd"),

    /** European style dates: 27. Apr, 27.04.2010 */
    EURO("d. MMM", "dd.MM.yyyy"),

    /** UK style dates: 27/04, 27/04/2010 */
    UK("dd/MM", "dd/MM/yyyy");

    private final String shortFormat;
    private final String longFormat;

    DateFormat(String shortFormat, String longFormat) {
      this.shortFormat = shortFormat;
      this.longFormat = longFormat;
    }

    public String getShortFormat() {
      return shortFormat;
    }

    public String getLongFormat() {
      return longFormat;
    }
  }

  public enum DiffView {
    SIDE_BY_SIDE,
    UNIFIED_DIFF
  }

  public enum EmailStrategy {
    ENABLED,
    CC_ON_OWN_COMMENTS,
    ATTENTION_SET_ONLY,
    DISABLED
  }

  public enum EmailFormat {
    PLAINTEXT,
    HTML_PLAINTEXT
  }

  public enum DefaultBase {
    AUTO_MERGE(null),
    FIRST_PARENT(-1);

    private final String base;

    DefaultBase(String base) {
      this.base = base;
    }

    DefaultBase(int base) {
      this(Integer.toString(base));
    }

    public String getBase() {
      return base;
    }
  }

  public enum Theme {
    AUTO,
    DARK,
    LIGHT
  }

  public enum TimeFormat {
    /** 12-hour clock: 1:15 am, 2:13 pm */
    HHMM_12("h:mm a"),

    /** 24-hour clock: 01:15, 14:13 */
    HHMM_24("HH:mm");

    private final String format;

    TimeFormat(String format) {
      this.format = format;
    }

    public String getFormat() {
      return format;
    }
  }

  /** Number of changes to show in a screen. */
  public Integer changesPerPage;

  /** Type of download URL the user prefers to use. */
  public String downloadScheme;

  public Theme theme;
  public DateFormat dateFormat;
  public TimeFormat timeFormat;
  public Boolean expandInlineDiffs;
  public Boolean relativeDateInChangeTable;
  public DiffView diffView;
  public Boolean sizeBarInChangeTable;
  public Boolean legacycidInChangeTable;
  public Boolean muteCommonPathPrefixes;
  public Boolean signedOffBy;
  public EmailStrategy emailStrategy;
  public EmailFormat emailFormat;
  public DefaultBase defaultBaseForMerges;
  public Boolean publishCommentsOnPush;
  public Boolean disableKeyboardShortcuts;
  public Boolean disableTokenHighlighting;
  public Boolean workInProgressByDefault;
  public List<MenuItem> my;
  public List<String> changeTable;
  public Boolean allowBrowserNotifications;
  public Boolean allowSuggestCodeWhileCommenting;

  /**
   * The sidebar section that the user prefers to have open on the diff page, or "NONE" if all
   * sidebars should be closed.
   *
   * <p>Sidebars supplied by plugins are prefixed with "plugin-".
   */
  public String diffPageSidebar;

  public DateFormat getDateFormat() {
    if (dateFormat == null) {
      return DateFormat.STD;
    }
    return dateFormat;
  }

  public TimeFormat getTimeFormat() {
    if (timeFormat == null) {
      return TimeFormat.HHMM_12;
    }
    return timeFormat;
  }

  public DiffView getDiffView() {
    if (diffView == null) {
      return DiffView.SIDE_BY_SIDE;
    }
    return diffView;
  }

  public EmailStrategy getEmailStrategy() {
    if (emailStrategy == null) {
      return EmailStrategy.ENABLED;
    }
    return emailStrategy;
  }

  public EmailFormat getEmailFormat() {
    if (emailFormat == null) {
      return EmailFormat.HTML_PLAINTEXT;
    }
    return emailFormat;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof GeneralPreferencesInfo)) {
      return false;
    }
    GeneralPreferencesInfo other = (GeneralPreferencesInfo) obj;
    return Objects.equals(this.changesPerPage, other.changesPerPage)
        && Objects.equals(this.downloadScheme, other.downloadScheme)
        && Objects.equals(this.theme, other.theme)
        && Objects.equals(this.dateFormat, other.dateFormat)
        && Objects.equals(this.timeFormat, other.timeFormat)
        && Objects.equals(this.expandInlineDiffs, other.expandInlineDiffs)
        && Objects.equals(this.relativeDateInChangeTable, other.relativeDateInChangeTable)
        && Objects.equals(this.diffView, other.diffView)
        && Objects.equals(this.sizeBarInChangeTable, other.sizeBarInChangeTable)
        && Objects.equals(this.legacycidInChangeTable, other.legacycidInChangeTable)
        && Objects.equals(this.muteCommonPathPrefixes, other.muteCommonPathPrefixes)
        && Objects.equals(this.signedOffBy, other.signedOffBy)
        && Objects.equals(this.emailStrategy, other.emailStrategy)
        && Objects.equals(this.emailFormat, other.emailFormat)
        && Objects.equals(this.defaultBaseForMerges, other.defaultBaseForMerges)
        && Objects.equals(this.publishCommentsOnPush, other.publishCommentsOnPush)
        && Objects.equals(this.disableKeyboardShortcuts, other.disableKeyboardShortcuts)
        && Objects.equals(this.disableTokenHighlighting, other.disableTokenHighlighting)
        && Objects.equals(this.workInProgressByDefault, other.workInProgressByDefault)
        && Objects.equals(this.my, other.my)
        && Objects.equals(this.changeTable, other.changeTable)
        && Objects.equals(this.allowBrowserNotifications, other.allowBrowserNotifications)
        && Objects.equals(this.diffPageSidebar, other.diffPageSidebar);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        changesPerPage,
        downloadScheme,
        theme,
        dateFormat,
        timeFormat,
        expandInlineDiffs,
        relativeDateInChangeTable,
        diffView,
        sizeBarInChangeTable,
        legacycidInChangeTable,
        muteCommonPathPrefixes,
        signedOffBy,
        emailStrategy,
        emailFormat,
        defaultBaseForMerges,
        publishCommentsOnPush,
        disableKeyboardShortcuts,
        disableTokenHighlighting,
        workInProgressByDefault,
        my,
        changeTable,
        allowBrowserNotifications,
        diffPageSidebar);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("GeneralPreferencesInfo")
        .add("changesPerPage", changesPerPage)
        .add("downloadScheme", downloadScheme)
        .add("theme", theme)
        .add("dateFormat", dateFormat)
        .add("timeFormat", timeFormat)
        .add("expandInlineDiffs", expandInlineDiffs)
        .add("relativeDateInChangeTable", relativeDateInChangeTable)
        .add("diffView", diffView)
        .add("sizeBarInChangeTable", sizeBarInChangeTable)
        .add("legacycidInChangeTable", legacycidInChangeTable)
        .add("muteCommonPathPrefixes", muteCommonPathPrefixes)
        .add("signedOffBy", signedOffBy)
        .add("emailStrategy", emailStrategy)
        .add("emailFormat", emailFormat)
        .add("defaultBaseForMerges", defaultBaseForMerges)
        .add("publishCommentsOnPush", publishCommentsOnPush)
        .add("disableKeyboardShortcuts", disableKeyboardShortcuts)
        .add("disableTokenHighlighting", disableTokenHighlighting)
        .add("workInProgressByDefault", workInProgressByDefault)
        .add("my", my)
        .add("changeTable", changeTable)
        .add("allowBrowserNotifications", allowBrowserNotifications)
        .add("diffPageSidebar", diffPageSidebar)
        .toString();
  }

  public static GeneralPreferencesInfo defaults() {
    GeneralPreferencesInfo p = new GeneralPreferencesInfo();
    p.changesPerPage = DEFAULT_PAGESIZE;
    p.downloadScheme = null;
    p.theme = Theme.AUTO;
    p.dateFormat = DateFormat.STD;
    p.timeFormat = TimeFormat.HHMM_12;
    p.expandInlineDiffs = false;
    p.relativeDateInChangeTable = false;
    p.diffView = DiffView.SIDE_BY_SIDE;
    p.sizeBarInChangeTable = true;
    p.legacycidInChangeTable = false;
    p.muteCommonPathPrefixes = true;
    p.signedOffBy = false;
    p.emailStrategy = EmailStrategy.ENABLED;
    p.emailFormat = EmailFormat.HTML_PLAINTEXT;
    p.defaultBaseForMerges = DefaultBase.FIRST_PARENT;
    p.publishCommentsOnPush = false;
    p.disableKeyboardShortcuts = false;
    p.disableTokenHighlighting = false;
    p.workInProgressByDefault = false;
    p.allowBrowserNotifications = true;
    p.diffPageSidebar = "NONE";
    p.allowSuggestCodeWhileCommenting = true;
    return p;
  }
}
