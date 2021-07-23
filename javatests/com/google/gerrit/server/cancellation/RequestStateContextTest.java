// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.cancellation;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class RequestStateContextTest {
  @Test
  public void openContext() {
    assertNoRequestStateProviders();

    RequestStateProvider requestStateProvider1 = new TestRequestStateProvider();
    try (RequestStateContext requestStateContext =
        RequestStateContext.open().addRequestStateProvider(requestStateProvider1)) {
      RequestStateProvider requestStateProvider2 = new TestRequestStateProvider();
      requestStateContext.addRequestStateProvider(requestStateProvider2);
      assertRequestStateProviders(ImmutableSet.of(requestStateProvider1, requestStateProvider2));
    }

    assertNoRequestStateProviders();
  }

  @Test
  public void openNestedContexts() {
    assertNoRequestStateProviders();

    RequestStateProvider requestStateProvider1 = new TestRequestStateProvider();
    try (RequestStateContext requestStateContext =
        RequestStateContext.open().addRequestStateProvider(requestStateProvider1)) {
      RequestStateProvider requestStateProvider2 = new TestRequestStateProvider();
      requestStateContext.addRequestStateProvider(requestStateProvider2);
      assertRequestStateProviders(ImmutableSet.of(requestStateProvider1, requestStateProvider2));

      RequestStateProvider requestStateProvider3 = new TestRequestStateProvider();
      try (RequestStateContext requestStateContext2 =
          RequestStateContext.open().addRequestStateProvider(requestStateProvider3)) {
        RequestStateProvider requestStateProvider4 = new TestRequestStateProvider();
        requestStateContext2.addRequestStateProvider(requestStateProvider4);
        assertRequestStateProviders(
            ImmutableSet.of(
                requestStateProvider1,
                requestStateProvider2,
                requestStateProvider3,
                requestStateProvider4));
      }

      assertRequestStateProviders(ImmutableSet.of(requestStateProvider1, requestStateProvider2));
    }

    assertNoRequestStateProviders();
  }

  @Test
  public void openNestedContextsWithSameRequestStateProviders() {
    assertNoRequestStateProviders();

    RequestStateProvider requestStateProvider1 = new TestRequestStateProvider();
    try (RequestStateContext requestStateContext =
        RequestStateContext.open().addRequestStateProvider(requestStateProvider1)) {
      RequestStateProvider requestStateProvider2 = new TestRequestStateProvider();
      requestStateContext.addRequestStateProvider(requestStateProvider2);
      assertRequestStateProviders(ImmutableSet.of(requestStateProvider1, requestStateProvider2));

      try (RequestStateContext requestStateContext2 =
          RequestStateContext.open().addRequestStateProvider(requestStateProvider1)) {
        requestStateContext2.addRequestStateProvider(requestStateProvider2);

        assertRequestStateProviders(ImmutableSet.of(requestStateProvider1, requestStateProvider2));
      }

      assertRequestStateProviders(ImmutableSet.of(requestStateProvider1, requestStateProvider2));
    }

    assertNoRequestStateProviders();
  }

  private void assertNoRequestStateProviders() {
    assertRequestStateProviders(ImmutableSet.of());
  }

  private void assertRequestStateProviders(
      ImmutableSet<RequestStateProvider> expectedRequestStateProviders) {
    assertThat(RequestStateContext.getRequestStateProviders())
        .containsExactlyElementsIn(expectedRequestStateProviders);
  }

  private static class TestRequestStateProvider implements RequestStateProvider {
    @Override
    public void checkIfCancelled(OnCancelled onCancelled) {}
  }
}
