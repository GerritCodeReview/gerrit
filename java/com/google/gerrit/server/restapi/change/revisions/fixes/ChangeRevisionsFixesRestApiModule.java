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

package com.google.gerrit.server.restapi.change.revisions.fixes;

import static com.google.gerrit.server.change.FixResource.FIX_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/**
 * Guice module that binds all REST endpoints for {@code
 * /changes/<change-id>/revisions/<revision-id>/fixes}.
 */
public class ChangeRevisionsFixesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Fixes.class);

    DynamicMap.mapOf(binder(), FIX_KIND);

    /**
     * Apply provided fix for change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/fix:apply}.
     */
    post(REVISION_KIND, "fix:apply").to(ApplyProvidedFix.class);
    /**
     * Preview provided fix for change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/fix:preview}.
     */
    post(REVISION_KIND, "fix:preview").to(PreviewFix.Provided.class);

    /**
     * List stored fixes for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/fixes}.
     */
    child(REVISION_KIND, "fixes").to(Fixes.class);

    /**
     * Apply stored fix for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/fixes/<fix-id>/apply}.
     */
    post(FIX_KIND, "apply").to(ApplyStoredFix.class);

    /**
     * Preview stored fix for change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/fixes/<fix-id>/preview}.
     */
    get(FIX_KIND, "preview").to(PreviewFix.Stored.class);
  }
}
