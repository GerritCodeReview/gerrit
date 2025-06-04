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

package com.google.gerrit.server.restapi.change.revisions.drafts;

import static com.google.gerrit.server.change.DraftCommentResource.DRAFT_COMMENT_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/**
 * Guice module that binds all REST endpoints for {@code
 * /changes/<change-id>/revisions/<revision-id>/drafts}.
 */
public class ChangeRevisionsDraftsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(DraftComments.class);

    DynamicMap.mapOf(binder(), DRAFT_COMMENT_KIND);

    /**
     * Create draft comment for change revision {@code PUT
     * /changes/<change-id>/revisions/<revision-id>/drafts}.
     */
    put(REVISION_KIND, "drafts").to(CreateDraftComment.class);
    /**
     * List draft comments for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/drafts}.
     */
    child(REVISION_KIND, "drafts").to(DraftComments.class);

    /**
     * Delete draft comment for change revision {@code DELETE
     * /changes/<change-id>/revisions/<revision-id>/drafts/<draft-id>}.
     */
    delete(DRAFT_COMMENT_KIND).to(DeleteDraftComment.class);
    /**
     * Get draft comment for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/drafts/<draft-id>}.
     */
    get(DRAFT_COMMENT_KIND).to(GetDraftComment.class);
    /**
     * Update draft comment for change revision {@code PUT
     * /changes/<change-id>/revisions/<revision-id>/drafts/<draft-id>}.
     */
    put(DRAFT_COMMENT_KIND).to(PutDraftComment.class);
  }
}
