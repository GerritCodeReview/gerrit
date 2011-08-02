// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Constants;

import java.net.URI;
import java.net.URISyntaxException;

public class SubmoduleSectionParser {
  private final String id;
  private final BlobBasedConfig bbc;
  private final String thisServer;
  private final Branch.NameKey subscriber;
  private final GitRepositoryManager repoManager;
  private String path;
  private String revision;
  private Branch.NameKey submoduleBranch;

  public SubmoduleSectionParser(final String id, final BlobBasedConfig bbc,
      final String thisServer, final Branch.NameKey subscriber,
      final GitRepositoryManager repoManager) {
    this.id = id;
    this.bbc = bbc;
    this.thisServer = thisServer;
    this.subscriber = subscriber;
    this.repoManager = repoManager;
  }

  public boolean parse() {
    boolean parseResult = true;

    String url = bbc.getString("submodule", id, "url");
    path = bbc.getString("submodule", id, "path");
    revision = bbc.getString("submodule", id, "revision");

    try {
      if (url != null && url.length() > 0 && path != null && path.length() > 0
          && revision != null && revision.length() > 0) {
        boolean urlIsRelative = url.startsWith("/");
        String server = null;
        if (!urlIsRelative) {
          // It is actually an URI. It could be ssh://localhost/project-a.
          server = new URI(url).getHost();
        }
        if ((urlIsRelative)
            || (server != null && server.equalsIgnoreCase(thisServer))) {
          if (revision.equals(".")) {
            revision = subscriber.get();
          } else if (!revision.startsWith(Constants.R_REFS)) {
            revision = Constants.R_HEADS + revision;
          }

          final String urlExtractedPath = new URI(url).getPath();
          String projectName = urlExtractedPath;
          int fromIndex = urlExtractedPath.length() - 1;
          while (fromIndex > 0) {
            fromIndex = urlExtractedPath.lastIndexOf('/', fromIndex - 1);
            projectName = urlExtractedPath.substring(fromIndex + 1);
            if (repoManager.list().contains(new Project.NameKey(projectName))) {
              break;
            } else {
              projectName = null;
            }
          }

          if (projectName != null) {
            submoduleBranch =
                new Branch.NameKey(new Project.NameKey(projectName), revision);
          } else {
            // Project name could not be resolved.
            parseResult = false;
          }
        } else {
          // The subscription is not related to the running server.
          parseResult = false;
        }
      } else {
        // Not all required fields filled (url, path, revision).
        parseResult = false;
      }
    } catch (URISyntaxException e) {
      // Error in url syntax (in fact it is uri syntax)
      parseResult = false;
    }

    return parseResult;
  }

  public String getPath() {
    return path;
  }

  public Branch.NameKey getSubmoduleBranch() {
    return submoduleBranch;
  }
}
