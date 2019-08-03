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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotesStatesLoader;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Singleton
public class GetHashtags implements RestReadView<ChangeResource> {

  private final ChangeNotesStatesLoader.Factory changeNotesStateLoaderFactory;

  @Inject
  public GetHashtags(ChangeNotesStatesLoader.Factory changeNotesStateLoaderFactory) {
    this.changeNotesStateLoaderFactory = changeNotesStateLoaderFactory;
  }

  @Override
  public Response<Set<String>> apply(ChangeResource req)
      throws AuthException, IOException, BadRequestException {
    ChangeNotesStatesLoader loader = changeNotesStateLoaderFactory.createChecked(req.getChange());
    Set<String> hashtags = loader.state().hashtags();
    if (hashtags == null) {
      hashtags = Collections.emptySet();
    }
    return Response.ok(hashtags);
  }
}
