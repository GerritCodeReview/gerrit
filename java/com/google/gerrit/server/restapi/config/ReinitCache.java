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

package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.common.data.GlobalCapability.FLUSH_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.config.CacheResource;
import com.google.inject.Singleton;
import java.io.IOException;

/** Discards a persistent cache's entries and moves its backing file aside. */
@RequiresAnyCapability({FLUSH_CACHES, MAINTAIN_SERVER})
@Singleton
public class ReinitCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public void apply(CacheResource rsrc)
      throws UnprocessableEntityException, ResourceConflictException {
    if (!(rsrc.getCache() instanceof PersistentCache persistent)) {
      throw new UnprocessableEntityException(
          String.format("cache %s is not persistent", rsrc.getName()));
    }
    try {
      persistent.reinit();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot reinitialize cache %s", rsrc.getName());
      throw new ResourceConflictException(
          String.format("cannot reinitialize cache %s: %s", rsrc.getName(), e.getMessage()), e);
    }
  }
}
