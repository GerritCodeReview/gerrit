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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.api.access.ProjectAccessChangeInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface AccessApi {
  ProjectAccessInfo get() throws RestApiException;
  ProjectAccessInfo set(ProjectAccessChangeInfo p) throws RestApiException;

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  class NotImplemented implements AccessApi {
    @Override
    public ProjectAccessInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    public ProjectAccessInfo set(ProjectAccessChangeInfo p)
        throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
