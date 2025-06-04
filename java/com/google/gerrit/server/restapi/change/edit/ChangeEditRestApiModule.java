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

public class ChangeEditRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CHANGE_EDIT_KIND);

    put(CHANGE_KIND, "edit:identity").to(ChangeEdits.EditIdentity.class);
    put(CHANGE_KIND, "edit:message").to(ChangeEdits.EditMessage.class);
    get(CHANGE_KIND, "edit:message").to(ChangeEdits.GetMessage.class);
    post(CHANGE_KIND, "edit:publish").to(PublishChangeEdit.class);
    post(CHANGE_KIND, "edit:rebase").to(RebaseChangeEdit.class);

    child(CHANGE_KIND, "edit").to(ChangeEdits.class);
    create(CHANGE_EDIT_KIND).to(ChangeEdits.Create.class);
    delete(CHANGE_EDIT_KIND).to(ChangeEdits.DeleteContent.class);
    deleteOnCollection(CHANGE_EDIT_KIND).to(DeleteChangeEdit.class);
    deleteMissing(CHANGE_EDIT_KIND).to(ChangeEdits.DeleteFile.class);
    postOnCollection(CHANGE_EDIT_KIND).to(ChangeEdits.Post.class);
    get(CHANGE_EDIT_KIND, "/").to(ChangeEdits.Get.class);
    put(CHANGE_EDIT_KIND, "/").to(ChangeEdits.Put.class);
    get(CHANGE_EDIT_KIND, "meta").to(ChangeEdits.GetMeta.class);
  }
}
