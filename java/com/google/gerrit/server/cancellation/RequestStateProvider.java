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

import com.google.gerrit.common.Nullable;

/** Interface that provides information about the state of the current request. */
public interface RequestStateProvider {
  /**
   * Checks whether the current request is cancelled.
   *
   * <p>Invoked by Gerrit to check whether the current request is cancelled and should be aborted.
   *
   * <p>If the current request is cancelled {@link OnCancelled#onCancel(Reason, String)} is invoked
   * on the provided callback.
   *
   * @param onCancelled callback that should be invoked if the request is cancelled
   */
  void checkIfCancelled(OnCancelled onCancelled);

  /** Callback interface to be invoked if a request is cancelled. */
  interface OnCancelled {
    /**
     * Callback that is invoked if the request is cancelled.
     *
     * @param reason the reason for the cancellation of the request
     * @param message an optional message providing details about the cancellation
     */
    void onCancel(Reason reason, @Nullable String message);
  }

  /** Reason why a request is cancelled. */
  enum Reason {
    /** The client got disconnected or has cancelled the request. */
    CLIENT_CLOSED_REQUEST,

    /** The deadline that the client provided for the request exceeded. */
    CLIENT_PROVIDED_DEADLINE_EXCEEDED,

    /**
     * A server-side deadline for the request exceeded.
     *
     * <p>Server-side deadlines are usually configurable, but may also be hard-coded.
     */
    SERVER_DEADLINE_EXCEEDED;
  }
}
