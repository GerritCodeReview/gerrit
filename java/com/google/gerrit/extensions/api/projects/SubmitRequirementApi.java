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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface SubmitRequirementApi {
  /** Create a new submit requirement. */
  @CanIgnoreReturnValue
  SubmitRequirementApi create(SubmitRequirementInput input) throws RestApiException;

  /** Get existing submit requirement. */
  SubmitRequirementInfo get() throws RestApiException;

  /** Update existing submit requirement. */
  @CanIgnoreReturnValue
  SubmitRequirementInfo update(SubmitRequirementInput input) throws RestApiException;

  /** Delete existing submit requirement. */
  void delete() throws RestApiException;
}
