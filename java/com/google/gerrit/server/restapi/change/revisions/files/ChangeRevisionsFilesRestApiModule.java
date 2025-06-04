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

package com.google.gerrit.server.restapi.change.revisions.files;

import static com.google.gerrit.server.change.FileResource.FILE_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.revisions.files.Reviewed.DeleteReviewed;
import com.google.gerrit.server.restapi.change.revisions.files.Reviewed.PutReviewed;

public class ChangeRevisionsFilesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Files.class);

    DynamicMap.mapOf(binder(), FILE_KIND);

    child(REVISION_KIND, "files").to(Files.class);
    get(FILE_KIND, "blame").to(GetBlame.class);
    get(FILE_KIND, "content").to(GetContent.class);
    get(FILE_KIND, "diff").to(GetDiff.class);
    get(FILE_KIND, "download").to(DownloadContent.class);
    put(FILE_KIND, "reviewed").to(PutReviewed.class);
    delete(FILE_KIND, "reviewed").to(DeleteReviewed.class);
  }
}
