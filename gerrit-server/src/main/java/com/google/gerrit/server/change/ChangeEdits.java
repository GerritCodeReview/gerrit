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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

@Singleton
public class ChangeEdits implements
    ChildCollection<ChangeResource, ChangeEditResource>,
    AcceptsCreate<ChangeResource>,
    AcceptsPost<ChangeResource> {
  private final DynamicMap<RestView<ChangeEditResource>> views;
  private final Create.Factory createFactory;
  private final Detail detail;
  private final ChangeEditUtil editUtil;

  @Inject
  ChangeEdits(DynamicMap<RestView<ChangeEditResource>> views,
      Create.Factory createFactory,
      Detail detail,
      ChangeEditUtil editUtil) {
    this.views = views;
    this.createFactory = createFactory;
    this.detail = detail;
    this.editUtil = editUtil;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return detail;
  }

  @Override
  public ChangeEditResource parse(ChangeResource rsrc, IdString id)
      throws ResourceNotFoundException, AuthException, IOException,
      InvalidChangeOperationException {
    Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
    if (!edit.isPresent()) {
      throw new ResourceNotFoundException(id);
    }
    return new ChangeEditResource(rsrc, edit.get(), id.get());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Create create(ChangeResource parent, IdString id)
      throws RestApiException {
    return createFactory.create(parent.getChange(), id.get());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Create post(ChangeResource parent)
      throws RestApiException {
    return createFactory.create(parent.getChange(), null);
  }

  static class Create implements
      RestModifyView<ChangeResource, Put.Input> {

    interface Factory {
      Create create(Change change, String path);
    }

    private final Provider<ReviewDb> db;
    private final ChangeEditUtil editUtil;
    private final ChangeEditModifier editModifier;
    private final Put putEdit;
    private final Change change;
    private final String path;

    @Inject
    Create(Provider<ReviewDb> db,
        ChangeEditUtil editUtil,
        ChangeEditModifier editModifier,
        Put putEdit,
        @Assisted Change change,
        @Assisted @Nullable String path) {
      this.db = db;
      this.editUtil = editUtil;
      this.editModifier = editModifier;
      this.putEdit = putEdit;
      this.change = change;
      this.path = path;
    }

    @Override
    public Response<?> apply(ChangeResource resource, Put.Input input)
        throws AuthException, IOException, ResourceConflictException,
        OrmException, InvalidChangeOperationException {
      Optional<ChangeEdit> edit = editUtil.byChange(change);
      if (edit.isPresent()) {
        throw new ResourceConflictException(String.format(
            "edit already exists for the change %s",
            resource.getChange().getChangeId()));
      }
      edit = createEdit();
      if (!Strings.isNullOrEmpty(path)) {
        putEdit.apply(new ChangeEditResource(resource, edit.get(), path),
            input);
      }
      return Response.none();
    }

    private Optional<ChangeEdit> createEdit() throws AuthException,
        IOException, ResourceConflictException, OrmException,
        InvalidChangeOperationException {
      editModifier.createEdit(change,
          db.get().patchSets().get(change.currentPatchSetId()));
      return editUtil.byChange(change);
    }
  }

  @Singleton
  static class Detail implements RestReadView<ChangeResource> {
    private final ChangeEditUtil editUtil;
    private final ChangeEditJson editJson;

    @Inject
    Detail(ChangeEditJson editJson,
        ChangeEditUtil editUtil) {
      this.editJson = editJson;
      this.editUtil = editUtil;
    }

    @Override
    public Response<EditInfo> apply(ChangeResource rsrc) throws AuthException,
        IOException, NoSuchChangeException, InvalidChangeOperationException,
        ResourceNotFoundException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (edit.isPresent()) {
        return Response.ok(editJson.toEditInfo(edit.get()));
      }
      return Response.none();
    }
  }

  @Singleton
  static class Delete implements
      RestModifyView<ChangeEditResource, Delete.Input> {
    public static class Input {
    }

    private final ChangeEditModifier editModifier;

    @Inject
    Delete(ChangeEditModifier editModifier) {
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, Delete.Input input)
        throws AuthException, ResourceNotFoundException,
        ResourceConflictException, OrmException {
      try {
        editModifier.deleteFile(rsrc.getChangeEdit(), rsrc.getPath());
      } catch(InvalidChangeOperationException | IOException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }

  @Singleton
  public static class Put implements
      RestModifyView<ChangeEditResource, Put.Input> {
    public static class Input {
      public RawInput content;
      public boolean restore;
    }

    private final ChangeEditModifier editModifier;

    @Inject
    Put(ChangeEditModifier editModifier) {
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, Input input)
        throws AuthException, ResourceConflictException, IOException {
      String path = rsrc.getPath();
      byte[] content = null;
      if (input.content != null) {
        content = ByteStreams.toByteArray(input.content.getInputStream());
      }
      try {
        if (input.restore) {
          editModifier.restoreFile(rsrc.getChangeEdit(), path);
        } else {
          editModifier.modifyFile(rsrc.getChangeEdit(), path, content);
        }
      } catch(InvalidChangeOperationException | IOException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }
}
