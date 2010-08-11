// Copyright (C) 2010 The Android Open Source Project
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

import java.util.EnumSet;

/**
 * Predicates that rely on ChangeData need to give some information about what
 * data from ChangeData will be needed. This enables better caching and
 * fetching.
 */
interface Prefetchable {

  /**
   * @return the set of data that will be needed by this predicate or its
   *         children.
   */
  public EnumSet<ChangeData.NeededData> getNeededData();
}
