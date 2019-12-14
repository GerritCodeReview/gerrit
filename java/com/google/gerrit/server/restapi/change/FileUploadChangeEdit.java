// Copyright (C) 2019 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
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
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.Base64;
import org.kohsuke.args4j.Option;

@Singleton
public class FileUploadChangeEdit implements RestModifyView<ChangeEditResource, FileUploadChangeEdit.Input> {
  public static class Input {
    @DefaultInput public RawInput content;
  }

  private final ChangeEditModifier editModifier;
  private final GitRepositoryManager repositoryManager;

  @Inject
  FileUploadChangeEdit(ChangeEditModifier editModifier, GitRepositoryManager repositoryManager) {
    this.editModifier = editModifier;
    this.repositoryManager = repositoryManager;
  }

  @Override
  public Response<?> apply(ChangeEditResource rsrc, FileUploadChangeEdit.Input input)
      throws AuthException, ResourceConflictException, IOException, PermissionBackendException {
    return apply(rsrc.getChangeResource(), rsrc.getPath(), input.content);
  }

  public Response<?> apply(ChangeResource rsrc, String path, RawInput newContent)
      throws ResourceConflictException, AuthException, IOException, PermissionBackendException {
    if (Strings.isNullOrEmpty(path) || path.charAt(0) == '/') {
      throw new ResourceConflictException("Invalid path: " + path);
    }

    String contentString =
        CharStreams.toString(
            new InputStreamReader(newContent.getInputStream(), UTF_8));
    Matcher m = Pattern.compile("data:([\\w/.-]+);([\\w]+),(.*)").matcher(contentString);
    if (m.matches()) {
      if ("base64".equals(m.group(2))) {
        newContent = RawInputUtil.create(Base64.decode(m.group(3)));
      } else {
        throw new ResourceConflictException("file must be encoded with base64");
      }
    } else {
      throw new ResourceConflictException("file must be encoded with base64");
    }

    try (Repository repository = repositoryManager.openRepository(rsrc.getProject())) {
      editModifier.modifyFile(repository, rsrc.getNotes(), path, newContent);
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.none();
  }
}
