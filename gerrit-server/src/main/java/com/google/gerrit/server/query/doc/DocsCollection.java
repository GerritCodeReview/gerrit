// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class DocsCollection implements
    RestCollection<TopLevelResource, DocResource> {
  private final Provider<QueryDocs> queryFactory;
  private final DynamicMap<RestView<DocResource>> views;

  @Inject
  DocsCollection(
      Provider<QueryDocs> queryFactory,
      DynamicMap<RestView<DocResource>> views) {
    this.queryFactory = queryFactory;
    this.views = views;
  }

  @Override
  public RestView<TopLevelResource> list() {
    return queryFactory.get();
  }

  @Override
  public DynamicMap<RestView<DocResource>> views() {
    return views;
  }

  @Override
  public DocResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException {
    // Always throw ResourceNotFoundException, as we don't support such thing in
    // doc search.
    throw new ResourceNotFoundException(id);
  }
}
