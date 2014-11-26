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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AcceptsDelete;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.DefaultInput;
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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;

import org.kohsuke.args4j.Option;

import java.io.IOException;

@Singleton
public class ChangeEdits implements
    ChildCollection<ChangeResource, ChangeEditResource>,
    AcceptsCreate<ChangeResource>,
    AcceptsPost<ChangeResource>,
    AcceptsDelete<ChangeResource> {
  private final DynamicMap<RestView<ChangeEditResource>> views;
  private final Create.Factory createFactory;
  private final DeleteFile.Factory deleteFileFactory;
  private final Provider<Detail> detail;
  private final ChangeEditUtil editUtil;
  private final Post post;

  @Inject
  ChangeEdits(DynamicMap<RestView<ChangeEditResource>> views,
      Create.Factory createFactory,
      Provider<Detail> detail,
      ChangeEditUtil editUtil,
      Post post,
      DeleteFile.Factory deleteFileFactory) {
    this.views = views;
    this.createFactory = createFactory;
    this.detail = detail;
    this.editUtil = editUtil;
    this.post = post;
    this.deleteFileFactory = deleteFileFactory;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return detail.get();
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
  public Post post(ChangeResource parent) throws RestApiException {
    return post;
  }

  /**
  * Create handler that is activated when collection element is accessed
  * but doesn't exist, e. g. PUT request with a path was called but
  * change edit wasn't created yet. Change edit is created and PUT
  * handler is called.
  */
  @SuppressWarnings("unchecked")
  @Override
  public DeleteFile delete(ChangeResource parent, IdString id)
      throws RestApiException {
    // It's safe to assume that id can never be null, because
    // otherwise we would end up in dedicated endpoint for
    // deleting of change edits and not a file in change edit
    return deleteFileFactory.create(id.get());
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
        IOException, ResourceConflictException, OrmException {
      editModifier.createEdit(change,
          db.get().patchSets().get(change.currentPatchSetId()));
      return editUtil.byChange(change);
    }
  }

  static class DeleteFile implements
      RestModifyView<ChangeResource, DeleteFile.Input> {
    public static class Input {
    }

    interface Factory {
      DeleteFile create(String path);
    }

    private final ChangeEditUtil editUtil;
    private final ChangeEditModifier editModifier;
    private final Provider<ReviewDb> db;
    private final String path;

    @Inject
    DeleteFile(ChangeEditUtil editUtil,
        ChangeEditModifier editModifier,
        Provider<ReviewDb> db,
        @Assisted String path) {
      this.editUtil = editUtil;
      this.editModifier = editModifier;
      this.db = db;
      this.path = path;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, DeleteFile.Input in)
        throws IOException, AuthException, ResourceConflictException,
        OrmException, InvalidChangeOperationException, BadRequestException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (edit.isPresent()) {
        // Edit is wiped out
        editUtil.delete(edit.get());
      } else {
        // Edit is created on top of current patch set by deleting path.
        // Even if the latest patch set changed since the user triggered
        // the operation, deleting the whole file is probably still what
        // they intended.
        editModifier.createEdit(rsrc.getChange(), db.get().patchSets().get(
            rsrc.getChange().currentPatchSetId()));
        edit = editUtil.byChange(rsrc.getChange());
        editModifier.deleteFile(edit.get(), path);
      }
      return Response.none();
    }
  }

  // TODO(davido): Turn the boolean options to ChangeEditOption enum,
  // like it's already the case for ListChangesOption/ListGroupsOption
  static class Detail implements RestReadView<ChangeResource> {
    private final ChangeEditUtil editUtil;
    private final ChangeEditJson editJson;
    private final FileInfoJson fileInfoJson;
    private final Revisions revisions;

    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Option(name = "--list", metaVar = "LIST")
    boolean list;

    @Option(name = "--download-commands", metaVar = "download-commands")
    boolean downloadCommands;

    @Inject
    Detail(ChangeEditUtil editUtil,
        ChangeEditJson editJson,
        FileInfoJson fileInfoJson,
        Revisions revisions) {
      this.editJson = editJson;
      this.editUtil = editUtil;
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
    }

    @Override
    public Response<EditInfo> apply(ChangeResource rsrc) throws AuthException,
        IOException, InvalidChangeOperationException,
        ResourceNotFoundException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (!edit.isPresent()) {
        return Response.none();
      }

      EditInfo editInfo = editJson.toEditInfo(edit.get(), downloadCommands);
      if (list) {
        PatchSet basePatchSet = null;
        if (base != null) {
          RevisionResource baseResource = revisions.parse(
              rsrc, IdString.fromDecoded(base));
          basePatchSet = baseResource.getPatchSet();
        }
        try {
          editInfo.files =
              fileInfoJson.toFileInfoMap(
                  rsrc.getChange(),
                  edit.get().getRevision(),
                  basePatchSet);
        } catch (PatchListNotAvailableException e) {
          throw new ResourceNotFoundException(e.getMessage());
        }
      }
      return Response.ok(editInfo);
    }
  }

  /**
   * Post to edit collection resource. Two different operations are
   * supported:
   * <ul>
   * <li>Create non existing change edit</li>
   * <li>Restore path in existing change edit</li>
   * </ul>
   * The combination of two operations in one request is supported.
   */
  @Singleton
  public static class Post implements
      RestModifyView<ChangeResource, Post.Input> {
    public static class Input {
      public String restorePath;
    }

    private final Provider<ReviewDb> db;
    private final ChangeEditUtil editUtil;
    private final ChangeEditModifier editModifier;

    @Inject
    Post(Provider<ReviewDb> db,
        ChangeEditUtil editUtil,
        ChangeEditModifier editModifier) {
      this.db = db;
      this.editUtil = editUtil;
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeResource resource, Post.Input input)
        throws AuthException, InvalidChangeOperationException, IOException,
        ResourceConflictException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(resource.getChange());
      if (!edit.isPresent()) {
        edit = createEdit(resource.getChange());
      }

      if (input != null && !Strings.isNullOrEmpty(input.restorePath)) {
        editModifier.restoreFile(edit.get(), input.restorePath);
      }
      return Response.none();
    }

    private Optional<ChangeEdit> createEdit(Change change)
        throws AuthException, IOException, ResourceConflictException,
        OrmException {
      editModifier.createEdit(change,
          db.get().patchSets().get(change.currentPatchSetId()));
      return editUtil.byChange(change);
    }
  }

  /**
  * Put handler that is activated when PUT request is called on
  * collection element.
  */
  @Singleton
  public static class Put implements
      RestModifyView<ChangeEditResource, Put.Input> {
    public static class Input {
      @DefaultInput
      public RawInput content;
    }

    private final ChangeEditModifier editModifier;

    @Inject
    Put(ChangeEditModifier editModifier) {
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, Input input)
        throws AuthException, ResourceConflictException, IOException {
      try {
        editModifier.modifyFile(
            rsrc.getChangeEdit(),
            rsrc.getPath(),
            input.content);
      } catch(InvalidChangeOperationException | IOException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }

  /**
   * Handler to delete a file.
   * <p>
   * This deletes the file from the repository completely. This is not the same
   * as reverting or restoring a file to its previous contents.
   */
  @Singleton
  static class DeleteContent implements
      RestModifyView<ChangeEditResource, DeleteContent.Input> {
    public static class Input {
    }

    private final ChangeEditModifier editModifier;

    @Inject
    DeleteContent(ChangeEditModifier editModifier) {
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, DeleteContent.Input input)
        throws AuthException, ResourceConflictException {
      try {
        editModifier.deleteFile(rsrc.getChangeEdit(), rsrc.getPath());
      } catch(InvalidChangeOperationException | IOException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }

  @Singleton
  static class Get implements RestReadView<ChangeEditResource> {
    private final FileContentUtil fileContentUtil;

    @Inject
    Get(FileContentUtil fileContentUtil) {
      this.fileContentUtil = fileContentUtil;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc)
        throws ResourceNotFoundException, IOException {
      try {
        return Response.ok(fileContentUtil.getContent(
              rsrc.getChangeEdit().getChange().getProject(),
              rsrc.getChangeEdit().getRevision().get(),
              rsrc.getPath()));
      } catch (ResourceNotFoundException rnfe) {
        return Response.none();
      }
    }
  }

  @Singleton
  public static class EditMessage implements
      RestModifyView<ChangeResource, EditMessage.Input> {
    public static class Input {
      @DefaultInput
      public String message;
    }

    private final Provider<ReviewDb> db;
    private final ChangeEditModifier editModifier;
    private final ChangeEditUtil editUtil;

    @Inject
    EditMessage(Provider<ReviewDb> db,
        ChangeEditModifier editModifier,
        ChangeEditUtil editUtil) {
      this.db = db;
      this.editModifier = editModifier;
      this.editUtil = editUtil;
    }

    @Override
    public Object apply(ChangeResource rsrc, Input input) throws AuthException,
        IOException, InvalidChangeOperationException, BadRequestException,
        ResourceConflictException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (!edit.isPresent()) {
        editModifier.createEdit(rsrc.getChange(),
            db.get().patchSets().get(rsrc.getChange().currentPatchSetId()));
        edit = editUtil.byChange(rsrc.getChange());
      }

      if (input == null || Strings.isNullOrEmpty(input.message)) {
        throw new BadRequestException("commit message must be provided");
      }

      editModifier.modifyMessage(edit.get(), input.message);
      return Response.none();
    }
  }

  @Singleton
  public static class GetMessage implements RestReadView<ChangeResource> {
    private final ChangeUtil changeUtil;
    private final ChangeEditUtil editUtil;

    @Inject
    GetMessage(ChangeUtil changeUtil,
        ChangeEditUtil editUtil) {
      this.changeUtil = changeUtil;
      this.editUtil = editUtil;
    }

    @Override
    public BinaryResult apply(ChangeResource rsrc) throws AuthException, IOException,
       OrmException, NoSuchChangeException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      // TODO(davido): Clean this up by returning 404 when edit doesn't exist.
      // Client should call GET /changes/{id}/revisions/current/commit in this
      // case; or, to be consistent with GET content logic, the client could
      // call directly the right endpoint.
      String m = edit.isPresent()
        ? edit.get().getEditCommit().getFullMessage()
        : changeUtil.getMessage(rsrc.getChange());
      return BinaryResult.create(m).base64();
    }
  }
}
