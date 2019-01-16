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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.registration.DynamicMap;
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
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.RestCollectionDeleteMissingView;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.change.ChangeEditResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.change.FileInfoJson;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.edit.UnchangedCommitMessageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

@Singleton
public class ChangeEdits implements ChildCollection<ChangeResource, ChangeEditResource> {
  private final DynamicMap<RestView<ChangeEditResource>> views;
  private final Provider<Detail> detail;
  private final ChangeEditUtil editUtil;

  @Inject
  ChangeEdits(
      DynamicMap<RestView<ChangeEditResource>> views,
      Provider<Detail> detail,
      ChangeEditUtil editUtil) {
    this.views = views;
    this.detail = detail;
    this.editUtil = editUtil;
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
      throws ResourceNotFoundException, AuthException, IOException, StorageException {
    Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getNotes(), rsrc.getUser());
    if (!edit.isPresent()) {
      throw new ResourceNotFoundException(id);
    }
    return new ChangeEditResource(rsrc, edit.get(), id.get());
  }

  /**
   * Create handler that is activated when collection element is accessed but doesn't exist, e. g.
   * PUT request with a path was called but change edit wasn't created yet. Change edit is created
   * and PUT handler is called.
   */
  public static class Create
      implements RestCollectionCreateView<ChangeResource, ChangeEditResource, Put.Input> {
    private final Put putEdit;

    @Inject
    Create(Put putEdit) {
      this.putEdit = putEdit;
    }

    @Override
    public Response<?> apply(ChangeResource resource, IdString id, Put.Input input)
        throws AuthException, ResourceConflictException, IOException, StorageException,
            PermissionBackendException {
      putEdit.apply(resource, id.get(), input.content);
      return Response.none();
    }
  }

  public static class DeleteFile
      implements RestCollectionDeleteMissingView<ChangeResource, ChangeEditResource, Input> {
    private final DeleteContent deleteContent;

    @Inject
    DeleteFile(DeleteContent deleteContent) {
      this.deleteContent = deleteContent;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, IdString id, Input in)
        throws IOException, AuthException, ResourceConflictException, StorageException,
            PermissionBackendException {
      return deleteContent.apply(rsrc, id.get());
    }
  }

  // TODO(davido): Turn the boolean options to ChangeEditOption enum,
  // like it's already the case for ListChangesOption/ListGroupsOption
  public static class Detail implements RestReadView<ChangeResource> {
    private final ChangeEditUtil editUtil;
    private final ChangeEditJson editJson;
    private final FileInfoJson fileInfoJson;
    private final Revisions revisions;

    private String base;
    private boolean list;
    private boolean downloadCommands;

    @Option(name = "--base", metaVar = "revision-id")
    public void setBase(String base) {
      this.base = base;
    }

    @Option(name = "--list")
    public void setList(boolean list) {
      this.list = list;
    }

