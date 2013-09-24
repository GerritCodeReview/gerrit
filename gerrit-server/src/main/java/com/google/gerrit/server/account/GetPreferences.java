// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ChangeScreen;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.CommentVisibilityStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DateFormat;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.TimeFormat;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GetPreferences implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;

  @Inject
  GetPreferences(Provider<CurrentUser> self) {
    this.self = self;
  }

  @Override
  public PreferenceInfo apply(AccountResource rsrc) throws AuthException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }
    return new PreferenceInfo(rsrc.getUser().getAccount()
        .getGeneralPreferences());
  }

  static class PreferenceInfo {
    final String kind = "gerritcodereview#preferences";

    short changesPerPage;
    Boolean showSiteHeader;
    Boolean useFlashClipboard;
    DownloadScheme downloadScheme;
    DownloadCommand downloadCommand;
    Boolean copySelfOnEmail;
    DateFormat dateFormat;
    TimeFormat timeFormat;
    Boolean reversePatchSetOrder;
    Boolean showUsernameInReviewCategory;
    Boolean relativeDateInChangeTable;
    CommentVisibilityStrategy commentVisibilityStrategy;
    DiffView diffView;
    ChangeScreen changeScreen;

    PreferenceInfo(AccountGeneralPreferences p) {
      changesPerPage = p.getMaximumPageSize();
      showSiteHeader = p.isShowSiteHeader() ? true : null;
      useFlashClipboard = p.isUseFlashClipboard() ? true : null;
      downloadScheme = p.getDownloadUrl();
      downloadCommand = p.getDownloadCommand();
      copySelfOnEmail = p.isCopySelfOnEmails() ? true : null;
      dateFormat = p.getDateFormat();
      timeFormat = p.getTimeFormat();
      reversePatchSetOrder = p.isReversePatchSetOrder() ? true : null;
      showUsernameInReviewCategory = p.isShowUsernameInReviewCategory() ? true : null;
      relativeDateInChangeTable = p.isRelativeDateInChangeTable() ? true : null;
      commentVisibilityStrategy = p.getCommentVisibilityStrategy();
      diffView = p.getDiffView();
      changeScreen = p.getChangeScreen();
    }
  }
}
