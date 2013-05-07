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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

class Files implements ChildCollection<RevisionResource, FileResource> {
  private final DynamicMap<RestView<FileResource>> views;
  private final FileInfoJson fileInfoJson;
  private final Provider<Revisions> revisions;

  @Inject
  Files(DynamicMap<RestView<FileResource>> views,
      FileInfoJson fileInfoJson,
      Provider<Revisions> revisions) {
    this.views = views;
    this.fileInfoJson = fileInfoJson;
    this.revisions = revisions;
  }

  @Override
  public DynamicMap<RestView<FileResource>> views() {
    return views;
  }

  @Override
  public RestView<RevisionResource> list() throws AuthException {
    return new List();
  }

  @Override
  public FileResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException, OrmException, AuthException {
    return new FileResource(rev, id.get());
  }

  private final class List implements RestReadView<RevisionResource> {
    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Override
    public Object apply(RevisionResource resource)
        throws ResourceNotFoundException, OrmException,
        PatchListNotAvailableException {
      PatchSet basePatchSet = null;
      if (base != null) {
        RevisionResource baseResource = revisions.get().parse(
            resource.getChangeResource(), IdString.fromDecoded(base));
        basePatchSet = baseResource.getPatchSet();
      }
      return fileInfoJson.toFileInfoMap(
          resource.getChange(), resource.getPatchSet(), basePatchSet);
    }
  }
}
