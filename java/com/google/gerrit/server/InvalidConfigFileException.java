// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.entities.Project;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/** Exception that is thrown if an invalid config file causes an error. */
public class InvalidConfigFileException extends ConfigInvalidException {
  private static final long serialVersionUID = 1L;

  private final String fileName;

  public InvalidConfigFileException(
      Project.NameKey projectName,
      String branchName,
      ObjectId revision,
      String fileName,
      ConfigInvalidException cause) {
    super(createMessage(projectName, branchName, revision, fileName, cause), cause);
    this.fileName = fileName;
  }

  public String getFileName() {
    return fileName;
  }

  private static String createMessage(
      Project.NameKey projectName,
      String branchName,
      ObjectId revision,
      String fileName,
      ConfigInvalidException cause) {
    StringBuilder msg =
        new StringBuilder("Invalid config file ")
            .append(fileName)
            .append(" in project ")
            .append(projectName.get())
            .append(" in branch ")
            .append(branchName)
            .append(" in commit ")
            .append(revision.name());
    if (cause != null) {
      msg.append(": ").append(cause.getMessage());
    }
    return msg.toString();
  }
}
