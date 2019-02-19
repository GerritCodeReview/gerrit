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

package com.google.gerrit.plugins.checkers.api;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checkers.Check;
import com.google.gerrit.plugins.checkers.CheckJson;
import com.google.gerrit.plugins.checkers.CheckKey;
import com.google.gerrit.plugins.checkers.CheckUpdate;
import com.google.gerrit.plugins.checkers.Checks;
import com.google.gerrit.plugins.checkers.ChecksUpdate;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UserInitiated;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Optional;

public class CheckApiImpl implements CheckApi {
  public interface Factory {
    CheckApiImpl create(CheckResource c);
  }

  private final Checks checks;
  private final CheckJson checkJson;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckResource checkResource;

  @Inject
  CheckApiImpl(
      Checks checks,
      CheckJson checkJson,
      @UserInitiated Provider<ChecksUpdate> checksUpdate,
      @Assisted CheckResource checkResource) {
    this.checks = checks;
    this.checkJson = checkJson;
    this.checksUpdate = checksUpdate;
    this.checkResource = checkResource;
  }

  @Override
  public CheckInfo get() throws RestApiException {
    return checkJson.format(checkResource.getCheck());
  }

  @Override
  public CheckInfo update(CheckInput input) throws RestApiException, IOException, OrmException {
    if (input == null) {
      throw new BadRequestException("input is required");
    }
    if (input.checkerUUID != null && !input.checkerUUID.equals(checkResource.getCheckerUUID())) {
      throw new BadRequestException(
          "checkerUUID must either be null or the same as on the resource");
    }

    Project.NameKey project = checkResource.getRevisionResource().getProject();

    CheckKey key =
        CheckKey.create(
            project,
            checkResource.getRevisionResource().getPatchSet().getId(),
            checkResource.getCheckerUUID());
    Optional<Check> check = checks.getCheck(key);
    if (!check.isPresent()) {
      throw new ResourceNotFoundException("Not found: " + input.checkerUUID);
    }

    checksUpdate.get().updateCheck(key, toCheckUpdate(input));

    Optional<Check> updatedCheck = checks.getCheck(key);
    return checkJson.format(
        updatedCheck.orElseThrow(() -> new IOException("updated check absent")));
  }

  private static CheckUpdate toCheckUpdate(CheckInput input) {
    return CheckUpdate.builder()
        .setState(Optional.ofNullable(input.state))
        .setUrl(Optional.ofNullable(input.url))
        .setStarted(Optional.ofNullable(input.started))
        .setFinished(Optional.ofNullable(input.finished))
        .build();
  }
}
