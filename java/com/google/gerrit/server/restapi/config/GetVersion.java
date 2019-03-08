// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class GetVersion implements RestReadView<ConfigResource> {
  @Override
  public Response<String> apply(ConfigResource resource) throws ResourceNotFoundException {
    String version = Version.getVersion();
    if (version == null) {
      throw new ResourceNotFoundException();
    }
    return Response.ok(version).caching(CacheControl.PRIVATE(30, TimeUnit.SECONDS));
  }
}
