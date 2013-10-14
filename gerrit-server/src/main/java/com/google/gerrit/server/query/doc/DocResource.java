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

package com.google.gerrit.server.query.doc;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.query.doc.QueryDocs.DocResult;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;

import java.nio.charset.Charset;

public class DocResource implements RestResource, HasETag {
  public static final TypeLiteral<RestView<DocResource>> DOC_KIND =
      new TypeLiteral<RestView<DocResource>>() {};

  private final DocResult doc;

  @Inject
  public DocResource(DocResult doc) {
    this.doc = doc;
  }

  protected DocResource(DocResource copy) {
    this.doc = copy.doc;
  }

  public DocResult getDoc() {
    return doc;
  }

  @Override
  public String getETag() {
    Hasher h = Hashing.md5().newHasher().putString(
        doc.content, Charset.forName("UTF-8"));
    return h.hash().toString();
  }
}
