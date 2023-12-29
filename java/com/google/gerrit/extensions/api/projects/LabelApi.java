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

package com.google.gerrit.extensions.api.projects;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface LabelApi {
  @CanIgnoreReturnValue
  LabelApi create(LabelDefinitionInput input) throws RestApiException;

  LabelDefinitionInfo get() throws RestApiException;

  @CanIgnoreReturnValue
  LabelDefinitionInfo update(LabelDefinitionInput input) throws RestApiException;

  default void delete() throws RestApiException {
    delete(null);
  }

  void delete(@Nullable String commitMessage) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements LabelApi {
    @Override
    public LabelApi create(LabelDefinitionInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public LabelDefinitionInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public LabelDefinitionInfo update(LabelDefinitionInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void delete(@Nullable String commitMessage) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
