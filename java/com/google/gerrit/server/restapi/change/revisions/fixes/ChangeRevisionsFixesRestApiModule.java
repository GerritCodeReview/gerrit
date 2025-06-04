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

public class ChangeRevisionsFixesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Fixes.class);

    DynamicMap.mapOf(binder(), FIX_KIND);

    child(REVISION_KIND, "fixes").to(Fixes.class);
    post(FIX_KIND, "apply").to(ApplyStoredFix.class);
    get(FIX_KIND, "preview").to(PreviewFix.Stored.class);

    post(REVISION_KIND, "fix:apply").to(ApplyProvidedFix.class);
    post(REVISION_KIND, "fix:preview").to(PreviewFix.Provided.class);
  }
}
