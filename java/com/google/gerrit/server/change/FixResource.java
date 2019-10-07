// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;
import java.util.List;

public class FixResource implements RestResource {
  public static final TypeLiteral<RestView<FixResource>> FIX_KIND =
      new TypeLiteral<RestView<FixResource>>() {};

  private final List<FixReplacement> fixReplacements;
  private final RevisionResource revisionResource;

  public FixResource(RevisionResource revisionResource, List<FixReplacement> fixReplacements) {
    this.fixReplacements = fixReplacements;
    this.revisionResource = revisionResource;
  }

  public List<FixReplacement> getFixReplacements() {
    return fixReplacements;
  }

  public RevisionResource getRevisionResource() {
    return revisionResource;
  }
}
