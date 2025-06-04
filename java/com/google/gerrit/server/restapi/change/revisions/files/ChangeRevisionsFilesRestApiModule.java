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

/**
 * Guice module that binds all REST endpoints for {@code
 * /changes/<change-id>/revisions/<revision-id>/files}.
 */
public class ChangeRevisionsFilesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Files.class);

    DynamicMap.mapOf(binder(), FILE_KIND);

    /**
     * List files for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/files}.
     */
    child(REVISION_KIND, "files").to(Files.class);

    /**
     * Get blame for file in change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/files/<file-id>/blame}.
     */
    get(FILE_KIND, "blame").to(GetBlame.class);

    /**
     * Get content of file in change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/files/<file-id>/content}.
     */
    get(FILE_KIND, "content").to(GetContent.class);

    /**
     * Get diff of file in change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/files/<file-id>/diff}.
     */
    get(FILE_KIND, "diff").to(GetDiff.class);

    /**
     * Download file in change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/files/<file-id>/download}.
     */
    get(FILE_KIND, "download").to(DownloadContent.class);

    /**
     * Mark file in change revision as reviewed {@code PUT
     * /changes/<change-id>/revisions/<revision-id>/files/<file-id>/reviewed}.
     */
    put(FILE_KIND, "reviewed").to(PutReviewed.class);
    /**
     * Unmark file in change revision as reviewed {@code DELETE
     * /changes/<change-id>/revisions/<revision-id>/files/<file-id>/reviewed}.
     */
    delete(FILE_KIND, "reviewed").to(DeleteReviewed.class);
  }
}
