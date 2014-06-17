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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface FileApi {
  String content() throws RestApiException;

  /**
   * diff against the revision's parent version of the file
   */
  DiffInfo diff() throws RestApiException;

  /**
   * @param base revision id of the revision to be used as the
   * diff base
   */
  DiffInfo diff(String base) throws RestApiException;

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements FileApi {

    @Override
    public String content() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DiffInfo diff() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DiffInfo diff(String base) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
