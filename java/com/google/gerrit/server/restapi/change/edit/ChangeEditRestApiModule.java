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

package com.google.gerrit.server.restapi.change.edit;

import static com.google.gerrit.server.change.ChangeEditResource.CHANGE_EDIT_KIND;
import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/** Guice module that binds all REST endpoints for {@code /changes/<change-id>/edit}. */
public class ChangeEditRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CHANGE_EDIT_KIND);

    /** Change change author/committer in edit {@code PUT /changes/<change-id>/edit:identity}. */
    put(CHANGE_KIND, "edit:identity").to(ChangeEdits.EditIdentity.class);
    /** Change commit message in edit {@code PUT /changes/<change-id>/edit:message}. */
    put(CHANGE_KIND, "edit:message").to(ChangeEdits.EditMessage.class);
    /** Get commit message in edit {@code GET /changes/<change-id>/edit:message}. */
    get(CHANGE_KIND, "edit:message").to(ChangeEdits.GetMessage.class);
    /** Publish change edit {@code POST /changes/<change-id>/edit:publish}. */
    post(CHANGE_KIND, "edit:publish").to(PublishChangeEdit.class);
    /** Rebase change edit {@code POST /changes/<change-id>/edit:rebase}. */
    post(CHANGE_KIND, "edit:rebase").to(RebaseChangeEdit.class);

    /** List change edits {@code GET /changes/<change-id>/edit}. */
    child(CHANGE_KIND, "edit").to(ChangeEdits.class);
    /** Restore/Rename file in edit {@code POST /changes/<change-id>/edit}. */
    postOnCollection(CHANGE_EDIT_KIND).to(ChangeEdits.Post.class);
    /** Delete change edit {@code DELETE /changes/<change-id>/edit}. */
    deleteOnCollection(CHANGE_EDIT_KIND).to(DeleteChangeEdit.class);

    /** Change file in edit {@code PUT /changes/<change-id>/edit/<path-to-file>}. */
    create(CHANGE_EDIT_KIND).to(ChangeEdits.Create.class);
    /** Delete file in edit {@code DELETE /changes/<change-id>/edit/<path-to-file>}. */
    delete(CHANGE_EDIT_KIND).to(ChangeEdits.DeleteContent.class);
    deleteMissing(CHANGE_EDIT_KIND).to(ChangeEdits.DeleteFile.class);

    /** Get file in edit {@code GET /changes/<change-id>/edit/<path-to-file>}. */
    get(CHANGE_EDIT_KIND, "/").to(ChangeEdits.Get.class);
    /** Create file in edit {@code PUT /changes/<change-id>/edit/<path-to-file>}. */
    put(CHANGE_EDIT_KIND, "/").to(ChangeEdits.Put.class);

    /** Get file metadata in edit {@code GET /changes/<change-id>/edit/<path-to-file>/meta}. */
    get(CHANGE_EDIT_KIND, "meta").to(ChangeEdits.GetMeta.class);
  }
}
