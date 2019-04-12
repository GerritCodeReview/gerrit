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

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.resetToStrict;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaEnforcer;
import com.google.gerrit.server.quota.QuotaException;
import com.google.gerrit.server.quota.QuotaRequestContext;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.Collections;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class DefaultQuotaBackendIT extends AbstractDaemonTest {

  private static final QuotaEnforcer quotaEnforcer = EasyMock.createStrictMock(QuotaEnforcer.class);

  private IdentifiedUser identifiedAdmin;
  @Inject private QuotaBackend quotaBackend;

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(QuotaEnforcer.class)
            .annotatedWith(Exports.named("TestQuotaEnforcer"))
            .toProvider(() -> quotaEnforcer);
      }
    };
  }

  @Before
  public void setUp() {
    identifiedAdmin = identifiedUserFactory.create(admin.id());
    resetToStrict(quotaEnforcer);
  }

  @Test
  public void requestTokenForUser() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.ok());
    replay(quotaEnforcer);
    assertThat(quotaBackend.user(identifiedAdmin).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenForUserAndAccount() {
    QuotaRequestContext ctx =
        QuotaRequestContext.builder().user(identifiedAdmin).account(user.id()).build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.ok());
    replay(quotaEnforcer);
    assertThat(quotaBackend.user(identifiedAdmin).account(user.id()).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenForUserAndProject() {
    QuotaRequestContext ctx =
        QuotaRequestContext.builder().user(identifiedAdmin).project(project).build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.ok());
    replay(quotaEnforcer);
    assertThat(quotaBackend.user(identifiedAdmin).project(project).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenForUserAndChange() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    QuotaRequestContext ctx =
        QuotaRequestContext.builder()
            .user(identifiedAdmin)
            .change(changeId)
            .project(project)
            .build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.ok());
    replay(quotaEnforcer);
    assertThat(
            quotaBackend.user(identifiedAdmin).change(changeId, project).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokens() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 123)).andReturn(QuotaResponse.ok());
    replay(quotaEnforcer);
    assertThat(quotaBackend.user(identifiedAdmin).requestTokens("testGroup", 123))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void dryRun() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcer.dryRun("testGroup", ctx, 123)).andReturn(QuotaResponse.ok());
    replay(quotaEnforcer);
    assertThat(quotaBackend.user(identifiedAdmin).dryRun("testGroup", 123))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenError() throws Exception {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 1))
        .andReturn(QuotaResponse.error("failed"));
    replay(quotaEnforcer);

    QuotaResponse.Aggregated result = quotaBackend.user(identifiedAdmin).requestToken("testGroup");
    assertThat(result).isEqualTo(singletonAggregation(QuotaResponse.error("failed")));
    exception.expect(QuotaException.class);
    exception.expectMessage("failed");
    result.throwOnError();
  }

  @Test
  public void requestTokenPluginThrowsAndRethrows() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcer.requestTokens("testGroup", ctx, 1)).andThrow(new NullPointerException());
    replay(quotaEnforcer);

    exception.expect(NullPointerException.class);
    quotaBackend.user(identifiedAdmin).requestToken("testGroup");
  }

  private static QuotaResponse.Aggregated singletonAggregation(QuotaResponse response) {
    return QuotaResponse.Aggregated.create(Collections.singleton(response));
  }
}
