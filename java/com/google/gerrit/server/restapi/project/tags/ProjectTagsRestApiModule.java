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

package com.google.gerrit.server.restapi.project.tags;

import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.google.gerrit.server.project.TagResource.TAG_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class ProjectTagsRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), TAG_KIND);

    /** List tags in a project {@code GET /projects/<project-id>/tags}. */
    child(PROJECT_KIND, "tags").to(TagsCollection.class);

    /** Create tag in a project {@code PUT /projects/<project-id>/tags/<tag-id>}. */
    create(TAG_KIND).to(CreateTag.class);
    /** Get tag in a project {@code GET /projects/<project-id>/tags/<tag-id>}. */
    get(TAG_KIND).to(GetTag.class);
    /** Update tag in a project {@code PUT /projects/<project-id>/tags/<tag-id>}. */
    put(TAG_KIND).to(PutTag.class);
    /** Delete tag in a project {@code DELETE /projects/<project-id>/tags/<tag-id>}. */
    delete(TAG_KIND).to(DeleteTag.class);
  }
}
