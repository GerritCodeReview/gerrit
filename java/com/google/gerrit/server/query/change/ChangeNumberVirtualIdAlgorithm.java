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

import com.google.gerrit.entities.Change;
import com.google.inject.ImplementedBy;
import java.util.function.Supplier;

/**
 * Algorithm for encoding a serverId/legacyChangeNum into a virtual numeric id
 *
 * <p>TODO: To be reverted on master and stable-3.8
 */
@ImplementedBy(ChangeNumberNoopAlgorithm.class)
public interface ChangeNumberVirtualIdAlgorithm {

  /**
   * Virtualize a serverId/legacyChangeNum if the algorithm isn't a NoOp
   *
   * @param serverIdSupplier supplier of Gerrit serverId
   * @param legacyChangeNum legacy change number
   * @return the virtual id, when algorithm isn't a NoOp
   */
  default Change.Id applyIfVirtual(Supplier<String> serverIdSupplier, Change.Id legacyChangeNum) {
    return isNoop() ? legacyChangeNum : apply(serverIdSupplier.get(), legacyChangeNum);
  }

  /**
   * Convert a serverId/legacyChangeNum tuple into a virtual numeric id
   *
   * @param serverId Gerrit serverId
   * @param legacyChangeNum legacy change number
   * @return virtual id which combines serverId and legacyChangeNum together
   */
  Change.Id apply(String serverId, Change.Id legacyChangeNum);

  /**
   * Determine if the virtual-id algorithm is implemented or just an noop.
   *
   * @return true if the implementation is noop, therefore ids are not virtualized.
   */
  default boolean isNoop() {
    return true;
  }
}
