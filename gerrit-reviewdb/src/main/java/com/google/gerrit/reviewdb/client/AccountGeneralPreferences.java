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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;

/** Preferences about a single user. */
public final class AccountGeneralPreferences {

  /** Default number of items to display per page. */
  public static final short DEFAULT_PAGESIZE = 25;

  /** Valid choices for the page size. */
  public static final short[] PAGESIZE_CHOICES = {10, 25, 50, 100};

  /** Preferred scheme type to download a change. */
  public static enum DownloadScheme {
    ANON_GIT, ANON_HTTP, ANON_SSH, HTTP, SSH, REPO_DOWNLOAD, DEFAULT_DOWNLOADS;
  }

  /** Preferred method to download a change. */
  public static enum DownloadCommand {
    REPO_DOWNLOAD, PULL, CHECKOUT, CHERRY_PICK, FORMAT_PATCH, DEFAULT_DOWNLOADS;
  }

  public static enum DateFormat {
    /** US style dates: Apr 27, Feb 14, 2010 */
    STD("MMM d", "MMM d, yyyy"),

    /** US style dates: 04/27, 02/14/10 */
    US("MM/dd", "MM/dd/yy"),

    /** ISO style dates: 2010-02-14 */
    ISO("MM-dd", "yyyy-MM-dd"),

    /** European style dates: 27. Apr, 27.04.2010 */
    EURO("d. MMM", "dd.MM.yyyy");

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

  public static enum CommentVisibilityStrategy {
    COLLAPSE_ALL,
    EXPAND_MOST_RECENT,
    EXPAND_RECENT,
    EXPAND_ALL;
  }

  public static enum TimeFormat {
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
  @Column(id = 2)
  protected short maximumPageSize;

  /** Should the site header be displayed when logged in ? */
  @Column(id = 3)
  protected boolean showSiteHeader;

  /** Should the Flash helper movie be used to copy text to the clipboard? */
  @Column(id = 4)
  protected boolean useFlashClipboard;

  /** Type of download URL the user prefers to use. */
  @Column(id = 5, length = 20, notNull = false)
  protected String downloadUrl;

  /** Type of download command the user prefers to use. */
  @Column(id = 6, length = 20, notNull = false)
  protected String downloadCommand;

  /** If true we CC the user on their own changes. */
  @Column(id = 7)
  protected boolean copySelfOnEmail;

  @Column(id = 8, length = 10, notNull = false)
  protected String dateFormat;

  @Column(id = 9, length = 10, notNull = false)
  protected String timeFormat;

  /**
   * If true display the patch sets in the ChangeScreen in reverse order
   * (show latest patch set on top).
   */
  @Column(id = 10)
  protected boolean reversePatchSetOrder;

  @Column(id = 11)
  protected boolean showUsernameInReviewCategory;

  @Column(id = 12)
  protected boolean relativeDateInChangeTable;

  @Column(id = 13, length = 20, notNull = false)
  protected String commentVisibilityStrategy;

  public AccountGeneralPreferences() {
  }

  public short getMaximumPageSize() {
    return maximumPageSize;
  }

  public void setMaximumPageSize(final short s) {
    maximumPageSize = s;
  }

  public boolean isShowSiteHeader() {
    return showSiteHeader;
  }

  public void setShowSiteHeader(final boolean b) {
    showSiteHeader = b;
  }

  public boolean isUseFlashClipboard() {
    return useFlashClipboard;
  }

  public void setUseFlashClipboard(final boolean b) {
    useFlashClipboard = b;
  }

  public DownloadScheme getDownloadUrl() {
    if (downloadUrl == null) {
      return null;
    }
    return DownloadScheme.valueOf(downloadUrl);
  }

  public void setDownloadUrl(DownloadScheme url) {
    if (url != null) {
      downloadUrl = url.name();
    } else {
      downloadUrl = null;
    }
  }

  public DownloadCommand getDownloadCommand() {
    if (downloadCommand == null) {
      return null;
    }
    return DownloadCommand.valueOf(downloadCommand);
  }

  public void setDownloadCommand(DownloadCommand cmd) {
    if (cmd != null) {
      downloadCommand = cmd.name();
    } else {
      downloadCommand = null;
    }
  }

  public boolean isCopySelfOnEmails() {
    return copySelfOnEmail;
  }

  public void setCopySelfOnEmails(boolean includeSelfOnEmail) {
    copySelfOnEmail = includeSelfOnEmail;
  }

  public boolean isReversePatchSetOrder() {
    return reversePatchSetOrder;
  }

  public void setReversePatchSetOrder(final boolean reversePatchSetOrder) {
    this.reversePatchSetOrder = reversePatchSetOrder;
  }

  public boolean isShowUsernameInReviewCategory() {
    return showUsernameInReviewCategory;
  }

  public void setShowUsernameInReviewCategory(final boolean showUsernameInReviewCategory) {
    this.showUsernameInReviewCategory = showUsernameInReviewCategory;
  }

  public DateFormat getDateFormat() {
    if (dateFormat == null) {
      return DateFormat.STD;
    }
    return DateFormat.valueOf(dateFormat);
  }

  public void setDateFormat(DateFormat fmt) {
    dateFormat = fmt.name();
  }

  public TimeFormat getTimeFormat() {
    if (timeFormat == null) {
      return TimeFormat.HHMM_12;
    }
    return TimeFormat.valueOf(timeFormat);
  }

  public void setTimeFormat(TimeFormat fmt) {
    timeFormat = fmt.name();
  }

  public boolean isRelativeDateInChangeTable() {
    return relativeDateInChangeTable;
  }

  public void setRelativeDateInChangeTable(final boolean relativeDateInChangeTable) {
    this.relativeDateInChangeTable = relativeDateInChangeTable;
  }

  public CommentVisibilityStrategy getCommentVisibilityStrategy() {
    if (commentVisibilityStrategy == null) {
      return CommentVisibilityStrategy.EXPAND_RECENT;
    }
    return CommentVisibilityStrategy.valueOf(commentVisibilityStrategy);
  }

  public void setCommentVisibilityStrategy(
      CommentVisibilityStrategy strategy) {
    commentVisibilityStrategy = strategy.name();
  }

  public void resetToDefaults() {
    maximumPageSize = DEFAULT_PAGESIZE;
    showSiteHeader = true;
    useFlashClipboard = true;
    copySelfOnEmail = false;
    reversePatchSetOrder = false;
    showUsernameInReviewCategory = false;
    downloadUrl = null;
    downloadCommand = null;
    dateFormat = null;
    timeFormat = null;
    relativeDateInChangeTable = false;
  }
}
