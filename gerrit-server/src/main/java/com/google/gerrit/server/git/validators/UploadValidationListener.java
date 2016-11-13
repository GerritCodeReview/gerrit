// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.validators.ValidationException;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;

/**
 * Listener to provide validation for upload operations.
 *
 * <p>Invoked by Gerrit before it begins to send a pack to the client.
 *
 * <p>Implementors can block the upload operation by throwing a ValidationException. The exception's
 * message text will be reported to the end-user over the client's protocol connection.
 */
@ExtensionPoint
public interface UploadValidationListener {

  /**
   * Validate an upload before it begins.
   *
   * @param repository The repository
   * @param project The project
   * @param remoteHost Remote address/hostname of the user
   * @param wants The list of wanted objects. These may be RevObject or RevCommit if the processor
   *     parsed them. Implementors should not rely on the values being parsed.
   * @param haves The list of common objects. Empty on an initial clone request. These may be
   *     RevObject or RevCommit if the processor parsed them. Implementors should not rely on the
   *     values being parsed.
   * @throws ValidationException to block the upload and send a message back to the end-user over
   *     the client's protocol connection.
   */
  void onPreUpload(
      Repository repository,
      Project project,
      String remoteHost,
      UploadPack up,
      Collection<? extends ObjectId> wants,
      Collection<? extends ObjectId> haves)
      throws ValidationException;
}
