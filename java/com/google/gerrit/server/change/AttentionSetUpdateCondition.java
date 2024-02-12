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

package com.google.gerrit.server.change;

/**
 * Condition to be checked by {@link AddToAttentionSetOp} and {@link RemoveFromAttentionSetOp}
 * before performing an attention set update.
 */
@FunctionalInterface
public interface AttentionSetUpdateCondition {
  /**
   * Checks whether the condition is fulfilled and the attention set update should be performed.
   *
   * @return {@code true} if the attention set should be updated, {@code false} if the attention set
   *     should not be updated
   */
  boolean check();
}
