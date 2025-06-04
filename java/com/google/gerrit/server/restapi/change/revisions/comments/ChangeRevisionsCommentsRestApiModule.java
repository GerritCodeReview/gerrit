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

package com.google.gerrit.server.restapi.change.revisions.comments;

import static com.google.gerrit.server.change.HumanCommentResource.COMMENT_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/**
 * Guice module that binds all REST endpoints for {@code
 * /changes/<change-id>/revisions/<revision-id>/comments}.
 */
public class ChangeRevisionsCommentsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Comments.class);

    DynamicMap.mapOf(binder(), COMMENT_KIND);

    /**
     * List comments for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/comments}.
     */
    child(REVISION_KIND, "comments").to(Comments.class);

    /**
     * Get comment for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/comments/<comment-id>}.
     */
    get(COMMENT_KIND).to(GetComment.class);
    /**
     * Delete comment for change revision {@code DELETE
     * /changes/<change-id>/revisions/<revision-id>/comments/<comment-id>}.
     */
    delete(COMMENT_KIND).to(DeleteComment.class);

    /**
     * Delete comment for change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/comments/<comment-id>/delete}.
     */
    post(COMMENT_KIND, "delete").to(DeleteComment.class);
  }
}
