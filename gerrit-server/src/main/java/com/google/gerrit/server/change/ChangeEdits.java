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

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
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
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.edit.UnchangedCommitMessageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

@Singleton
public class ChangeEdits
    implements ChildCollection<ChangeResource, ChangeEditResource>,
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
  ChangeEdits(
      DynamicMap<RestView<ChangeEditResource>> views,
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
      throws ResourceNotFoundException, AuthException, IOException, InvalidChangeOperationException,
          OrmException {
    Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
    if (!edit.isPresent()) {
      throw new ResourceNotFoundException(id);
    }
    return new ChangeEditResource(rsrc, edit.get(), id.get());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Create create(ChangeResource parent, IdString id) throws RestApiException {
    return createFactory.create(parent.getChange(), id.get());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Post post(ChangeResource parent) throws RestApiException {
    return post;
  }

  /**
   * Create handler that is activated when collection element is accessed but doesn't exist, e. g.
   * PUT request with a path was called but change edit wasn't created yet. Change edit is created
   * and PUT handler is called.
   */
  @SuppressWarnings("unchecked")
  @Override
  public DeleteFile delete(ChangeResource parent, IdString id) throws RestApiException {
    // It's safe to assume that id can never be null, because
    // otherwise we would end up in dedicated endpoint for
    // deleting of change edits and not a file in change edit
    return deleteFileFactory.create(id.get());
  }

  public static class Create implements RestModifyView<ChangeResource, Put.Input> {

    interface Factory {
      Create create(Change change, String path);
    }

    private final Provider<ReviewDb> db;
    private final ChangeEditUtil editUtil;
    private final ChangeEditModifier editModifier;
    private final PatchSetUtil psUtil;
    private final Put putEdit;
    private final Change change;
    private final String path;

    @Inject
    Create(
        Provider<ReviewDb> db,
        ChangeEditUtil editUtil,
        ChangeEditModifier editModifier,
        PatchSetUtil psUtil,
        Put putEdit,
        @Assisted Change change,
        @Assisted @Nullable String path) {
      this.db = db;
      this.editUtil = editUtil;
      this.editModifier = editModifier;
      this.psUtil = psUtil;
      this.putEdit = putEdit;
      this.change = change;
      this.path = path;
    }

    @Override
    public Response<?> apply(ChangeResource resource, Put.Input input)
        throws AuthException, IOException, ResourceConflictException, OrmException,
            InvalidChangeOperationException {
      Optional<ChangeEdit> edit = editUtil.byChange(change);
      if (edit.isPresent()) {
        throw new ResourceConflictException(
            String.format("edit already exists for the change %s", resource.getId()));
      }
      edit = createEdit(resource);
      if (!Strings.isNullOrEmpty(path)) {
        putEdit.apply(new ChangeEditResource(resource, edit.get(), path), input);
      }
      return Response.none();
    }

    private Optional<ChangeEdit> createEdit(ChangeResource resource)
        throws AuthException, IOException, ResourceConflictException, OrmException {
      editModifier.createEdit(change, psUtil.current(db.get(), resource.getNotes()));
      return editUtil.byChange(change);
    }
  }

  public static class DeleteFile implements RestModifyView<ChangeResource, DeleteFile.Input> {
    public static class Input {}

    interface Factory {
      DeleteFile create(String path);
    }

    private final ChangeEditUtil editUtil;
    private final ChangeEditModifier editModifier;
    private final PatchSetUtil psUtil;
    private final Provider<ReviewDb> db;
    private final String path;

    @Inject
    DeleteFile(
        ChangeEditUtil editUtil,
        ChangeEditModifier editModifier,
        PatchSetUtil psUtil,
        Provider<ReviewDb> db,
        @Assisted String path) {
      this.editUtil = editUtil;
      this.editModifier = editModifier;
      this.psUtil = psUtil;
      this.db = db;
      this.path = path;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, DeleteFile.Input in)
        throws IOException, AuthException, ResourceConflictException, OrmException,
            InvalidChangeOperationException, BadRequestException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (edit.isPresent()) {
        // Edit is wiped out
        editUtil.delete(edit.get());
      } else {
        // Edit is created on top of current patch set by deleting path.
        // Even if the latest patch set changed since the user triggered
        // the operation, deleting the whole file is probably still what
        // they intended.
        editModifier.createEdit(rsrc.getChange(), psUtil.current(db.get(), rsrc.getNotes()));
        edit = editUtil.byChange(rsrc.getChange());
        editModifier.deleteFile(edit.get(), path);
      }
      return Response.none();
    }
  }

  // TODO(davido): Turn the boolean options to ChangeEditOption enum,
  // like it's already the case for ListChangesOption/ListGroupsOption
  public static class Detail implements RestReadView<ChangeResource> {
    private final ChangeEditUtil editUtil;
    private final ChangeEditJson editJson;
    private final FileInfoJson fileInfoJson;
    private final Revisions revisions;

    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Option(name = "--list")
    boolean list;

    @Option(name = "--download-commands")
    boolean downloadCommands;

    @Inject
    Detail(
        ChangeEditUtil editUtil,
        ChangeEditJson editJson,
        FileInfoJson fileInfoJson,
        Revisions revisions) {
      this.editJson = editJson;
      this.editUtil = editUtil;
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
    }

    @Override
    public Response<EditInfo> apply(ChangeResource rsrc)
        throws AuthException, IOException, ResourceNotFoundException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (!edit.isPresent()) {
        return Response.none();
      }

      EditInfo editInfo = editJson.toEditInfo(edit.get(), downloadCommands);
      if (list) {
        PatchSet basePatchSet = null;
        if (base != null) {
          RevisionResource baseResource = revisions.parse(rsrc, IdString.fromDecoded(base));
          basePatchSet = baseResource.getPatchSet();
        }
        try {
          editInfo.files =
              fileInfoJson.toFileInfoMap(rsrc.getChange(), edit.get().getRevision(), basePatchSet);
        } catch (PatchListNotAvailableException e) {
          throw new ResourceNotFoundException(e.getMessage());
        }
      }
      return Response.ok(editInfo);
    }
  }

  /**
   * Post to edit collection resource. Two different operations are supported:
   *
   * <ul>
   *   <li>Create non existing change edit
   *   <li>Restore path in existing change edit
   * </ul>
   *
   * The combination of two operations in one request is supported.
   */
  @Singleton
  public static class Post implements RestModifyView<ChangeResource, Post.Input> {
    public static class Input {
      public String restorePath;
      public String oldPath;
      public String newPath;
    }

    private final Provider<ReviewDb> db;
    private final ChangeEditUtil editUtil;
    private final ChangeEditModifier editModifier;
    private final PatchSetUtil psUtil;

    @Inject
    Post(
        Provider<ReviewDb> db,
        ChangeEditUtil editUtil,
        ChangeEditModifier editModifier,
        PatchSetUtil psUtil) {
      this.db = db;
      this.editUtil = editUtil;
      this.editModifier = editModifier;
      this.psUtil = psUtil;
    }

    @Override
    public Response<?> apply(ChangeResource resource, Post.Input input)
        throws AuthException, InvalidChangeOperationException, IOException,
            ResourceConflictException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(resource.getChange());
      if (!edit.isPresent()) {
        edit = createEdit(resource);
      }

      if (input != null) {
        if (!Strings.isNullOrEmpty(input.restorePath)) {
          editModifier.restoreFile(edit.get(), input.restorePath);
        } else if (!Strings.isNullOrEmpty(input.oldPath) && !Strings.isNullOrEmpty(input.newPath)) {
          editModifier.renameFile(edit.get(), input.oldPath, input.newPath);
        }
      }
      return Response.none();
    }

    private Optional<ChangeEdit> createEdit(ChangeResource resource)
        throws AuthException, IOException, ResourceConflictException, OrmException {
      editModifier.createEdit(resource.getChange(), psUtil.current(db.get(), resource.getNotes()));
      return editUtil.byChange(resource.getChange());
    }
  }

  /** Put handler that is activated when PUT request is called on collection element. */
  @Singleton
  public static class Put implements RestModifyView<ChangeEditResource, Put.Input> {
    public static class Input {
      @DefaultInput public RawInput content;
    }

    private final ChangeEditModifier editModifier;

    @Inject
    Put(ChangeEditModifier editModifier) {
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, Input input)
        throws AuthException, ResourceConflictException {
      String path = rsrc.getPath();
      if (Strings.isNullOrEmpty(path) || path.charAt(0) == '/') {
        throw new ResourceConflictException("Invalid path: " + path);
      }

      try {
        editModifier.modifyFile(rsrc.getChangeEdit(), rsrc.getPath(), input.content);
      } catch (InvalidChangeOperationException | IOException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }

  /**
   * Handler to delete a file.
   *
   * <p>This deletes the file from the repository completely. This is not the same as reverting or
   * restoring a file to its previous contents.
   */
  @Singleton
  public static class DeleteContent
      implements RestModifyView<ChangeEditResource, DeleteContent.Input> {
    public static class Input {}

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
      } catch (InvalidChangeOperationException | IOException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }

  public static class Get implements RestReadView<ChangeEditResource> {
    private final FileContentUtil fileContentUtil;

    @Option(
      name = "--base",
      aliases = {"-b"},
      usage = "whether to load the content on the base revision instead of the" + " change edit"
    )
    private boolean base;

    @Inject
    Get(FileContentUtil fileContentUtil) {
      this.fileContentUtil = fileContentUtil;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc) throws IOException {
      try {
        ChangeEdit edit = rsrc.getChangeEdit();
        return Response.ok(
            fileContentUtil.getContent(
                rsrc.getControl().getProjectControl().getProjectState(),
                base
                    ? ObjectId.fromString(edit.getBasePatchSet().getRevision().get())
                    : ObjectId.fromString(edit.getRevision().get()),
                rsrc.getPath()));
      } catch (ResourceNotFoundException rnfe) {
        return Response.none();
      }
    }
  }

  @Singleton
  public static class GetMeta implements RestReadView<ChangeEditResource> {
    private final WebLinks webLinks;

    @Inject
    GetMeta(WebLinks webLinks) {
      this.webLinks = webLinks;
    }

    @Override
    public FileInfo apply(ChangeEditResource rsrc) {
      FileInfo r = new FileInfo();
      ChangeEdit edit = rsrc.getChangeEdit();
      Change change = edit.getChange();
      FluentIterable<DiffWebLinkInfo> links =
          webLinks.getDiffLinks(
              change.getProject().get(),
              change.getChangeId(),
              edit.getBasePatchSet().getPatchSetId(),
              edit.getBasePatchSet().getRefName(),
              rsrc.getPath(),
              0,
              edit.getRefName(),
              rsrc.getPath());
      r.webLinks = links.isEmpty() ? null : links.toList();
      return r;
    }

    public static class FileInfo {
      public List<DiffWebLinkInfo> webLinks;
    }
  }

  @Singleton
  public static class EditMessage implements RestModifyView<ChangeResource, EditMessage.Input> {
    public static class Input {
      @DefaultInput public String message;
    }

    private final Provider<ReviewDb> db;
    private final ChangeEditModifier editModifier;
    private final ChangeEditUtil editUtil;
    private final PatchSetUtil psUtil;

    @Inject
    EditMessage(
        Provider<ReviewDb> db,
        ChangeEditModifier editModifier,
        ChangeEditUtil editUtil,
        PatchSetUtil psUtil) {
      this.db = db;
      this.editModifier = editModifier;
      this.editUtil = editUtil;
      this.psUtil = psUtil;
    }

    @Override
    public Object apply(ChangeResource rsrc, Input input)
        throws AuthException, IOException, InvalidChangeOperationException, BadRequestException,
            ResourceConflictException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (!edit.isPresent()) {
        editModifier.createEdit(rsrc.getChange(), psUtil.current(db.get(), rsrc.getNotes()));
        edit = editUtil.byChange(rsrc.getChange());
      }

      if (input == null || Strings.isNullOrEmpty(input.message)) {
        throw new BadRequestException("commit message must be provided");
      }

      try {
        editModifier.modifyMessage(edit.get(), input.message);
      } catch (UnchangedCommitMessageException ucm) {
        throw new ResourceConflictException(ucm.getMessage());
      }

      return Response.none();
    }
  }

  public static class GetMessage implements RestReadView<ChangeResource> {
    private final GitRepositoryManager repoManager;
    private final ChangeEditUtil editUtil;

    @Option(
      name = "--base",
      aliases = {"-b"},
      usage = "whether to load the message on the base revision instead" + " of the change edit"
    )
    private boolean base;

    @Inject
    GetMessage(GitRepositoryManager repoManager, ChangeEditUtil editUtil) {
      this.repoManager = repoManager;
      this.editUtil = editUtil;
    }

    @Override
    public BinaryResult apply(ChangeResource rsrc)
        throws AuthException, IOException, ResourceNotFoundException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      String msg;
      if (edit.isPresent()) {
        if (base) {
          try (Repository repo = repoManager.openRepository(rsrc.getProject());
              RevWalk rw = new RevWalk(repo)) {
            RevCommit commit =
                rw.parseCommit(
                    ObjectId.fromString(edit.get().getBasePatchSet().getRevision().get()));
            msg = commit.getFullMessage();
          }
        } else {
          msg = edit.get().getEditCommit().getFullMessage();
        }

        return BinaryResult.create(msg)
            .setContentType(FileContentUtil.TEXT_X_GERRIT_COMMIT_MESSAGE)
            .base64();
      }
      throw new ResourceNotFoundException();
    }
  }
}
