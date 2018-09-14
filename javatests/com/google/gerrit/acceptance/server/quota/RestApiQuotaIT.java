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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Module;
import java.util.Collections;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class RestApiQuotaIT extends AbstractDaemonTest {
  private static final QuotaBackend.WithResource quotaBackendWithResource =
      EasyMock.createStrictMock(QuotaBackend.WithResource.class);
  private static final QuotaBackend.WithUser quotaBackendWithUser =
      EasyMock.createStrictMock(QuotaBackend.WithUser.class);

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
    reset(quotaBackendWithResource);
    reset(quotaBackendWithUser);
  }

  @Test
  public void changeDetail() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    expect(quotaBackendWithResource.requestToken("/restapi/changes/detail:GET"))
        .andReturn(singletonAggregation(QuotaResponse.ok()));
    replay(quotaBackendWithResource);
    expect(quotaBackendWithUser.change(changeId, project)).andReturn(quotaBackendWithResource);
    replay(quotaBackendWithUser);
    adminRestSession.get("/changes/" + changeId + "/detail").assertOK();
    verify(quotaBackendWithUser);
    verify(quotaBackendWithResource);
  }

  @Test
  public void revisionDetail() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    expect(quotaBackendWithResource.requestToken("/restapi/changes/revisions/actions:GET"))
        .andReturn(singletonAggregation(QuotaResponse.ok()));
    replay(quotaBackendWithResource);
    expect(quotaBackendWithUser.change(changeId, project)).andReturn(quotaBackendWithResource);
    replay(quotaBackendWithUser);
    adminRestSession.get("/changes/" + changeId + "/revisions/current/actions").assertOK();
    verify(quotaBackendWithUser);
    verify(quotaBackendWithResource);
  }

  @Test
  public void createChangePost() throws Exception {
    expect(quotaBackendWithUser.requestToken("/restapi/changes:POST"))
        .andReturn(singletonAggregation(QuotaResponse.ok()));
    replay(quotaBackendWithUser);
    ChangeInput changeInput = new ChangeInput(project.get(), "master", "test");
    adminRestSession.post("/changes/", changeInput).assertCreated();
    verify(quotaBackendWithUser);
  }

  @Test
  public void accountDetail() throws Exception {
    expect(quotaBackendWithResource.requestToken("/restapi/accounts/detail:GET"))
        .andReturn(singletonAggregation(QuotaResponse.ok()));
    replay(quotaBackendWithResource);
    expect(quotaBackendWithUser.account(admin.id())).andReturn(quotaBackendWithResource);
    replay(quotaBackendWithUser);
    adminRestSession.get("/accounts/self/detail").assertOK();
    verify(quotaBackendWithUser);
    verify(quotaBackendWithResource);
  }

  @Test
  public void config() throws Exception {
    expect(quotaBackendWithUser.requestToken("/restapi/config/version:GET"))
        .andReturn(singletonAggregation(QuotaResponse.ok()));
    replay(quotaBackendWithUser);
    adminRestSession.get("/config/server/version").assertOK();
  }

  @Test
  public void outOfQuotaReturnsError() throws Exception {
    expect(quotaBackendWithUser.requestToken("/restapi/config/version:GET"))
        .andReturn(singletonAggregation(QuotaResponse.error("no quota")));
    replay(quotaBackendWithUser);
    adminRestSession.get("/config/server/version").assertStatus(429);
  }

  private static QuotaResponse.Aggregated singletonAggregation(QuotaResponse response) {
    return QuotaResponse.Aggregated.create(Collections.singleton(response));
  }
}
