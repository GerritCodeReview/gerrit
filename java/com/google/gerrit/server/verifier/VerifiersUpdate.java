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

import com.google.gerrit.server.verifier.db.VerifierCreation;
import com.google.gerrit.server.verifier.db.VerifierUpdate;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * A database accessor for write calls related to verifiers.
 *
 * <p>All calls which write verifier related details to the database are gathered here. Other
 * classes should always use this interface instead of accessing the database directly.
 *
 * <p>This is an interface so that the implementation can be swapped if needed.
 */
public interface VerifiersUpdate {
  /**
   * Creates the specified verifier.
   *
   * @param verifierCreation an {@code VerifierCreation} which specifies all mandatory properties of
   *     the verifier
   * @param verifierUpdate an {@code VerifierUpdate} which specifies optional properties of the
   *     verifier. If this {@code VerifierUpdate} updates a property which was already specified by
   *     the {@code VerifierCreation}, the value of this {@code VerifierUpdate} wins.
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @return the created {@code InternalGroup}
   */
  Verifier createGroup(VerifierCreation verifierCreation, VerifierUpdate verifierUpdate)
      throws IOException, ConfigInvalidException;
}
