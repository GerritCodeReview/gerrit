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
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class GeneralPreferencesIT extends AbstractDaemonTest {
  @Inject private DynamicMap<DownloadScheme> downloadSchemes;

  private TestAccount user42;

  @Before
  public void setUp() throws Exception {
    String name = name("user42");
    user42 = accountCreator.create(name, name + "@example.com", "User 42");
  }

  @Test
  public void getAndSetPreferences() throws Exception {
    GeneralPreferencesInfo o = gApi.accounts().id(user42.id().toString()).getPreferences();
    assertPrefs(o, GeneralPreferencesInfo.defaults(), "my", "changeTable");
    assertThat(o.my)
        .containsExactly(
            new MenuItem("Changes", "#/dashboard/self", null),
            new MenuItem("Draft Comments", "#/q/has:draft", null),
            new MenuItem("Edits", "#/q/has:edit", null),
            new MenuItem("Watched Changes", "#/q/is:watched+is:open", null),
            new MenuItem("Starred Changes", "#/q/is:starred", null),
            new MenuItem("Groups", "#/groups/self", null));
    assertThat(o.changeTable).isEmpty();

    GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();

    // change all default values
    i.changesPerPage *= -1;
    i.showSiteHeader ^= true;
    i.useFlashClipboard ^= true;
    i.downloadCommand = DownloadCommand.REPO_DOWNLOAD;
    i.dateFormat = DateFormat.US;
    i.timeFormat = TimeFormat.HHMM_24;
    i.emailStrategy = EmailStrategy.DISABLED;
    i.emailFormat = EmailFormat.PLAINTEXT;
    i.defaultBaseForMerges = DefaultBase.AUTO_MERGE;
    i.expandInlineDiffs ^= true;
    i.highlightAssigneeInChangeTable ^= true;
    i.relativeDateInChangeTable ^= true;
    i.sizeBarInChangeTable ^= true;
    i.legacycidInChangeTable ^= true;
    i.muteCommonPathPrefixes ^= true;
    i.signedOffBy ^= true;
    i.reviewCategoryStrategy = ReviewCategoryStrategy.ABBREV;
    i.diffView = DiffView.UNIFIED_DIFF;
    i.my = new ArrayList<>();
    i.my.add(new MenuItem("name", "url"));
    i.changeTable = new ArrayList<>();
    i.changeTable.add("Status");
    i.urlAliases = new HashMap<>();
    i.urlAliases.put("foo", "bar");

    o = gApi.accounts().id(user42.id().toString()).setPreferences(i);
    assertPrefs(o, i, "my");
    assertThat(o.my).containsExactlyElementsIn(i.my);
    assertThat(o.changeTable).containsExactlyElementsIn(i.changeTable);
  }

  @Test
  public void getPreferencesWithConfiguredDefaults() throws Exception {
    GeneralPreferencesInfo d = GeneralPreferencesInfo.defaults();
    int newChangesPerPage = d.changesPerPage * 2;
    GeneralPreferencesInfo update = new GeneralPreferencesInfo();
    update.changesPerPage = newChangesPerPage;
    gApi.config().server().setDefaultPreferences(update);

    GeneralPreferencesInfo o = gApi.accounts().id(user42.id().toString()).getPreferences();

    // assert configured defaults
    assertThat(o.changesPerPage).isEqualTo(newChangesPerPage);

    // assert hard-coded defaults
    assertPrefs(o, d, "my", "changeTable", "changesPerPage");
  }

  @Test
  public void overwriteConfiguredDefaults() throws Exception {
    GeneralPreferencesInfo d = GeneralPreferencesInfo.defaults();
    int configuredChangesPerPage = d.changesPerPage * 2;
    GeneralPreferencesInfo update = new GeneralPreferencesInfo();
    update.changesPerPage = configuredChangesPerPage;
    gApi.config().server().setDefaultPreferences(update);

    GeneralPreferencesInfo o = gApi.accounts().id(admin.id().toString()).getPreferences();
    assertThat(o.changesPerPage).isEqualTo(configuredChangesPerPage);
    assertPrefs(o, d, "my", "changeTable", "changesPerPage");

    int newChangesPerPage = configuredChangesPerPage * 2;
    GeneralPreferencesInfo i = new GeneralPreferencesInfo();
    i.changesPerPage = newChangesPerPage;
    GeneralPreferencesInfo a = gApi.accounts().id(admin.id().toString()).setPreferences(i);
    assertThat(a.changesPerPage).isEqualTo(newChangesPerPage);
    assertPrefs(a, d, "my", "changeTable", "changesPerPage");

    a = gApi.accounts().id(admin.id().toString()).getPreferences();
    assertThat(a.changesPerPage).isEqualTo(newChangesPerPage);
    assertPrefs(a, d, "my", "changeTable", "changesPerPage");

    // overwrite the configured default with original hard-coded default
    i = new GeneralPreferencesInfo();
    i.changesPerPage = d.changesPerPage;
    a = gApi.accounts().id(admin.id().toString()).setPreferences(i);
    assertThat(a.changesPerPage).isEqualTo(d.changesPerPage);
    assertPrefs(a, d, "my", "changeTable", "changesPerPage");

    a = gApi.accounts().id(admin.id().toString()).getPreferences();
    assertThat(a.changesPerPage).isEqualTo(d.changesPerPage);
    assertPrefs(a, d, "my", "changeTable", "changesPerPage");
  }

  @Test
  public void rejectMyMenuWithoutName() throws Exception {
    GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();
    i.my = new ArrayList<>();
    i.my.add(new MenuItem(null, "url"));

    exception.expect(BadRequestException.class);
    exception.expectMessage("name for menu item is required");
    gApi.accounts().id(user42.id().toString()).setPreferences(i);
  }

  @Test
  public void rejectMyMenuWithoutUrl() throws Exception {
    GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();
    i.my = new ArrayList<>();
    i.my.add(new MenuItem("name", null));

    exception.expect(BadRequestException.class);
    exception.expectMessage("URL for menu item is required");
    gApi.accounts().id(user42.id().toString()).setPreferences(i);
  }

  @Test
  public void trimMyMenuInput() throws Exception {
    GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();
    i.my = new ArrayList<>();
    i.my.add(new MenuItem(" name\t", " url\t", " _blank\t", " id\t"));

    GeneralPreferencesInfo o = gApi.accounts().id(user42.id().toString()).setPreferences(i);
    assertThat(o.my).containsExactly(new MenuItem("name", "url", "_blank", "id"));
  }

  @Test
  public void rejectUnsupportedDownloadScheme() throws Exception {
    GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();
    i.downloadScheme = "foo";

    exception.expect(BadRequestException.class);
    exception.expectMessage("Unsupported download scheme: " + i.downloadScheme);
    gApi.accounts().id(user42.id().toString()).setPreferences(i);
  }

  @Test
  public void setDownloadScheme() throws Exception {
    String schemeName = "foo";
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<DownloadScheme>) downloadSchemes)
            .put("myPlugin", schemeName, Providers.of(new TestDownloadScheme()));
    try {
      GeneralPreferencesInfo i = GeneralPreferencesInfo.defaults();
      i.downloadScheme = schemeName;

      GeneralPreferencesInfo o = gApi.accounts().id(user42.id().toString()).setPreferences(i);
      assertThat(o.downloadScheme).isEqualTo(schemeName);

      o = gApi.accounts().id(user42.id().toString()).getPreferences();
      assertThat(o.downloadScheme).isEqualTo(schemeName);
    } finally {
      registrationHandle.remove();
    }
  }

  @Test
  public void unsupportedDownloadSchemeIsNotReturned() throws Exception {
    // Set a download scheme and unregister the plugin that provides this download scheme so that it
    // becomes unsupported.
    setDownloadScheme();

    GeneralPreferencesInfo o = gApi.accounts().id(user42.id().toString()).getPreferences();
    assertThat(o.downloadScheme).isNull();
  }

  private static class TestDownloadScheme extends DownloadScheme {
    @Override
    public String getUrl(String project) {
      return "http://foo/" + project;
    }

    @Override
    public boolean isAuthRequired() {
      return false;
    }

    @Override
    public boolean isAuthSupported() {
      return false;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }
}
