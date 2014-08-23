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

package com.google.gerrit.acceptance.rest.account;

import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.GetDiffPreferences.DiffPreferencesInfo;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

import java.io.IOException;

public class AccountIT extends AbstractDaemonTest {
  @Inject
  private AllProjectsName allProjects;

  @Test
  public void testAll() throws Exception {
    getDiffPreferencesOfNonExistingAccount_NotFound();
    getDiffPreferences();
    getNonExistingAccount_NotFound();
    getAccount();
    reset();
    starredChangeState();
    testCapabilitiesUser();
    reset();
    testCapabilitiesAdmin();
  }

  public void getNonExistingAccount_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/accounts/non-existing").getStatusCode());
  }

  public void getAccount() throws IOException {
    // by formatted string
    testGetAccount("/accounts/"
        + Url.encode(admin.fullName + " <" + admin.email + ">"), admin);

    // by email
    testGetAccount("/accounts/" + admin.email, admin);

    // by full name
    testGetAccount("/accounts/" + admin.fullName, admin);

    // by account ID
    testGetAccount("/accounts/" + admin.id.get(), admin);

    // by user name
    testGetAccount("/accounts/" + admin.username, admin);

    // by 'self'
    testGetAccount("/accounts/self", admin);
  }

  public void testCapabilitiesUser() throws IOException,
      ConfigInvalidException, IllegalArgumentException,
      IllegalAccessException, NoSuchFieldException,
      SecurityException {
    grantAllCapabilities();
    RestResponse r =
        userSession.get("/accounts/self/capabilities");
    int code = r.getStatusCode();
    assertEquals(code, 200);
    CapabilityInfo info = (new Gson()).fromJson(r.getReader(),
        new TypeToken<CapabilityInfo>() {}.getType());
    for (String c: GlobalCapability.getAllNames()) {
      if (GlobalCapability.ADMINISTRATE_SERVER.equals(c)) {
        assertFalse(info.administrateServer);
      } else if (GlobalCapability.PRIORITY.equals(c)) {
        assertFalse(info.priority);
      } else if (GlobalCapability.QUERY_LIMIT.equals(c)) {
        assertEquals(0, info.queryLimit.min);
        assertEquals(0, info.queryLimit.max);
      } else {
        assertTrue(String.format("capability %s was not granted", c),
            (Boolean)CapabilityInfo.class.getField(c).get(info));
      }
    }
  }

  public void testCapabilitiesAdmin() throws IOException,
      ConfigInvalidException, IllegalArgumentException,
      IllegalAccessException, NoSuchFieldException,
      SecurityException {
    RestResponse r =
        adminSession.get("/accounts/self/capabilities");
    int code = r.getStatusCode();
    assertEquals(code, 200);
    CapabilityInfo info = (new Gson()).fromJson(r.getReader(),
        new TypeToken<CapabilityInfo>() {}.getType());
    for (String c: GlobalCapability.getAllNames()) {
      if (GlobalCapability.PRIORITY.equals(c)) {
        assertFalse(info.priority);
      } else if (GlobalCapability.QUERY_LIMIT.equals(c)) {
        assertNotNull("missing queryLimit", info.queryLimit);
        assertEquals(0, info.queryLimit.min);
        assertEquals(500, info.queryLimit.max);
      } else if (GlobalCapability.ACCESS_DATABASE.equals(c)) {
        assertFalse(info.accessDatabase);
      } else if (GlobalCapability.RUN_AS.equals(c)) {
        assertFalse(info.runAs);
      } else {
        assertTrue(String.format("capability %s was not granted", c),
            (Boolean)CapabilityInfo.class.getField(c).get(info));
      }
    }
  }

  public void getDiffPreferencesOfNonExistingAccount_NotFound()
      throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/accounts/non-existing/preferences.diff").getStatusCode());
  }

  public void getDiffPreferences() throws IOException, OrmException {
    RestResponse r = adminSession.get("/accounts/" + admin.email + "/preferences.diff");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    DiffPreferencesInfo diffPreferences =
        newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);
    assertDiffPreferences(new AccountDiffPreference(admin.id), diffPreferences);
  }

  public void starredChangeState() throws GitAPIException, IOException,
      OrmException {
    Result c1 = createChange();
    Result c2 = createChange();
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
    starChange(true, c1.getPatchSetId().getParentKey());
    starChange(true, c2.getPatchSetId().getParentKey());
    assertTrue(getChange(c1.getChangeId()).starred);
    assertTrue(getChange(c2.getChangeId()).starred);
    starChange(false, c1.getPatchSetId().getParentKey());
    starChange(false, c2.getPatchSetId().getParentKey());
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
  }

  private void starChange(boolean on, Change.Id id) throws IOException {
    String url = "/accounts/self/starred.changes/" + id.get();
    if (on) {
      RestResponse r = adminSession.put(url);
      assertEquals(204, r.getStatusCode());
    } else {
      RestResponse r = adminSession.delete(url);
      assertEquals(204, r.getStatusCode());
    }
  }

  private static void assertDiffPreferences(AccountDiffPreference expected, DiffPreferencesInfo actual) {
    assertEquals(expected.getContext(), actual.context);
    assertEquals(expected.isExpandAllComments(), toBoolean(actual.expandAllComments));
    assertEquals(expected.getIgnoreWhitespace(), actual.ignoreWhitespace);
    assertEquals(expected.isIntralineDifference(), toBoolean(actual.intralineDifference));
    assertEquals(expected.getLineLength(), actual.lineLength);
    assertEquals(expected.isManualReview(), toBoolean(actual.manualReview));
    assertEquals(expected.isRetainHeader(), toBoolean(actual.retainHeader));
    assertEquals(expected.isShowLineEndings(), toBoolean(actual.showLineEndings));
    assertEquals(expected.isShowTabs(), toBoolean(actual.showTabs));
    assertEquals(expected.isShowWhitespaceErrors(), toBoolean(actual.showWhitespaceErrors));
    assertEquals(expected.isSkipDeleted(), toBoolean(actual.skipDeleted));
    assertEquals(expected.isSkipUncommented(), toBoolean(actual.skipUncommented));
    assertEquals(expected.isSyntaxHighlighting(), toBoolean(actual.syntaxHighlighting));
    assertEquals(expected.getTabSize(), actual.tabSize);
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }

  private void grantAllCapabilities() throws IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    md.setMessage("Make super user");
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(
        AccessSection.GLOBAL_CAPABILITIES);
    for (String c: GlobalCapability.getAllNames()) {
      if (GlobalCapability.ADMINISTRATE_SERVER.equals(c)) {
        continue;
      }
      Permission p = s.getPermission(c, true);
      p.add(new PermissionRule(
          config.resolve(SystemGroupBackend.getGroup(
              SystemGroupBackend.REGISTERED_USERS))));
    }
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private void testGetAccount(String url, TestAccount expectedAccount)
      throws IOException {
    RestResponse r = adminSession.get(url);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertAccountInfo(expectedAccount, newGson()
        .fromJson(r.getReader(), AccountInfo.class));
  }
}
