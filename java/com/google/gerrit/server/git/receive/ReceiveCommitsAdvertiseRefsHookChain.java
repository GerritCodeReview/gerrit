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

package com.google.gerrit.server.git.receive;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.AdvertiseRefsHookChain;

/**
 * Helper to ensure that the chain for advertising refs is the same in tests and production code.
 */
public class ReceiveCommitsAdvertiseRefsHookChain {

  /**
   * Returns a single {@link AdvertiseRefsHook} that encompasses a chain of {@link
   * AdvertiseRefsHook} to be used for advertising when processing a Git push.
   */
  public static AdvertiseRefsHook create(
      AllRefsWatcher allRefsWatcher,
      Provider<InternalChangeQuery> queryProvider,
      Project.NameKey projectName) {
    return create(allRefsWatcher, queryProvider, projectName, false);
  }

  /**
   * Returns a single {@link AdvertiseRefsHook} that encompasses a chain of {@link
   * AdvertiseRefsHook} to be used for advertising when processing a Git push. Omits {@link
   * HackPushNegotiateHook} as that does not advertise refs on it's own but adds {@code .have} based
   * on history which is not relevant for the tests we have.
   */
  @VisibleForTesting
  public static AdvertiseRefsHook createForTest(
      Provider<InternalChangeQuery> queryProvider, Project.NameKey projectName) {
    return create(new AllRefsWatcher(), queryProvider, projectName, true);
  }

  private static AdvertiseRefsHook create(
      AllRefsWatcher allRefsWatcher,
      Provider<InternalChangeQuery> queryProvider,
      Project.NameKey projectName,
      boolean skipHackPushNegotiateHook) {
    List<AdvertiseRefsHook> advHooks = new ArrayList<>();
    advHooks.add(allRefsWatcher);
    advHooks.add(new ReceiveCommitsAdvertiseRefsHook(queryProvider, projectName));
    if (!skipHackPushNegotiateHook) {
      advHooks.add(new HackPushNegotiateHook());
    }
    return AdvertiseRefsHookChain.newChain(advHooks);
  }
}
