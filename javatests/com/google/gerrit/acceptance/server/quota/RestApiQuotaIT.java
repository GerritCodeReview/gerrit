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

package com.google.gerrit.acceptance.server.quota;

import static com.google.gerrit.httpd.restapi.RestApiServlet.SC_TOO_MANY_REQUESTS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Module;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class RestApiQuotaIT extends AbstractDaemonTest {
  private static final QuotaBackend.WithResource quotaBackendWithResource =
      mock(QuotaBackend.WithResource.class);
  private static final QuotaBackend.WithUser quotaBackendWithUser =
      mock(QuotaBackend.WithUser.class);

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(QuotaBackend.class)
            .toInstance(
                new QuotaBackend() {
                  @Override
                  public WithUser currentUser() {
                    return quotaBackendWithUser;
                  }

                  @Override
                  public WithUser user(CurrentUser user) {
                    return quotaBackendWithUser;
                  }
                });
      }
    };
  }

  @Before
  public void setUp() {
    clearInvocations(quotaBackendWithResource);
    clearInvocations(quotaBackendWithUser);
  }

  @Test
  public void changeDetail() throws Exception {
    Change.Id changeId = retrieveChangeId();
    when(quotaBackendWithResource.requestToken("/restapi/changes/detail:GET"))
        .thenReturn(singletonAggregation(QuotaResponse.ok()));
    when(quotaBackendWithUser.change(changeId, project)).thenReturn(quotaBackendWithResource);
    adminRestSession.get("/changes/" + changeId + "/detail").assertOK();
    verify(quotaBackendWithResource).requestToken("/restapi/changes/detail:GET");
    verify(quotaBackendWithUser).change(changeId, project);
  }

  @Test
  public void revisionDetail() throws Exception {
    Change.Id changeId = retrieveChangeId();
    when(quotaBackendWithResource.requestToken("/restapi/changes/revisions/actions:GET"))
        .thenReturn(singletonAggregation(QuotaResponse.ok()));
    when(quotaBackendWithUser.change(changeId, project)).thenReturn(quotaBackendWithResource);
    adminRestSession.get("/changes/" + changeId + "/revisions/current/actions").assertOK();
    verify(quotaBackendWithResource).requestToken("/restapi/changes/revisions/actions:GET");
    verify(quotaBackendWithUser).change(changeId, project);
  }

  @Test
  public void createChangePost() throws Exception {
    when(quotaBackendWithUser.requestToken("/restapi/changes:POST"))
        .thenReturn(singletonAggregation(QuotaResponse.ok()));
    ChangeInput changeInput = new ChangeInput(project.get(), "master", "test");
    adminRestSession.post("/changes/", changeInput).assertCreated();
    verify(quotaBackendWithUser).requestToken("/restapi/changes:POST");
  }

  @Test
  public void accountDetail() throws Exception {
    when(quotaBackendWithResource.requestToken("/restapi/accounts/detail:GET"))
        .thenReturn(singletonAggregation(QuotaResponse.ok()));
    when(quotaBackendWithUser.account(admin.id())).thenReturn(quotaBackendWithResource);
    adminRestSession.get("/accounts/self/detail").assertOK();
    verify(quotaBackendWithResource).requestToken("/restapi/accounts/detail:GET");
    verify(quotaBackendWithUser).account(admin.id());
  }

  @Test
  public void config() throws Exception {
    when(quotaBackendWithUser.requestToken("/restapi/config/version:GET"))
        .thenReturn(singletonAggregation(QuotaResponse.ok()));
    adminRestSession.get("/config/server/version").assertOK();
    verify(quotaBackendWithUser).requestToken("/restapi/config/version:GET");
  }

  @Test
  public void outOfQuotaReturnsError() throws Exception {
    when(quotaBackendWithUser.requestToken("/restapi/config/version:GET"))
        .thenReturn(singletonAggregation(QuotaResponse.error("no quota")));
    adminRestSession.get("/config/server/version").assertStatus(SC_TOO_MANY_REQUESTS);
    verify(quotaBackendWithUser).requestToken("/restapi/config/version:GET");
  }

  private Change.Id retrieveChangeId() throws Exception {
    // use REST API so that repository size quota doesn't have to be stubbed
    ChangeInfo changeInfo =
        gApi.changes().create(new ChangeInput(project.get(), "master", "test")).get();
    return Change.id(changeInfo._number);
  }

  private static QuotaResponse.Aggregated singletonAggregation(QuotaResponse response) {
    return QuotaResponse.Aggregated.create(Collections.singleton(response));
  }
}
