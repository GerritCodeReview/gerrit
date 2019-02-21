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

package com.google.gerrit.plugins.checks.api;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class CheckerApiImpl implements CheckerApi {
  interface Factory {
    CheckerApiImpl create(CheckerResource rsrc);
  }

  private final GetChecker getChecker;
  private final UpdateChecker updateChecker;
  private final CheckerResource rsrc;

  @Inject
  CheckerApiImpl(
      GetChecker getChecker, UpdateChecker updateChecker, @Assisted CheckerResource rsrc) {
    this.getChecker = getChecker;
    this.updateChecker = updateChecker;

    this.rsrc = rsrc;
  }

  @Override
  public CheckerInfo get() throws RestApiException {
    try {
      return getChecker.apply(rsrc);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve checker", e);
    }
  }

  @Override
  public CheckerInfo update(CheckerInput input) throws RestApiException {
    try {
      return updateChecker.apply(rsrc, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot update checker", e);
    }
  }
}
