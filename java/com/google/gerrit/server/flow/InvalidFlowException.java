// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.flow;

/**
 * Exception to be thrown either directly or subclassed indicating that a flow be to created is
 * invalid (e.g. because a condition cannot be parsed, because an action is unknown or because
 * mandatory parameters for an action are missing).
 */
public class InvalidFlowException extends Exception {
  private static final long serialVersionUID = 1L;

  public InvalidFlowException(String reason) {
    super(reason);
  }

  public InvalidFlowException(String reason, Throwable why) {
    super(reason, why);
  }
}
