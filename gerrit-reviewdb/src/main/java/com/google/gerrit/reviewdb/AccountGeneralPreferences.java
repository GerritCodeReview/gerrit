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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;

/** Preferences about a single user. */
public final class AccountGeneralPreferences {

  /** Default number of items to display per page. */
  public static final short DEFAULT_PAGESIZE = 25;

  /** Valid choices for the page size. */
  public static final short[] PAGESIZE_CHOICES = {10, 25, 50, 100};

  /** Preferred URL type to download a change. */
  public static enum DownloadUrl {
    ANON_GIT, ANON_HTTP, ANON_SSH, HTTP, SSH;
  }

  /** Preferred method to download a change. */
  public static enum DownloadCommand {
    REPO_DOWNLOAD, PULL, CHECKOUT, CHERRY_PICK, FORMAT_PATCH;
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

  public DownloadUrl getDownloadUrl() {
    if (downloadUrl == null) {
      return null;
    }
    return DownloadUrl.valueOf(downloadUrl);
  }

  public void setDownloadUrl(DownloadUrl url) {
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

  public void resetToDefaults() {
    maximumPageSize = DEFAULT_PAGESIZE;
    showSiteHeader = true;
    useFlashClipboard = true;
    copySelfOnEmail = false;
    downloadUrl = null;
    downloadCommand = null;
  }
}
