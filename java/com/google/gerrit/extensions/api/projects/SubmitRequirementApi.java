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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface SubmitRequirementApi {
  /** Create a new submit requirement. */
  SubmitRequirementApi create(SubmitRequirementInput input) throws RestApiException;

  /** Get existing submit requirement. */
  SubmitRequirementInfo get() throws RestApiException;

  /** Update existing submit requirement. */
  SubmitRequirementInfo update(SubmitRequirementInput input) throws RestApiException;

  /** Delete existing submit requirement. */
  void delete() throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements SubmitRequirementApi {
    @Override
    public SubmitRequirementApi create(SubmitRequirementInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmitRequirementInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmitRequirementInfo update(SubmitRequirementInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void delete() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
