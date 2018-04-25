// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

/** Interface for change message APIs. */
public interface ChangeMessageApi {
  /** Gets one change message. */
  ChangeMessageInfo get() throws RestApiException;

  /**
   * Deletes a change message by replacing its message. For NoteDb, it's implemented by rewriting
   * the commit history of change meta branch.
   *
   * @return the change message with its message updated.
   */
  ChangeMessageInfo delete(DeleteChangeMessageInput input) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ChangeMessageApi {
    @Override
    public ChangeMessageInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeMessageInfo delete(DeleteChangeMessageInput input) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
