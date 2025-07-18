// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.PasswordMigrator;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.restapi.account.CreateToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Future;

@RequiresCapability(GlobalCapability.MAINTAIN_SERVER)
@Singleton
public class MigratePasswordsToTokens
    implements RestModifyView<
        ConfigResource, com.google.gerrit.server.restapi.config.MigratePasswordsToTokens.Input> {
  static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final WorkQueue workQueue;
  private final PasswordMigrator.Factory passwordMigratorFactory;

  static class Input {
    String lifetime;
  }

  @Inject
  public MigratePasswordsToTokens(
      WorkQueue workQueue, PasswordMigrator.Factory passwordMigratorFactory) {
    this.workQueue = workQueue;
    this.passwordMigratorFactory = passwordMigratorFactory;
  }

  @Override
  public Response<?> apply(ConfigResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Optional<Instant> expirationDate =
        CreateToken.getExpirationInstant(input.lifetime, Optional.empty());
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        workQueue.getDefaultQueue().submit(passwordMigratorFactory.create(expirationDate));
    return Response.accepted("Password Migrator task added to work queue.");
  }
}
