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

import com.google.common.base.Strings;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.changes.FileUploadInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.Base64;

@Singleton
public class UploadFile implements RestModifyView<ChangeResource, FileUploadInput> {
  private final ChangeEditModifier editModifier;
  private final GitRepositoryManager repositoryManager;

  @Inject
  UploadFile(ChangeEditModifier editModifier, GitRepositoryManager repositoryManager) {
    this.editModifier = editModifier;
    this.repositoryManager = repositoryManager;
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, FileUploadInput input)
      throws AuthException, ResourceConflictException, IOException, PermissionBackendException,
          BadRequestException {
    return apply(rsrc, input.path, input.content);
  }

  public Response<?> apply(ChangeResource rsrc, String path, String content)
      throws ResourceConflictException, AuthException, IOException, PermissionBackendException,
          BadRequestException {
    if (Strings.isNullOrEmpty(path) || path.charAt(0) == '/') {
      throw new BadRequestException(path != null ? "Invalid path: " + path : "Path is required");
    } else if (Strings.isNullOrEmpty(content)) {
      throw new BadRequestException("Invalid content");
    }

    RawInput newContent;
    Matcher m = Pattern.compile("data:([\\w/.-]*);([\\w]+),(.*)").matcher(content);
    if (m.matches() && "base64".equals(m.group(2))) {
      newContent = RawInputUtil.create(Base64.decode(m.group(3)));
    } else {
      throw new BadRequestException("File must be encoded with base64 data uri");
    }

    try (Repository repository = repositoryManager.openRepository(rsrc.getProject())) {
      editModifier.modifyFile(repository, rsrc.getNotes(), path, newContent);
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.created();
  }
}