    @Option(name = "--download-commands")
    public void setDownloadCommands(boolean downloadCommands) {
      this.downloadCommands = downloadCommands;
    }

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
        throws AuthException, IOException, ResourceNotFoundException, StorageException,
            PermissionBackendException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getNotes(), rsrc.getUser());
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
              fileInfoJson.toFileInfoMap(
                  rsrc.getChange(), edit.get().getEditCommit(), basePatchSet);
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
  public static class Post
      implements RestCollectionModifyView<ChangeResource, ChangeEditResource, Post.Input> {
    public static class Input {
      public String restorePath;
      public String oldPath;
      public String newPath;
    }

    private final ChangeEditModifier editModifier;
    private final GitRepositoryManager repositoryManager;

    @Inject
    Post(ChangeEditModifier editModifier, GitRepositoryManager repositoryManager) {
      this.editModifier = editModifier;
      this.repositoryManager = repositoryManager;
    }

    @Override
    public Response<?> apply(ChangeResource resource, Post.Input input)
        throws AuthException, IOException, ResourceConflictException, StorageException,
            PermissionBackendException {
      Project.NameKey project = resource.getProject();
      try (Repository repository = repositoryManager.openRepository(project)) {
        if (isRestoreFile(input)) {
          editModifier.restoreFile(repository, resource.getNotes(), input.restorePath);
        } else if (isRenameFile(input)) {
          editModifier.renameFile(repository, resource.getNotes(), input.oldPath, input.newPath);
        } else {
          editModifier.createEdit(repository, resource.getNotes());
        }
      } catch (InvalidChangeOperationException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }

    private static boolean isRestoreFile(Input input) {
      return input != null && !Strings.isNullOrEmpty(input.restorePath);
    }

    private static boolean isRenameFile(Input input) {
      return input != null
          && !Strings.isNullOrEmpty(input.oldPath)
          && !Strings.isNullOrEmpty(input.newPath);
    }
  }

  /** Put handler that is activated when PUT request is called on collection element. */
  @Singleton
  public static class Put implements RestModifyView<ChangeEditResource, Put.Input> {
    public static class Input {
      @DefaultInput public RawInput content;
    }

    private final ChangeEditModifier editModifier;
    private final GitRepositoryManager repositoryManager;

    @Inject
    Put(ChangeEditModifier editModifier, GitRepositoryManager repositoryManager) {
      this.editModifier = editModifier;
      this.repositoryManager = repositoryManager;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, Input input)
        throws AuthException, ResourceConflictException, IOException, StorageException,
            PermissionBackendException {
      return apply(rsrc.getChangeResource(), rsrc.getPath(), input.content);
    }

    public Response<?> apply(ChangeResource rsrc, String path, RawInput newContent)
        throws ResourceConflictException, AuthException, IOException, StorageException,
            PermissionBackendException {
      if (Strings.isNullOrEmpty(path) || path.charAt(0) == '/') {
        throw new ResourceConflictException("Invalid path: " + path);
      }

      try (Repository repository = repositoryManager.openRepository(rsrc.getProject())) {
        editModifier.modifyFile(repository, rsrc.getNotes(), path, newContent);
      } catch (InvalidChangeOperationException e) {
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
  public static class DeleteContent implements RestModifyView<ChangeEditResource, Input> {

    private final ChangeEditModifier editModifier;
    private final GitRepositoryManager repositoryManager;

    @Inject
    DeleteContent(ChangeEditModifier editModifier, GitRepositoryManager repositoryManager) {
      this.editModifier = editModifier;
      this.repositoryManager = repositoryManager;
    }

    @Override
    public Response<?> apply(ChangeEditResource rsrc, Input input)
        throws AuthException, ResourceConflictException, StorageException, IOException,
            PermissionBackendException {
      return apply(rsrc.getChangeResource(), rsrc.getPath());
    }

    public Response<?> apply(ChangeResource rsrc, String filePath)
        throws AuthException, IOException, StorageException, ResourceConflictException,
            PermissionBackendException {
      try (Repository repository = repositoryManager.openRepository(rsrc.getProject())) {
        editModifier.deleteFile(repository, rsrc.getNotes(), filePath);
      } catch (InvalidChangeOperationException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }

  public static class Get implements RestReadView<ChangeEditResource> {
    private final FileContentUtil fileContentUtil;
    private final ProjectCache projectCache;

    @Option(
        name = "--base",
        aliases = {"-b"},
        usage = "whether to load the content on the base revision instead of the change edit")
    private boolean base;

    @Inject
    Get(FileContentUtil fileContentUtil, ProjectCache projectCache) {
      this.fileContentUtil = fileContentUtil;
      this.projectCache = projectCache;
    }

    @Override
    public Response<BinaryResult> apply(ChangeEditResource rsrc) throws IOException {
      try {
        ChangeEdit edit = rsrc.getChangeEdit();
        return Response.ok(
            fileContentUtil.getContent(
                projectCache.checkedGet(rsrc.getChangeResource().getProject()),
                base
                    ? ObjectId.fromString(edit.getBasePatchSet().getRevision().get())
                    : edit.getEditCommit(),
                rsrc.getPath(),
                null));
      } catch (ResourceNotFoundException | BadRequestException e) {
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
      List<DiffWebLinkInfo> links =
          webLinks.getDiffLinks(
              change.getProject().get(),
              change.getChangeId(),
              edit.getBasePatchSet().getPatchSetId(),
              edit.getBasePatchSet().getRefName(),
              rsrc.getPath(),
              0,
              edit.getRefName(),
              rsrc.getPath());
      r.webLinks = links.isEmpty() ? null : links;
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

    private final ChangeEditModifier editModifier;
    private final GitRepositoryManager repositoryManager;

    @Inject
    EditMessage(ChangeEditModifier editModifier, GitRepositoryManager repositoryManager) {
      this.editModifier = editModifier;
      this.repositoryManager = repositoryManager;
    }

    @Override
    public Object apply(ChangeResource rsrc, Input input)
        throws AuthException, IOException, BadRequestException, ResourceConflictException,
            StorageException, PermissionBackendException {
      if (input == null || Strings.isNullOrEmpty(input.message)) {
        throw new BadRequestException("commit message must be provided");
      }

      Project.NameKey project = rsrc.getProject();
      try (Repository repository = repositoryManager.openRepository(project)) {
        editModifier.modifyMessage(repository, rsrc.getNotes(), input.message);
      } catch (UnchangedCommitMessageException e) {
        throw new ResourceConflictException(e.getMessage());
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
        usage = "whether to load the message on the base revision instead of the change edit")
    private boolean base;

    @Inject
    GetMessage(GitRepositoryManager repoManager, ChangeEditUtil editUtil) {
      this.repoManager = repoManager;
      this.editUtil = editUtil;
    }

    @Override
    public BinaryResult apply(ChangeResource rsrc)
        throws AuthException, IOException, ResourceNotFoundException, StorageException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getNotes(), rsrc.getUser());
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
