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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.Before;
import org.junit.Test;

public class MultipleQuotaPluginsIT extends AbstractDaemonTest {
  private static final QuotaEnforcer quotaEnforcerA = mock(QuotaEnforcer.class);
  private static final QuotaEnforcer quotaEnforcerB = mock(QuotaEnforcer.class);

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
    clearInvocations(quotaEnforcerA);
    clearInvocations(quotaEnforcerB);
  }

  @Test
  public void refillsOnError() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.ok());
    when(quotaEnforcerB.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.error("fail"));

    assertThat(quotaBackend.user(identifiedAdmin).requestToken("testGroup"))
        .isEqualTo(
            QuotaResponse.Aggregated.create(
                ImmutableList.of(QuotaResponse.ok(), QuotaResponse.error("fail"))));

    verify(quotaEnforcerA).requestTokens("testGroup", ctx, 1);
    verify(quotaEnforcerB).requestTokens("testGroup", ctx, 1);
    verify(quotaEnforcerA).refill("testGroup", ctx, 1);
  }

  @Test
  public void refillsOnException() {
    NullPointerException exception = new NullPointerException();
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.ok());
    when(quotaEnforcerB.requestTokens("testGroup", ctx, 1)).thenThrow(exception);

    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> quotaBackend.user(identifiedAdmin).requestToken("testGroup"));
    assertThat(thrown).isEqualTo(exception);

    verify(quotaEnforcerA).requestTokens("testGroup", ctx, 1);
    verify(quotaEnforcerB).requestTokens("testGroup", ctx, 1);
    verify(quotaEnforcerA).refill("testGroup", ctx, 1);
  }

  @Test
  public void doesNotRefillNoOp() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.error("fail"));
    when(quotaEnforcerB.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.noOp());

    assertThat(quotaBackend.user(identifiedAdmin).requestToken("testGroup"))
        .isEqualTo(
            QuotaResponse.Aggregated.create(
                ImmutableList.of(QuotaResponse.error("fail"), QuotaResponse.noOp())));

    verify(quotaEnforcerA).requestTokens("testGroup", ctx, 1);
    verify(quotaEnforcerB).requestTokens("testGroup", ctx, 1);
  }

  @Test
  public void minimumAvailableTokens() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.availableTokens("testGroup", ctx)).thenReturn(QuotaResponse.ok(20L));
    when(quotaEnforcerB.availableTokens("testGroup", ctx)).thenReturn(QuotaResponse.ok(10L));

    OptionalLong tokens =
        quotaBackend.user(identifiedAdmin).availableTokens("testGroup").availableTokens();
    assertThat(tokens).isPresent();
    assertThat(tokens.getAsLong()).isEqualTo(10L);

    verify(quotaEnforcerA).availableTokens("testGroup", ctx);
    verify(quotaEnforcerB).availableTokens("testGroup", ctx);
  }

  @Test
  public void quotaExceededMessageIsShownForMostRestrictiveEnforcer() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.availableTokens("testGroup", ctx))
        .thenReturn(QuotaResponse.ok(20L, "Message1"));
    when(quotaEnforcerB.availableTokens("testGroup", ctx))
        .thenReturn(QuotaResponse.ok(10L, "Message2"));

    Optional<String> quotaExceededMessage =
        quotaBackend
            .user(identifiedAdmin)
            .availableTokens("testGroup")
            .mostRestrictiveQuotaExceededMessage();
    assertThat(quotaExceededMessage).hasValue("Message2");
  }

  @Test
  public void quotaExceededMessagesAreJoinedForEquallyRestrictiveEnforcers() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.availableTokens("testGroup", ctx))
        .thenReturn(QuotaResponse.ok(10L, "Message1"));
    when(quotaEnforcerB.availableTokens("testGroup", ctx))
        .thenReturn(QuotaResponse.ok(10L, "Message2"));

    Optional<String> quotaExceededMessage =
        quotaBackend
            .user(identifiedAdmin)
            .availableTokens("testGroup")
            .mostRestrictiveQuotaExceededMessage();

    assertThat(quotaExceededMessage).hasValue("Message1,Message2");
  }

  @Test
  public void ignoreNoOpForAvailableTokens() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcerA.availableTokens("testGroup", ctx)).thenReturn(QuotaResponse.noOp());
    when(quotaEnforcerB.availableTokens("testGroup", ctx)).thenReturn(QuotaResponse.ok(20L));

    OptionalLong tokens =
        quotaBackend.user(identifiedAdmin).availableTokens("testGroup").availableTokens();
    assertThat(tokens).isPresent();
    assertThat(tokens.getAsLong()).isEqualTo(20L);

    verify(quotaEnforcerA).availableTokens("testGroup", ctx);
    verify(quotaEnforcerB).availableTokens("testGroup", ctx);
  }
}
