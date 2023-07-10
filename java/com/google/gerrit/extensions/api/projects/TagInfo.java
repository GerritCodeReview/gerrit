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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.WebLinkInfo;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class TagInfo extends RefInfo {
  public String object;
  public String message;
  public GitPerson tagger;

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp created;

  public List<WebLinkInfo> webLinks;

  public TagInfo(
      String ref,
      String revision,
      Boolean canDelete,
      List<WebLinkInfo> webLinks,
      Timestamp created) {
    this.ref = ref;
    this.revision = revision;
    this.canDelete = canDelete;
    this.webLinks = webLinks;
    this.created = created;
  }

  public TagInfo() {}

  @SuppressWarnings("JdkObsolete")
  public TagInfo(
      String ref,
      String revision,
      Boolean canDelete,
      List<WebLinkInfo> webLinks,
      @Nullable Instant created) {
    this.ref = ref;
    this.revision = revision;
    this.canDelete = canDelete;
    this.webLinks = webLinks;
    this.created = created != null ? Timestamp.from(created) : null;
  }

  public TagInfo(String ref, String revision, Boolean canDelete, List<WebLinkInfo> webLinks) {
    this(ref, revision, canDelete, webLinks, (Instant) null);
  }

  public TagInfo(
      String ref,
      String revision,
      String object,
      String message,
      GitPerson tagger,
      Boolean canDelete,
      List<WebLinkInfo> webLinks,
      Timestamp created) {
    this(ref, revision, canDelete, webLinks, created);
    this.object = object;
    this.message = message;
    this.tagger = tagger;
    this.webLinks = webLinks;
  }

  public TagInfo(
      String ref,
      String revision,
      String object,
      String message,
      GitPerson tagger,
      Boolean canDelete,
      List<WebLinkInfo> webLinks,
      Instant created) {
    this(ref, revision, canDelete, webLinks, created);
    this.object = object;
    this.message = message;
    this.tagger = tagger;
    this.webLinks = webLinks;
  }

  public TagInfo(
      String ref,
      String revision,
      String object,
      String message,
      GitPerson tagger,
      Boolean canDelete,
      List<WebLinkInfo> webLinks) {
    this(ref, revision, object, message, tagger, canDelete, webLinks, (Instant) null);
    this.object = object;
    this.message = message;
    this.tagger = tagger;
    this.webLinks = webLinks;
  }
}
