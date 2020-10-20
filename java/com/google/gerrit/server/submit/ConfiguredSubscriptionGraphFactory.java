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

package com.google.gerrit.server.submit;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Wrap a {@link SubscriptionGraph.Factory} to honor the gerrit configuration.
 *
 * <p>If superproject subscriptions are disabled in the conf, return an empty graph.
 */
public class ConfiguredSubscriptionGraphFactory implements SubscriptionGraph.Factory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubscriptionGraph.Factory subscriptionGraphFactory;
  private final Config cfg;

  @Inject
  ConfiguredSubscriptionGraphFactory(
      @VanillaSubscriptionGraph SubscriptionGraph.Factory subscriptionGraphFactory,
      @GerritServerConfig Config cfg) {
    this.subscriptionGraphFactory = subscriptionGraphFactory;
    this.cfg = cfg;
  }

  @Override
  public SubscriptionGraph compute(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
      throws SubmoduleConflictException {
    if (cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true)) {
      return subscriptionGraphFactory.compute(updatedBranches, orm);
    }
    logger.atFine().log("Updating superprojects disabled");
    return SubscriptionGraph.createEmptyGraph(ImmutableSet.copyOf(updatedBranches));
  }
}
