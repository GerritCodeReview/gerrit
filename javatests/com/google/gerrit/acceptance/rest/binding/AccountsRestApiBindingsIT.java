// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.binding;

import static com.google.gerrit.acceptance.rest.util.RestApiCallHelper.execute;
import static com.google.gerrit.acceptance.rest.util.RestCall.Method.PUT;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.gpg.testing.TestKey;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.junit.Test;

/**
 * Tests for checking the bindings of the accounts REST API.
 *
 * <p>These tests only verify that the account REST endpoints are correctly bound, they do no test
 * the functionality of the account REST endpoints.
 */
public class AccountsRestApiBindingsIT extends AbstractDaemonTest {
  @Inject private @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private RequestScopeOperations requestScopeOperations;

  /**
   * Account REST endpoints to be tested, each URL contains a placeholder for the account
   * identifier.
   */
  private static final ImmutableList<RestCall> ACCOUNT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/accounts/%s"),
          RestCall.put("/accounts/%s"),
          RestCall.get("/accounts/%s/detail"),
          RestCall.get("/accounts/%s/name"),
          RestCall.put("/accounts/%s/name"),
          RestCall.delete("/accounts/%s/name"),
          RestCall.get("/accounts/%s/username"),
          RestCall.builder(PUT, "/accounts/%s/username")
              // Changing the username is not allowed.
              .expectedResponseCode(SC_METHOD_NOT_ALLOWED)
              .expectedMessage("Username cannot be changed.")
              .build(),
          RestCall.get("/accounts/%s/active"),
          RestCall.put("/accounts/%s/active"),
          RestCall.delete("/accounts/%s/active"),
          RestCall.put("/accounts/%s/password.http"),
          RestCall.delete("/accounts/%s/password.http"),
          RestCall.get("/accounts/%s/status"),
          RestCall.put("/accounts/%s/status"),
          RestCall.get("/accounts/%s/avatar"),
          RestCall.get("/accounts/%s/avatar.change.url"),
          RestCall.get("/accounts/%s/emails/"),
          RestCall.put("/accounts/%s/emails/new-email@foo.com"),
          RestCall.get("/accounts/%s/sshkeys/"),
          RestCall.post("/accounts/%s/sshkeys/"),
          RestCall.get("/accounts/%s/watched.projects"),
          RestCall.post("/accounts/%s/watched.projects"),
          RestCall.post("/accounts/%s/watched.projects:delete"),
          RestCall.get("/accounts/%s/groups"),
          RestCall.get("/accounts/%s/preferences"),
          RestCall.put("/accounts/%s/preferences"),
          RestCall.get("/accounts/%s/preferences.diff"),
          RestCall.put("/accounts/%s/preferences.diff"),
          RestCall.get("/accounts/%s/preferences.edit"),
          RestCall.put("/accounts/%s/preferences.edit"),
          RestCall.get("/accounts/%s/starred.changes"),
          RestCall.get("/accounts/%s/stars.changes"),
          RestCall.post("/accounts/%s/index"),
          RestCall.get("/accounts/%s/agreements"),
          RestCall.put("/accounts/%s/agreements"),
          RestCall.get("/accounts/%s/external.ids"),
          RestCall.post("/accounts/%s/external.ids:delete"),
          RestCall.post("/accounts/%s/drafts:delete"),
          RestCall.get("/accounts/%s/oauthtoken"),
          RestCall.get("/accounts/%s/capabilities"),
          RestCall.get("/accounts/%s/capabilities/viewPlugins"),
          RestCall.get("/accounts/%s/gpgkeys"),
          RestCall.post("/accounts/%s/gpgkeys"));

  /**
   * Email REST endpoints to be tested, each URL contains a placeholders for the account and email
   * identifier.
   */
  private static final ImmutableList<RestCall> EMAIL_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/accounts/%s/emails/%s"),
          RestCall.put("/accounts/%s/emails/%s"),
          RestCall.put("/accounts/%s/emails/%s/preferred"),

          // email deletion must be tested last
          RestCall.delete("/accounts/%s/emails/%s"));

  /**
   * GPG key REST endpoints to be tested, each URL contains a placeholders for the account
   * identifier and the GPG key identifier.
   */
  private static final ImmutableList<RestCall> GPG_KEY_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/accounts/%s/gpgkeys/%s"),

          // GPG key deletion must be tested last
          RestCall.delete("/accounts/%s/gpgkeys/%s"));

  /**
   * SSH key REST endpoints to be tested, each URL contains a placeholders for the account and SSH
   * key identifier.
   */
  private static final ImmutableList<RestCall> SSH_KEY_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/accounts/%s/sshkeys/%s"),

          // SSH key deletion must be tested last
          RestCall.delete("/accounts/%s/sshkeys/%s"));

  /**
   * Star REST endpoints to be tested, each URL contains a placeholders for the account and change
   * identifier.
   */
  private static final ImmutableList<RestCall> STAR_ENDPOINTS =
      ImmutableList.of(
          RestCall.put("/accounts/%s/starred.changes/%s"),
          RestCall.delete("/accounts/%s/starred.changes/%s"),
          RestCall.get("/accounts/%s/stars.changes/%s"),
          RestCall.post("/accounts/%s/stars.changes/%s"));

  @Test
  public void accountEndpoints() throws Exception {
    execute(adminRestSession, ACCOUNT_ENDPOINTS, "self");
  }

  @Test
  public void emailEndpoints() throws Exception {
    execute(adminRestSession, EMAIL_ENDPOINTS, "self", admin.email());
  }

  @Test
  @GerritConfig(name = "receive.enableSignedPush", value = "true")
  public void gpgKeyEndpoints() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    String id = key.getKeyIdString();

    String email = "test1@example.com"; // email that is hard-coded in the test GPG key
    accountsUpdateProvider
        .get()
        .update(
            "Add Email",
            admin.id(),
            u ->
                u.addExternalId(
                    ExternalId.createWithEmail(name("test"), email, admin.id(), email)));

    requestScopeOperations.setApiUser(admin.id());
    gApi.accounts()
        .self()
        .putGpgKeys(ImmutableList.of(key.getPublicKeyArmored()), ImmutableList.of());

    execute(adminRestSession, GPG_KEY_ENDPOINTS, "self", id);
  }

  @Test
  @UseSsh
  public void sshKeyEndpoints() throws Exception {
    String sshKeySeq = Integer.toString(gApi.accounts().self().listSshKeys().size());
    execute(adminRestSession, SSH_KEY_ENDPOINTS, "self", sshKeySeq);
  }

  @Test
  public void starEndpoints() throws Exception {
    ChangeInput ci = new ChangeInput(project.get(), "master", "Test change");
    String changeId = gApi.changes().create(ci).get().id;
    execute(adminRestSession, STAR_ENDPOINTS, "self", changeId);
  }
}
