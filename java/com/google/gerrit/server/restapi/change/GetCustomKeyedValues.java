// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class GetCustomKeyedValues implements RestReadView<ChangeResource> {
  @Override
  public Response<ImmutableMap<String, String>> apply(ChangeResource req)
      throws AuthException, IOException, BadRequestException {
    ChangeNotes notes = req.getNotes().load();
    ImmutableMap<String, String> customKeyedValues = notes.getCustomKeyedValues();
    if (customKeyedValues == null) {
      customKeyedValues = ImmutableMap.of();
    }
    return Response.ok(customKeyedValues);
  }
}
