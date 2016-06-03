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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.AssertUtil.assertPrefs;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.MenuItem;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

@NoHttpd
public class GeneralPreferencesIT extends AbstractDaemonTest {
  private TestAccount user42;

  @Before
  public void setUp() throws Exception {
    String name = name("user42");
    user42 = accounts.create(name, name + "@example.com", "User 42");
  }

  @Test
  public void getAndSetPreferences() throws Exception {
    GeneralPreferencesInfo o = gApi.accounts()
        .id(user42.id.toString())
        .getPreferences();
    assertPrefs(o, GeneralPreferencesInfo.defaults(), "my");
    assertThat(o.my).hasSize(7);

    GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();

    // change all default values
    i.changesPerPage *= -1;
    i.showSiteHeader ^= true;
    i.useFlashClipboard ^= true;
    i.downloadCommand = DownloadCommand.REPO_DOWNLOAD;
    i.dateFormat = DateFormat.US;
    i.timeFormat = TimeFormat.HHMM_24;
    i.emailStrategy = EmailStrategy.DISABLED;
    i.relativeDateInChangeTable ^= true;
    i.sizeBarInChangeTable ^= true;
    i.legacycidInChangeTable ^= true;
    i.muteCommonPathPrefixes ^= true;
    i.signedOffBy ^= true;
    i.reviewCategoryStrategy = ReviewCategoryStrategy.ABBREV;
    i.diffView = DiffView.UNIFIED_DIFF;
    i.my = new ArrayList<>();
    i.my.add(new MenuItem("name", "url"));
    i.urlAliases = new HashMap<>();
    i.urlAliases.put("foo", "bar");

    o = gApi.accounts()
        .id(user42.getId().toString())
        .setPreferences(i);
    assertPrefs(o, i, "my");
    assertThat(o.my).hasSize(1);
  }
}
