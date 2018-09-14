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
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.resetToStrict;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaEnforcer;
import com.google.gerrit.server.quota.QuotaRequestContext;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Inject;
import com.google.inject.Module;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class MultipleQuotaPluginsIT extends AbstractDaemonTest {
  private static final QuotaEnforcer quotaEnforcerA =
      EasyMock.createStrictMock(QuotaEnforcer.class);
  private static final QuotaEnforcer quotaEnforcerB =
      EasyMock.createStrictMock(QuotaEnforcer.class);

  private IdentifiedUser identifiedAdmin;
  @Inject private QuotaBackend quotaBackend;

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(QuotaEnforcer.class)
            .annotatedWith(Exports.named("TestQuotaEnforcerA"))
            .toProvider(() -> quotaEnforcerA);

        bind(QuotaEnforcer.class)
            .annotatedWith(Exports.named("TestQuotaEnforcerB"))
            .toProvider(() -> quotaEnforcerB);
      }
    };
  }

  @Before
  public void setUp() {
    identifiedAdmin = identifiedUserFactory.create(admin.id());
    resetToStrict(quotaEnforcerA);
    resetToStrict(quotaEnforcerB);
  }

  @Test
  public void refillsOnError() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcerA.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.ok());
    expect(quotaEnforcerB.requestTokens("testGroup", ctx, 1))
        .andReturn(QuotaResponse.error("fail"));
    quotaEnforcerA.refill("testGroup", ctx, 1);
    expectLastCall();

    replay(quotaEnforcerA);
    replay(quotaEnforcerB);

    assertThat(quotaBackend.user(identifiedAdmin).requestToken("testGroup"))
        .isEqualTo(
            QuotaResponse.Aggregated.create(
                ImmutableList.of(QuotaResponse.ok(), QuotaResponse.error("fail"))));
  }

  @Test
  public void refillsOnException() {
    NullPointerException exception = new NullPointerException();
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcerA.requestTokens("testGroup", ctx, 1)).andThrow(exception);
    expect(quotaEnforcerB.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.ok());
    quotaEnforcerB.refill("testGroup", ctx, 1);
    expectLastCall();

    replay(quotaEnforcerA);
    replay(quotaEnforcerB);

    try {
      quotaBackend.user(identifiedAdmin).requestToken("testGroup");
      fail("expected a NullPointerException");
    } catch (NullPointerException e) {
      assertThat(exception).isEqualTo(e);
    }

    verify(quotaEnforcerA);
  }

  @Test
  public void doesNotRefillNoOp() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    expect(quotaEnforcerA.requestTokens("testGroup", ctx, 1))
        .andReturn(QuotaResponse.error("fail"));
    expect(quotaEnforcerB.requestTokens("testGroup", ctx, 1)).andReturn(QuotaResponse.noOp());

    replay(quotaEnforcerA);
    replay(quotaEnforcerB);

    assertThat(quotaBackend.user(identifiedAdmin).requestToken("testGroup"))
        .isEqualTo(
            QuotaResponse.Aggregated.create(
                ImmutableList.of(QuotaResponse.error("fail"), QuotaResponse.noOp())));
  }
}
