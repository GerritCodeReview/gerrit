// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.GetDiff;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.IOException;

public class GetRawDiff implements RestReadView<FileResource> {
  private final GitRepositoryManager repoManager;
  private final GetDiff getDiff;

  @Option(name = "--base", metaVar = "REVISION")
  String base;


  @Inject
  GetRawDiff(GitRepositoryManager repoManager, GetDiff getDiff) {
    this.repoManager = repoManager;
    this.getDiff = getDiff;
  }

  @Override
  public BinaryResult apply(FileResource resource)
      throws ResourceNotFoundException, IOException, NoSuchChangeException,
      OrmException, ResourceConflictException, AuthException, InvalidChangeOperationException {
    try {
      DiffPreferencesInfo prefs = new DiffPreferencesInfo();
      prefs.ignoreWhitespace = Whitespace.IGNORE_LEADING_AND_TRAILING;
      prefs.context = (int) DiffPreferencesInfo.WHOLE_FILE_CONTEXT;
      prefs.intralineDifference = false;
      DiffInfo diffInfo = getDiff.getDiffInfo(resource, prefs, base, 0, false, false);
      String output = "";
      for (String s: diffInfo.diffHeader) {
        output += s + "\n";
      }
      output += "\n";
      for (ContentEntry c: diffInfo.content) {
        if (c.ab == null) {
          if (c.a != null) {
            for (String s: c.a) {
              output += "-" + s + "\n";
            }
          }
          if (c.b != null) {
            for (String s: c.b) {
              output += "+" + s + "\n";
            }
          }
        } else {
          for (String s: c.ab) {
            output += " " + s + "\n";
          }
        }
      }
      return BinaryResult.create(output);
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } catch (LargeObjectException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
  }
}


