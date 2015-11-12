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

  /** Preferred method to download a change. */
  public static enum DownloadCommand {
    REPO_DOWNLOAD, PULL, CHECKOUT, CHERRY_PICK, FORMAT_PATCH
  }

  public static enum DateFormat {
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

  public static enum ReviewCategoryStrategy {
    NONE,
    NAME,
    EMAIL,
    USERNAME,
    ABBREV
  }

  public static enum DiffView {
    SIDE_BY_SIDE,
    UNIFIED_DIFF
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

  public static AccountGeneralPreferences createDefault() {
    AccountGeneralPreferences p = new AccountGeneralPreferences();
    p.resetToDefaults();
    return p;
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

  // DELETED: id = 10 (reversePatchSetOrder)
  // DELETED: id = 11 (showUserInReview)

  @Column(id = 12)
  protected boolean relativeDateInChangeTable;

  // DELETED: id = 13 (commentVisibilityStrategy)

  @Column(id = 14, length = 20, notNull = false)
  protected String diffView;

  // DELETED: id = 15 (changeScreen)

  @Column(id = 16)
  protected boolean sizeBarInChangeTable;

  @Column(id = 17)
  protected boolean legacycidInChangeTable;

  @Column(id = 18, length = 20, notNull = false)
  protected String reviewCategoryStrategy;

  @Column(id = 19)
  protected boolean muteCommonPathPrefixes;

  @Column(id = 20)
  protected boolean publishDraftCommentsOnPush;

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

  public String getDownloadUrl() {
    // Translate from legacy enum names to modern display names. (May be removed
    // if accompanied by a 2-phase schema upgrade.)
    if (downloadUrl != null) {
      switch (downloadUrl) {
        case "ANON_GIT":
          return CoreDownloadSchemes.ANON_GIT;
        case "ANON_HTTP":
          return CoreDownloadSchemes.ANON_HTTP;
        case "HTTP":
          return CoreDownloadSchemes.HTTP;
        case "SSH":
          return CoreDownloadSchemes.SSH;
        case "REPO_DOWNLOAD":
          return CoreDownloadSchemes.REPO_DOWNLOAD;
      }
    }
    return downloadUrl;
  }

  public void setDownloadUrl(String url) {
    // Translate from modern display names to legacy enum names. (May be removed
    // if accompanied by a 2-phase schema upgrade.)
    if (downloadUrl != null) {
      switch (url) {
        case CoreDownloadSchemes.ANON_GIT:
          url = "ANON_GIT";
          break;
        case CoreDownloadSchemes.ANON_HTTP:
          url = "ANON_HTTP";
          break;
        case CoreDownloadSchemes.HTTP:
          url = "HTTP";
          break;
        case CoreDownloadSchemes.SSH:
          url = "SSH";
          break;
        case CoreDownloadSchemes.REPO_DOWNLOAD:
          url = "REPO_DOWNLOAD";
          break;
      }
    }
    downloadUrl = url;
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

  public boolean isShowInfoInReviewCategory() {
    return getReviewCategoryStrategy() != ReviewCategoryStrategy.NONE;
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

  public ReviewCategoryStrategy getReviewCategoryStrategy() {
    if (reviewCategoryStrategy == null) {
      return ReviewCategoryStrategy.NONE;
    }
    return ReviewCategoryStrategy.valueOf(reviewCategoryStrategy);
  }

  public void setReviewCategoryStrategy(
      ReviewCategoryStrategy strategy) {
    reviewCategoryStrategy = strategy.name();
  }

  public DiffView getDiffView() {
    if (diffView == null) {
      return DiffView.SIDE_BY_SIDE;
    }
    return DiffView.valueOf(diffView);
  }

  public void setDiffView(DiffView diffView) {
    this.diffView = diffView.name();
  }

  public boolean isSizeBarInChangeTable() {
    return sizeBarInChangeTable;
  }

  public void setSizeBarInChangeTable(boolean sizeBarInChangeTable) {
    this.sizeBarInChangeTable = sizeBarInChangeTable;
  }

  public boolean isLegacycidInChangeTable() {
    return legacycidInChangeTable;
  }

  public void setLegacycidInChangeTable(boolean legacycidInChangeTable) {
    this.legacycidInChangeTable = legacycidInChangeTable;
  }

  public boolean isMuteCommonPathPrefixes() {
    return muteCommonPathPrefixes;
  }

  public void setMuteCommonPathPrefixes(
      boolean muteCommonPathPrefixes) {
    this.muteCommonPathPrefixes = muteCommonPathPrefixes;
  }

  public boolean isPublishDraftCommentsOnPush() {
    return publishDraftCommentsOnPush;
  }

  public void setPublishDraftCommentsOnPush(
      boolean publishDraftCommentsOnPush) {
    this.publishDraftCommentsOnPush = publishDraftCommentsOnPush;
  }

  public void resetToDefaults() {
    maximumPageSize = DEFAULT_PAGESIZE;
    showSiteHeader = true;
    useFlashClipboard = true;
    copySelfOnEmail = false;
    reviewCategoryStrategy = null;
    downloadUrl = null;
    downloadCommand = null;
    dateFormat = null;
    timeFormat = null;
    relativeDateInChangeTable = false;
    diffView = null;
    sizeBarInChangeTable = true;
    legacycidInChangeTable = false;
    muteCommonPathPrefixes = true;
    publishDraftCommentsOnPush = false;
  }
}
