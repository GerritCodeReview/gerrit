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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.kohsuke.args4j.Option;

import java.util.Map;

@Singleton
public class EditFiles implements ChildCollection<ChangeEditResource, EditFileResource> {
  private final DynamicMap<RestView<EditFileResource>> views;
  private final Provider<ListFiles> list;

  @Inject
  EditFiles(DynamicMap<RestView<EditFileResource>> views,
      Provider<ListFiles> list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<EditFileResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeEditResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public EditFileResource parse(ChangeEditResource rsrc, IdString id)
      throws ResourceNotFoundException, OrmException, AuthException {
    return new EditFileResource(rsrc, id.get());
  }

  public static final class ListFiles implements RestReadView<ChangeEditResource> {
    @Option(name = "--base", metaVar = "revision-id")
    String base;

    private final FileInfoJson fileInfoJson;
    private final Revisions revisions;

    @Inject
    ListFiles(FileInfoJson fileInfoJson,
        Revisions revisions) {
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
    }

    @Override
    public Response<?> apply(ChangeEditResource resource) throws AuthException,
        BadRequestException, ResourceNotFoundException, OrmException {
      PatchSet basePatchSet = null;
      if (base != null) {
        RevisionResource baseResource = revisions.parse(
            resource.getChangeResource(), IdString.fromDecoded(base));
        basePatchSet = baseResource.getPatchSet();
      }
      try {
        Response<Map<String, FileInfo>> r = Response.ok(fileInfoJson.toFileInfoMap(
            resource.getChange(),
            resource.getChangeEdit().getRevision(),
            basePatchSet));
        return r;
      } catch (PatchListNotAvailableException e) {
        throw new ResourceNotFoundException(e.getMessage());
      }
    }
  }
}
