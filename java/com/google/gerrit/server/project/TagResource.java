// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.TypeLiteral;

public class TagResource extends RefResource {
  public static final TypeLiteral<RestView<TagResource>> TAG_KIND =
      new TypeLiteral<RestView<TagResource>>() {};

  private final TagInfo tagInfo;

  public TagResource(ProjectAccessor projectAccessor, CurrentUser user, TagInfo tagInfo) {
    super(projectAccessor, user);
    this.tagInfo = tagInfo;
  }

  public TagInfo getTagInfo() {
    return tagInfo;
  }

  @Override
  public String getRef() {
    return tagInfo.ref;
  }

  @Override
  public String getRevision() {
    return tagInfo.revision;
  }
}
