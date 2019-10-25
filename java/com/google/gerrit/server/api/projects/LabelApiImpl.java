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

package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.projects.LabelApi;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.restapi.project.GetLabel;
import com.google.gerrit.server.restapi.project.SetLabel;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class LabelApiImpl implements LabelApi {
  interface Factory {
    LabelApiImpl create(LabelResource rsrc);
  }

  private final GetLabel getLabel;
  private final SetLabel setLabel;
  private final LabelResource rsrc;

  @Inject
  LabelApiImpl(GetLabel getLabel, SetLabel setLabel, @Assisted LabelResource rsrc) {
    this.getLabel = getLabel;
    this.setLabel = setLabel;
    this.rsrc = rsrc;
  }

  @Override
  public LabelDefinitionInfo get() throws RestApiException {
    try {
      return getLabel.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get label", e);
    }
  }

  @Override
  public LabelDefinitionInfo update(LabelDefinitionInput input) throws RestApiException {
    try {
      return setLabel.apply(rsrc, input).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot update label", e);
    }
  }
}
