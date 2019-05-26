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
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaEnforcer;
import com.google.gerrit.server.quota.QuotaException;
import com.google.gerrit.server.quota.QuotaRequestContext;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class DefaultQuotaBackendIT extends AbstractDaemonTest {

  private static final QuotaEnforcer quotaEnforcer = mock(QuotaEnforcer.class);

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
    clearInvocations(quotaEnforcer);
  }

  @Test
  public void requestTokenForUser() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.ok());
    assertThat(quotaBackend.user(identifiedAdmin).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenForUserAndAccount() {
    QuotaRequestContext ctx =
        QuotaRequestContext.builder().user(identifiedAdmin).account(user.id()).build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.ok());
    assertThat(quotaBackend.user(identifiedAdmin).account(user.id()).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenForUserAndProject() {
    QuotaRequestContext ctx =
        QuotaRequestContext.builder().user(identifiedAdmin).project(project).build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.ok());
    assertThat(quotaBackend.user(identifiedAdmin).project(project).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokenForUserAndChange() throws Exception {
    Change.Id changeId = retrieveChangeId();
    QuotaRequestContext ctx =
        QuotaRequestContext.builder()
            .user(identifiedAdmin)
            .change(changeId)
            .project(project)
            .build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 1)).thenReturn(QuotaResponse.ok());
    assertThat(
            quotaBackend.user(identifiedAdmin).change(changeId, project).requestToken("testGroup"))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void requestTokens() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 123)).thenReturn(QuotaResponse.ok());
    assertThat(quotaBackend.user(identifiedAdmin).requestTokens("testGroup", 123))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void dryRun() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.dryRun("testGroup", ctx, 123)).thenReturn(QuotaResponse.ok());
    assertThat(quotaBackend.user(identifiedAdmin).dryRun("testGroup", 123))
        .isEqualTo(singletonAggregation(QuotaResponse.ok()));
  }

  @Test
  public void availableTokensForUserAndAccount() {
    QuotaRequestContext ctx =
        QuotaRequestContext.builder().user(identifiedAdmin).account(user.id()).build();
    QuotaResponse r = QuotaResponse.ok(10L);
    when(quotaEnforcer.availableTokens("testGroup", ctx)).thenReturn(r);
    assertThat(quotaBackend.user(identifiedAdmin).account(user.id()).availableTokens("testGroup"))
        .isEqualTo(singletonAggregation(r));
  }

  @Test
  public void availableTokensForUserAndProject() {
    QuotaRequestContext ctx =
        QuotaRequestContext.builder().user(identifiedAdmin).project(project).build();
    QuotaResponse r = QuotaResponse.ok(10L);
    when(quotaEnforcer.availableTokens("testGroup", ctx)).thenReturn(r);
    assertThat(quotaBackend.user(identifiedAdmin).project(project).availableTokens("testGroup"))
        .isEqualTo(singletonAggregation(r));
  }

  @Test
  public void availableTokensForUserAndChange() throws Exception {
    Change.Id changeId = retrieveChangeId();
    QuotaRequestContext ctx =
        QuotaRequestContext.builder()
            .user(identifiedAdmin)
            .change(changeId)
            .project(project)
            .build();
    QuotaResponse r = QuotaResponse.ok(10L);
    when(quotaEnforcer.availableTokens("testGroup", ctx)).thenReturn(r);
    assertThat(
            quotaBackend
                .user(identifiedAdmin)
                .change(changeId, project)
                .availableTokens("testGroup"))
        .isEqualTo(singletonAggregation(r));
  }

  @Test
  public void availableTokens() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    QuotaResponse r = QuotaResponse.ok(10L);
    when(quotaEnforcer.availableTokens("testGroup", ctx)).thenReturn(r);
    assertThat(quotaBackend.user(identifiedAdmin).availableTokens("testGroup"))
        .isEqualTo(singletonAggregation(r));
  }

  @Test
  public void requestTokenError() throws Exception {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 1))
        .thenReturn(QuotaResponse.error("failed"));

    QuotaResponse.Aggregated result = quotaBackend.user(identifiedAdmin).requestToken("testGroup");
    assertThat(result).isEqualTo(singletonAggregation(QuotaResponse.error("failed")));
    QuotaException thrown = assertThrows(QuotaException.class, () -> result.throwOnError());
    assertThat(thrown).hasMessageThat().contains("failed");
  }

  @Test
  public void availableTokensError() throws Exception {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.availableTokens("testGroup", ctx)).thenReturn(QuotaResponse.error("failed"));
    QuotaResponse.Aggregated result =
        quotaBackend.user(identifiedAdmin).availableTokens("testGroup");
    assertThat(result).isEqualTo(singletonAggregation(QuotaResponse.error("failed")));
    QuotaException thrown = assertThrows(QuotaException.class, () -> result.throwOnError());
    assertThat(thrown).hasMessageThat().contains("failed");
  }

  @Test
  public void requestTokenPluginThrowsAndRethrows() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.requestTokens("testGroup", ctx, 1)).thenThrow(new NullPointerException());

    assertThrows(
        NullPointerException.class,
        () -> quotaBackend.user(identifiedAdmin).requestToken("testGroup"));
  }

  @Test
  public void availableTokensPluginThrowsAndRethrows() {
    QuotaRequestContext ctx = QuotaRequestContext.builder().user(identifiedAdmin).build();
    when(quotaEnforcer.availableTokens("testGroup", ctx)).thenThrow(new NullPointerException());

    assertThrows(
        NullPointerException.class,
        () -> quotaBackend.user(identifiedAdmin).availableTokens("testGroup"));
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
