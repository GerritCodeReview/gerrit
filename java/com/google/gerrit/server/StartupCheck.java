// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.extensions.events.LifecycleListener;

/** Check executed on Gerrit startup. */
public interface StartupCheck {
  /**
   * Performs Gerrit startup check, can abort startup by throwing {@link StartupException}.
   *
   * <p>Called on Gerrit startup after all {@link LifecycleListener} have been invoked.
   *
   * @throws StartupException thrown if Gerrit startup should be aborted
   */
  void check() throws StartupException;
}
