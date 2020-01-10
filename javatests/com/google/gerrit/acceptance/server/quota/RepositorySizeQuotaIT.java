// Copyright (C) 2019 The Android Open Source Project
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
import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;
import static com.google.gerrit.server.quota.QuotaResponse.ok;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Module;
import java.util.Collections;
import org.eclipse.jgit.api.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;

@UseLocalDisk
public class RepositorySizeQuotaIT extends AbstractDaemonTest {
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
  public void pushWithAvailableTokens() throws Exception {
    when(quotaBackendWithResource.availableTokens(REPOSITORY_SIZE_GROUP))
        .thenReturn(singletonAggregation(ok(276L)));
    when(quotaBackendWithResource.requestTokens(eq(REPOSITORY_SIZE_GROUP), anyLong()))
        .thenReturn(singletonAggregation(ok()));
    when(quotaBackendWithUser.project(project)).thenReturn(quotaBackendWithResource);
    pushCommit();
    verify(quotaBackendWithResource, times(2)).availableTokens(REPOSITORY_SIZE_GROUP);
  }

  @Test
  public void pushWithNotSufficientTokens() throws Exception {
    long availableTokens = 1L;
    when(quotaBackendWithResource.availableTokens(REPOSITORY_SIZE_GROUP))
        .thenReturn(singletonAggregation(ok(availableTokens)));
    when(quotaBackendWithUser.project(project)).thenReturn(quotaBackendWithResource);
    TooLargeObjectInPackException thrown =
        assertThrows(TooLargeObjectInPackException.class, () -> pushCommit());
    assertThat(thrown).hasMessageThat().contains("Object too large");
    assertThat(thrown)
        .hasMessageThat()
        .contains(String.format("Max object size limit is %d bytes.", availableTokens));
  }

  @Test
  public void errorGettingAvailableTokens() throws Exception {
    String msg = "quota error";
    when(quotaBackendWithResource.availableTokens(REPOSITORY_SIZE_GROUP))
        .thenReturn(singletonAggregation(QuotaResponse.error(msg)));
    when(quotaBackendWithUser.project(project)).thenReturn(quotaBackendWithResource);
    assertThrows(TransportException.class, () -> pushCommit());
  }

  private void pushCommit() throws Exception {
    createCommitAndPush(testRepo, "refs/heads/master", "test 01", "file.test", "some content");
  }

  private static QuotaResponse.Aggregated singletonAggregation(QuotaResponse response) {
    return QuotaResponse.Aggregated.create(Collections.singleton(response));
  }
}
