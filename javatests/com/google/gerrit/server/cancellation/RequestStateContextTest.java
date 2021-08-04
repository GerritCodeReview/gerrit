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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.cancellation.RequestStateContext.NonCancellableOperationContext;
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

  @Test
  public void abortIfCancelled_noRequestStateProvider() {
    assertNoRequestStateProviders();

    // Calling abortIfCancelled() shouldn't throw an exception.
    RequestStateContext.abortIfCancelled();
  }

  @Test
  public void abortIfCancelled_requestNotCancelled() {
    try (RequestStateContext requestStateContext =
        RequestStateContext.open()
            .addRequestStateProvider(
                new RequestStateProvider() {
                  @Override
                  public void checkIfCancelled(OnCancelled onCancelled) {}
                })) {
      // Calling abortIfCancelled() shouldn't throw an exception.
      RequestStateContext.abortIfCancelled();
    }
  }

  @Test
  public void abortIfCancelled_requestCancelled() {
    try (RequestStateContext requestStateContext =
        RequestStateContext.open()
            .addRequestStateProvider(
                new RequestStateProvider() {
                  @Override
                  public void checkIfCancelled(OnCancelled onCancelled) {
                    onCancelled.onCancel(
                        RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* message= */ null);
                  }
                })) {
      RequestCancelledException requestCancelledException =
          assertThrows(
              RequestCancelledException.class, () -> RequestStateContext.abortIfCancelled());
      assertThat(requestCancelledException)
          .hasMessageThat()
          .isEqualTo("Request cancelled: CLIENT_CLOSED_REQUEST");
      assertThat(requestCancelledException.getCancellationReason())
          .isEqualTo(RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST);
      assertThat(requestCancelledException.getCancellationMessage()).isEmpty();
    }
  }

  @Test
  public void abortIfCancelled_requestCancelled_withMessage() {
    try (RequestStateContext requestStateContext =
        RequestStateContext.open()
            .addRequestStateProvider(
                new RequestStateProvider() {
                  @Override
                  public void checkIfCancelled(OnCancelled onCancelled) {
                    onCancelled.onCancel(
                        RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m");
                  }
                })) {
      RequestCancelledException requestCancelledException =
          assertThrows(
              RequestCancelledException.class, () -> RequestStateContext.abortIfCancelled());
      assertThat(requestCancelledException)
          .hasMessageThat()
          .isEqualTo("Request cancelled: SERVER_DEADLINE_EXCEEDED (deadline = 10m)");
      assertThat(requestCancelledException.getCancellationReason())
          .isEqualTo(RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED);
      assertThat(requestCancelledException.getCancellationMessage()).hasValue("deadline = 10m");
    }
  }

  @Test
  public void nonCancellableOperation_requestNotCanclled() {
    try (RequestStateContext requestStateContext =
        RequestStateContext.open()
            .addRequestStateProvider(
                new RequestStateProvider() {
                  @Override
                  public void checkIfCancelled(OnCancelled onCancelled) {}
                })) {
      // Calling abortIfCancelled() shouldn't throw an exception.
      RequestStateContext.abortIfCancelled();
      try (NonCancellableOperationContext nonCancellableOperationContext =
          RequestStateContext.startNonCancellableOperation()) {
        // Calling abortIfCancelled() shouldn't throw an exception.
        RequestStateContext.abortIfCancelled();
      }
      // Calling abortIfCancelled() shouldn't throw an exception.
      RequestStateContext.abortIfCancelled();
    }
  }

  @Test
  public void nonCancellableOperationNotAborted() {
    try (RequestStateContext requestStateContext =
        RequestStateContext.open()
            .addRequestStateProvider(
                new RequestStateProvider() {
                  @Override
                  public void checkIfCancelled(OnCancelled onCancelled) {
                    onCancelled.onCancel(
                        RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* message= */ null);
                  }
                })) {
      assertThrows(RequestCancelledException.class, () -> RequestStateContext.abortIfCancelled());
      boolean cancelledOnClose = false;
      try (NonCancellableOperationContext nonCancellableOperationContext =
          RequestStateContext.startNonCancellableOperation()) {
        // Calling abortIfCancelled() shouldn't throw an exception since we are within a
        // non-cancellable operation.
        RequestStateContext.abortIfCancelled();
      } catch (RequestCancelledException e) {
        // The request is expected to get aborted on close of the non-cancellable operation.
        cancelledOnClose = true;
      }
      assertThat(cancelledOnClose).isTrue();
    }
  }

  @Test
  public void nestedNonCancellableOperationNotAborted() {
    try (RequestStateContext requestStateContext =
        RequestStateContext.open()
            .addRequestStateProvider(
                new RequestStateProvider() {
                  @Override
                  public void checkIfCancelled(OnCancelled onCancelled) {
                    onCancelled.onCancel(
                        RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* message= */ null);
                  }
                })) {
      assertThrows(RequestCancelledException.class, () -> RequestStateContext.abortIfCancelled());
      boolean cancelledOnClose = false;
      try (NonCancellableOperationContext nonCancellableOperationContext =
          RequestStateContext.startNonCancellableOperation()) {
        // Calling abortIfCancelled() shouldn't throw an exception since we are within a
        // non-cancellable operation.
        RequestStateContext.abortIfCancelled();

        try (NonCancellableOperationContext nestedNonCancellableOperationContext =
            RequestStateContext.startNonCancellableOperation()) {
          // Calling abortIfCancelled() shouldn't throw an exception since we are within a
          // non-cacellable operation.
          RequestStateContext.abortIfCancelled();

          // Close of the nestedNonCancellableOperationContext shouldn't throw an exception since
          // the outer nonCancellableOperationContext is still open.
        }

        // Calling abortIfCancelled() shouldn't throw an exception since we are within a
        // non-cancellable operation.
        RequestStateContext.abortIfCancelled();
      } catch (RequestCancelledException e) {
        // The request is expected to get aborted on close of the non-cancellable operation.
        cancelledOnClose = true;
      }
      assertThat(cancelledOnClose).isTrue();
    }
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
