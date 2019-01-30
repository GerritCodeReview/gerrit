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

package com.google.gerrit.server.verifier;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.IdentifiedUser;

public interface VerifiersUpdateFactory {
  /**
   * Creates a {@code VerifiersUpdate} which uses the identity of the specified user to mark
   * database modifications executed by it. For NoteDb, this identity is used as author and
   * committer for all related commits.
   *
   * <p><strong>Note</strong>: Please use this method with care and rather consider to use the
   * correct annotation on the provider of a {@code VerifiersUpdate} instead.
   *
   * @param currentUser the user to which modifications should be attributed, or {@code null} if the
   *     Gerrit server identity should be used
   */
  VerifiersUpdate create(@Nullable IdentifiedUser currentUser);
}
