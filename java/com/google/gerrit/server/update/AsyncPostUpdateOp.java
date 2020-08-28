// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.gerrit.server.config.SysExecutorModule;

/** Base interface for operations performed asynchronously as part of a {@link BatchUpdate}. */
public interface AsyncPostUpdateOp {

  /**
   * Override this method to do something after the update e.g. send emails. This method will be
   * invoked asynchronously, and when invoked, the invoking method will not wait for the async
   * updates to finish. This method will be called after {@link BatchUpdateOp} operations and {@link
   * RepoOnlyOp} are finished.
   *
   * <p>The maximum amount of threads in the thread pool is decided by sendemail.threadPoolSize (see
   * {@link SysExecutorModule#provideSendEmailExecutor}). The name "sendemail" is there for legacy
   * reasons since originally only specific email operations could be done asynchronously. Other
   * async operations are now supported by this method, but they will also be limited by the same
   * threadPoolSize.
   *
   * <p>TODO(paiking): should we rename the config "sendemail" to "postUpdate", or separate async
   * email sendings from other async operations?
   *
   * @param ctx context
   */
  default void asyncPostUpdate(Context ctx) throws Exception {}
}
