// Copyright (C) 2026 The Android Open Source Project
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

/**
 * {@link RequestStateProvider} that reports a request as cancelled once the servlet container was
 * notified that the client closed the connection.
 *
 * <p>The servlet container sets an instance as the {@link #REQUEST_ATTRIBUTE} request attribute and
 * calls {@link #onClientDisconnected()} from its I/O thread. Containers that don't support
 * disconnect detection don't set the attribute.
 */
public final class ClientDisconnectedRequestState implements RequestStateProvider {
  public static final String REQUEST_ATTRIBUTE = ClientDisconnectedRequestState.class.getName();

  private volatile boolean disconnected;

  public void onClientDisconnected() {
    disconnected = true;
  }

  @Override
  public void checkIfCancelled(OnCancelled onCancelled) {
    if (disconnected) {
      onCancelled.onCancel(Reason.CLIENT_CLOSED_REQUEST, /* message= */ null);
    }
  }
}
