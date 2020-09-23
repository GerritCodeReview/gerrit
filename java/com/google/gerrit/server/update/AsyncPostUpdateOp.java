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
   * <p>The maximum amount of threads in the thread pool is decided by
   * asyncPostUpdate.threadPoolSize. When asyncPostUpdate.threadPoolSize is not specified, the
   * deprecated sendemail.threadPoolSize is used (see {@link
   * SysExecutorModule#provideSendEmailExecutor}). This is the case for legacy reasons, since in the
   * past only some emails were sent async (and sendemail.threadPoolSize) was used, and now all
   * emails (and possibly others) are done async, so asyncPostUpdate.threadPoolSize is used.
   *
   * @param ctx context
   */
  default void asyncPostUpdate(Context ctx) throws Exception {}
}
