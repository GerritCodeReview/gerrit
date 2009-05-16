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

  /** Default number of lines of context when viewing a patch. */
  @Column
  protected short defaultContext;

  /** Should the site header be displayed when logged in ? */
  @Column
  protected boolean showSiteHeader;

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

  public boolean isShowSiteHeader() {
    return showSiteHeader;
  }

  public void setShowSiteHeader(final boolean b) {
    showSiteHeader = b;
  }

  public void resetToDefaults() {
    defaultContext = DEFAULT_CONTEXT;
    showSiteHeader = true;
  }
}
