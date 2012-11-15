// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.change.ReviewerResource.REVIEWER_KIND;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.config.FactoryModule;

public class Module extends FactoryModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CHANGE_KIND);
    DynamicMap.mapOf(binder(), REVIEWER_KIND);

    bind(CHANGE_KIND)
      .annotatedWith(Exports.named("GET./"))
      .to(GetChange.class);

    bind(CHANGE_KIND)
      .annotatedWith(Exports.named("POST.abandon"))
      .to(Abandon.class);

    bind(CHANGE_KIND)
      .annotatedWith(Exports.named("GET.reviewers"))
      .to(Reviewers.class);

    bind(REVIEWER_KIND)
      .annotatedWith(Exports.named("GET./"))
      .to(GetReviewer.class);
  }
}
