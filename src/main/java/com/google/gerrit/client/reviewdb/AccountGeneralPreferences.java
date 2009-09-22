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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

/** Preferences about a single user. */
public final class AccountGeneralPreferences {
  /** Default number of lines of context. */
  public static final short DEFAULT_CONTEXT = 10;

  /** Context setting to display the entire file. */
  public static final short WHOLE_FILE_CONTEXT = -1;

  /** Typical valid choices for the default context setting. */
  public static final short[] CONTEXT_CHOICES =
      {3, 10, 25, 50, 75, 100, WHOLE_FILE_CONTEXT};

  /** Default number of items to display per page. */
  public static final short DEFAULT_PAGESIZE = 25;

  /** Valid choices for the page size. */
  public static final short[] PAGESIZE_CHOICES = {10, 25, 50, 100};

  /** Default number of lines of context when viewing a patch. */
  @Column
  short defaultContext;

  /** Number of changes to show in a screen. */
  @Column
  short maximumPageSize;

  /** Should the site header be displayed when logged in ? */
  @Column
  boolean showSiteHeader;

  /** Should the Flash helper movie be used to copy text to the clipboard? */
  @Column
  boolean useFlashClipboard;

  public AccountGeneralPreferences() {
  }

  /** Get the default number of lines of context when viewing a patch. */
  public short getDefaultContext() {
    return defaultContext;
  }

  /** Set the number of lines of context when viewing a patch. */
  public void setDefaultContext(final short s) {
    defaultContext = s;
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

  public void resetToDefaults() {
    defaultContext = DEFAULT_CONTEXT;
    maximumPageSize = DEFAULT_PAGESIZE;
    showSiteHeader = true;
    useFlashClipboard = true;
  }
}
