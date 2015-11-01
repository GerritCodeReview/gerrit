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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.MenuItem;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

public class AccountGeneralPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getDiffPreferencesOfNonExistingAccount_NotFound()
      throws Exception {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/accounts/non-existing/preferences")
        .getStatusCode());
  }

  @Test
  public void getPreferences() throws Exception {
    RestResponse r = adminSession.get("/accounts/" + admin.email
        + "/preferences");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    AccountGeneralPreferencesInfo d = AccountGeneralPreferencesInfo.defaults();
    AccountGeneralPreferencesInfo o =
        newGson().fromJson(r.getReader(), AccountGeneralPreferencesInfo.class);

    assertThat(o.changesPerPage).isEqualTo(d.changesPerPage);
    assertThat(o.showSiteHeader).isEqualTo(d.showSiteHeader);
    assertThat(o.useFlashClipboard).isEqualTo(d.useFlashClipboard);
    assertThat(o.downloadScheme).isNull();
    assertThat(o.copySelfOnEmail).isNull();
    assertThat(o.downloadCommand).isEqualTo(d.downloadCommand);
    assertThat(o.dateFormat).isEqualTo(d.getDateFormat());
    assertThat(o.timeFormat).isEqualTo(d.getTimeFormat());
    assertThat(o.relativeDateInChangeTable).isNull();
    assertThat(o.sizeBarInChangeTable).isEqualTo(d.sizeBarInChangeTable);
    assertThat(o.legacycidInChangeTable).isNull();
    assertThat(o.muteCommonPathPrefixes).isEqualTo(
        d.muteCommonPathPrefixes);
    assertThat(o.reviewCategoryStrategy).isEqualTo(
        d.getReviewCategoryStrategy());
    assertThat(o.diffView).isEqualTo(d.getDiffView());

    assertThat(o.my).hasSize(7);
    // TODO(davido): URL aliases cannot be currently deleted
    assertThat(o.urlAliases).hasSize(1);
  }

  @Test
  public void setPreferences() throws Exception {
    AccountGeneralPreferencesInfo i = AccountGeneralPreferencesInfo.defaults();

    // change all default values
    i.changesPerPage *= -1;
    i.showSiteHeader ^= true;
    i.useFlashClipboard ^= true;
    i.copySelfOnEmail ^= true;
    i.downloadCommand = DownloadCommand.REPO_DOWNLOAD;
    i.dateFormat = DateFormat.US;
    i.timeFormat = TimeFormat.HHMM_24;
    i.relativeDateInChangeTable ^= true;
    i.sizeBarInChangeTable ^= true;
    i.legacycidInChangeTable ^= true;
    i.muteCommonPathPrefixes ^= true;
    i.reviewCategoryStrategy = ReviewCategoryStrategy.ABBREV;
    i.diffView = DiffView.UNIFIED_DIFF;
    i.my = new ArrayList<>();
    i.my.add(new MenuItem("name", "url"));
    i.urlAliases = new HashMap<>();
    i.urlAliases.put("foo", "bar");

    RestResponse r = adminSession.put("/accounts/" + admin.email
        + "/preferences", i);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    AccountGeneralPreferencesInfo o = newGson().fromJson(r.getReader(),
        AccountGeneralPreferencesInfo.class);

    assertThat(o.changesPerPage).isEqualTo(i.changesPerPage);
    assertThat(o.showSiteHeader).isNull();
    assertThat(o.useFlashClipboard).isNull();
    assertThat(o.downloadScheme).isNull();
    assertThat(o.copySelfOnEmail).isEqualTo(i.copySelfOnEmail);
    assertThat(o.downloadCommand).isEqualTo(i.downloadCommand);
    assertThat(o.dateFormat).isEqualTo(i.getDateFormat());
    assertThat(o.timeFormat).isEqualTo(i.getTimeFormat());
    assertThat(o.relativeDateInChangeTable).isEqualTo(
        i.relativeDateInChangeTable);
    assertThat(o.sizeBarInChangeTable).isNull();
    assertThat(o.legacycidInChangeTable).isEqualTo(i.legacycidInChangeTable);
    assertThat(o.muteCommonPathPrefixes).isNull();
    assertThat(o.reviewCategoryStrategy).isEqualTo(
        i.getReviewCategoryStrategy());
    assertThat(o.diffView).isEqualTo(i.getDiffView());
    assertThat(o.my).hasSize(1);
    assertThat(o.urlAliases).hasSize(1);

    // get default preferences
    r = adminSession.get("/config/server/preferences");
    o = newGson().fromJson(r.getReader(),
        AccountGeneralPreferencesInfo.class);
    assertThat(o.my).hasSize(7);

    // reset user preferences
    i = AccountGeneralPreferencesInfo.defaults();
    i.my = o.my;
    adminSession.put("/accounts/" + admin.email
        + "/preferences", i);
  }
}
