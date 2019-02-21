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

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class CheckersImpl implements Checkers {
  private final CheckerApiImpl.Factory api;
  private final CreateChecker createChecker;
  private final ListCheckers listCheckers;
  private final CheckersCollection checkers;

  @Inject
  CheckersImpl(
      CheckerApiImpl.Factory api,
      CreateChecker createChecker,
      ListCheckers listCheckers,
      CheckersCollection checkers) {
    this.api = api;
    this.createChecker = createChecker;
    this.listCheckers = listCheckers;
    this.checkers = checkers;
  }

  @Override
  public CheckerApi id(String id) throws RestApiException {
    try {
      return api.create(checkers.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve checker " + id, e);
    }
  }

  @Override
  public CheckerApi create(CheckerInput input) throws RestApiException {
    try {
      CheckerInfo info = createChecker.apply(TopLevelResource.INSTANCE, input).value();
      return id(info.uuid);
    } catch (Exception e) {
      throw asRestApiException("Cannot create checker " + input.name, e);
    }
  }

  @Override
  public List<CheckerInfo> all() throws RestApiException {
    try {
      return listCheckers.apply(TopLevelResource.INSTANCE);
    } catch (Exception e) {
      throw asRestApiException("Cannot list all checkers ", e);
    }
  }
}
