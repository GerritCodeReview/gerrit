// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Singleton
public class GetHashtags implements RestReadView<ChangeResource> {
  @Override
  public Response<Set<String>> apply(ChangeResource req)
      throws AuthException, OrmException, IOException, BadRequestException {
    ChangeControl control = req.getControl();
    ChangeNotes notes = control.getNotes().load();
    Set<String> hashtags = notes.getHashtags();
    if (hashtags == null) {
      hashtags = Collections.emptySet();
    }
    return Response.ok(hashtags);
  }
}
