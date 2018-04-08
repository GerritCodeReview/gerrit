// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.git;

import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Simple short-lived cache of individual refs read from a repo.
 *
 * <p>Within a single request that is known to read a small bounded number of refs, this class can
 * be used to ensure a consistent view of one ref, and avoid multiple system calls to read refs
 * multiple times.
 *
 * <p><strong>Note:</strong> Implementations of this class are only appropriate for short-term
 * caching, and do not support invalidation. It is also not threadsafe.
 */
public interface RefCache {
  /**
   * Get the possibly-cached value of a ref.
   *
   * @param refName name of the ref.
   * @return value of the ref; absent if the ref does not exist in the repo. Never null, and never
   *     present with a value of {@link ObjectId#zeroId()}.
   */
  Optional<ObjectId> get(String refName) throws IOException;
}
