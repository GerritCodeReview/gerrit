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

/** Algorithm for encoding a serverId/legacyChangeNum into a virtual numeric id */
@ImplementedBy(ChangeNumberNoopAlgorithm.class)
public interface ChangeNumberVirtualIdAlgorithm {

  /**
   * Convert a serverId/legacyChangeNum tuple into a virtual numeric id
   *
   * @param serverId Gerrit serverId
   * @param legacyChangeNum legacy change number
   * @return virtual id which combines serverId and legacyChangeNum together
   */
  Change.Id apply(Supplier<String> serverId, Change.Id legacyChangeNum);
}
