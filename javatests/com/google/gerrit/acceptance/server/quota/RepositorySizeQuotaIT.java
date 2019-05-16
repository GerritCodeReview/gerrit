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
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;
import static com.google.gerrit.server.quota.QuotaResponse.ok;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.resetToStrict;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Module;
import java.util.Collections;
import org.easymock.EasyMock;
import org.eclipse.jgit.api.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;

@UseLocalDisk
public class RepositorySizeQuotaIT extends AbstractDaemonTest {
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
    resetToStrict(quotaBackendWithResource);
    resetToStrict(quotaBackendWithUser);
  }

  @Test
  public void pushWithAvailableTokens() throws Exception {
    expect(quotaBackendWithResource.availableTokens(REPOSITORY_SIZE_GROUP))
        .andReturn(singletonAggregation(ok(276L)))
        .times(2);
    expect(quotaBackendWithResource.requestTokens(eq(REPOSITORY_SIZE_GROUP), anyLong()))
        .andReturn(singletonAggregation(ok()));
    expect(quotaBackendWithUser.project(project)).andReturn(quotaBackendWithResource).anyTimes();
    replay(quotaBackendWithResource);
    replay(quotaBackendWithUser);
    pushCommit();
    verify(quotaBackendWithUser);
    verify(quotaBackendWithResource);
  }

  @Test
  public void pushWithNotSufficientTokens() throws Exception {
    long availableTokens = 1L;
    expect(quotaBackendWithResource.availableTokens(REPOSITORY_SIZE_GROUP))
        .andReturn(singletonAggregation(ok(availableTokens)))
        .anyTimes();
    expect(quotaBackendWithUser.project(project)).andReturn(quotaBackendWithResource).anyTimes();
    replay(quotaBackendWithResource);
    replay(quotaBackendWithUser);
    try {
      pushCommit();
      assert_().fail("expected TooLargeObjectInPackException");
    } catch (TooLargeObjectInPackException e) {
      String msg = e.getMessage();
      assertThat(msg).contains("Object too large");
      assertThat(msg)
          .contains(String.format("Max object size limit is %d bytes.", availableTokens));
    }
    verify(quotaBackendWithUser);
    verify(quotaBackendWithResource);
  }

  @Test
  public void errorGettingAvailableTokens() throws Exception {
    String msg = "quota error";
    expect(quotaBackendWithResource.availableTokens(REPOSITORY_SIZE_GROUP))
        .andReturn(singletonAggregation(QuotaResponse.error(msg)))
        .anyTimes();
    expect(quotaBackendWithUser.project(project)).andReturn(quotaBackendWithResource).anyTimes();
    replay(quotaBackendWithResource);
    replay(quotaBackendWithUser);
    try {
      pushCommit();
      assert_().fail("expected TransportException");
    } catch (TransportException e) {
      // TransportException has not much info about the cause
    }
    verify(quotaBackendWithUser);
    verify(quotaBackendWithResource);
  }

  private void pushCommit() throws Exception {
    createCommitAndPush(testRepo, "refs/heads/master", "test 01", "file.test", "some content");
  }

  private static QuotaResponse.Aggregated singletonAggregation(QuotaResponse response) {
    return QuotaResponse.Aggregated.create(Collections.singleton(response));
  }
}
