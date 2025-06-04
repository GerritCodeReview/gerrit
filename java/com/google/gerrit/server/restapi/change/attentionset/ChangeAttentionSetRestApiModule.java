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

/** Guice module that binds all REST endpoints for {@code /changes/<change-id>/attention}. */
public class ChangeAttentionSetRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), ATTENTION_SET_ENTRY_KIND);

    /** Get attention set {@code GET /changes/<change-id>/attention}. */
    child(CHANGE_KIND, "attention").to(AttentionSet.class);
    /** Add to attention set {@code POST /changes/<change-id>/attention}. */
    postOnCollection(ATTENTION_SET_ENTRY_KIND).to(AddToAttentionSet.class);

    /** Remove from attention set {@code DELETE /changes/<change-id>/attention/<account-id>}. */
    delete(ATTENTION_SET_ENTRY_KIND).to(RemoveFromAttentionSet.class);

    /**
     * Remove from attention set {@code POST /changes/<change-id>/attention/<account-id>/delete}.
     */
    post(ATTENTION_SET_ENTRY_KIND, "delete").to(RemoveFromAttentionSet.class);
  }
}
