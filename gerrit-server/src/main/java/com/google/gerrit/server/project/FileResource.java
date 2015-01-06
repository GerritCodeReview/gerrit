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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.ObjectId;

public class FileResource implements RestResource {
  public static final TypeLiteral<RestView<FileResource>> FILE_KIND =
      new TypeLiteral<RestView<FileResource>>() {};

  private final ProjectControl project;
  private final ObjectId rev;
  private final String path;

  public FileResource(ProjectControl project, ObjectId rev, String path) {
    this.project = project;
    this.rev = rev;
    this.path = path;
  }

  public ProjectControl getProject() {
    return project;
  }

  public ObjectId getRev() {
    return rev;
  }

  public String getPath() {
    return path;
  }
}
