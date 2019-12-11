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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.FixResource;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class GetFixPreview implements RestReadView<FixResource> {

  @Override
  public Response<Map<String, DiffInfo>> apply(FixResource resource) {
    Map<String, DiffInfo> result = new HashMap<>();
    return Response.ok(result);
  }
}
