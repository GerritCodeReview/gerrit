// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.config.GerritImportedServerIds;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

/**
 * Dictionary-based encoding algorithm for combining a serverId/legacyChangeNum into a virtual
 * numeric id
 *
 * <p>TODO: To be reverted on master and stable-3.8
 */
@Singleton
public class ChangeNumberBitmapMaskAlgorithm implements ChangeNumberVirtualIdAlgorithm {
  /*
   * Bit-wise masks for representing the Change's VirtualId as combination of ServerId + ChangeNum:
   */
  private static final int CHANGE_NUM_BIT_LEN = 28; // Allows up to 268M changes
  private static final int LEGACY_ID_BIT_MASK = (1 << CHANGE_NUM_BIT_LEN) - 1;
  private static final int SERVER_ID_BIT_LEN =
      Integer.BYTES * 8 - CHANGE_NUM_BIT_LEN; // Allows up to 64 ServerIds

  private final ImmutableMap<String, Integer> serverIdCodes;

  @Inject
  public ChangeNumberBitmapMaskAlgorithm(
      @GerritImportedServerIds ImmutableList<String> importedServerIds) {
    if (importedServerIds.size() >= 1 << SERVER_ID_BIT_LEN) {
      throw new ProvisionException(
          String.format(
              "Too many imported GerritServerIds (%d) to fit into the Change virtual id",
              importedServerIds.size()));
    }
    ImmutableMap.Builder<String, Integer> serverIdCodesBuilder = new ImmutableMap.Builder<>();
    for (int i = 0; i < importedServerIds.size(); i++) {
      serverIdCodesBuilder.put(importedServerIds.get(i), i + 1);
    }

    serverIdCodes = serverIdCodesBuilder.build();
  }

  @Override
  public int apply(String changeServerId, int changeNum) {
    if ((changeNum & LEGACY_ID_BIT_MASK) != changeNum) {
      throw new IllegalArgumentException(
          String.format(
              "Change number %d is too large to be converted into a virtual id", changeNum));
    }

    Integer encodedServerId = serverIdCodes.get(changeServerId);
    if (encodedServerId == null) {
      throw new IllegalArgumentException(
          String.format("ServerId %s is not part of the GerritImportedServerIds", changeServerId));
    }
    int virtualId = (changeNum & LEGACY_ID_BIT_MASK) | (encodedServerId << CHANGE_NUM_BIT_LEN);

    return virtualId;
  }
}
