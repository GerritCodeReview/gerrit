// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.restapi.AuthException;
import java.util.Optional;

public class PermissionDeniedException extends AuthException {
  private static final long serialVersionUID = 1L;

  private final Describable permission;
  private final Optional<String> resource;

  public PermissionDeniedException(Describable permission) {
    super(permission + " not permitted");
    this.permission = checkNotNull(permission);
    this.resource = Optional.empty();
  }

  public PermissionDeniedException(Describable permission, String resource) {
    super(permission + " not permitted on " + resource);
    this.permission = checkNotNull(permission);
    this.resource = Optional.of(resource);
  }

  public String describePermission() {
    return permission.describeForException();
  }

  public Optional<String> getResource() {
    return resource;
  }
}
