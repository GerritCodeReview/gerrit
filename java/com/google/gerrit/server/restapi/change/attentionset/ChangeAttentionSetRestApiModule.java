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

package com.google.gerrit.server.restapi.change.attentionset;

import static com.google.gerrit.server.change.AttentionSetEntryResource.ATTENTION_SET_ENTRY_KIND;
import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class ChangeAttentionSetRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), ATTENTION_SET_ENTRY_KIND);

    child(CHANGE_KIND, "attention").to(AttentionSet.class);
    postOnCollection(ATTENTION_SET_ENTRY_KIND).to(AddToAttentionSet.class);
    delete(ATTENTION_SET_ENTRY_KIND).to(RemoveFromAttentionSet.class);
    post(ATTENTION_SET_ENTRY_KIND, "delete").to(RemoveFromAttentionSet.class);
  }
}
