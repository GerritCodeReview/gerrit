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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Constants;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * It parses from a configuration file submodule sections.
 * <p>
 * Example of submodule sections:
 *
 * <pre>
 * [submodule "project-a"]
 *     url = http://localhost/a
 *     path = a
 *     branch = .
 *
 * [submodule "project-b"]
 *     url = http://localhost/b
 *     path = b
 *     branch = refs/heads/test
 * </pre>
 */
public class SubmoduleSectionParser {
  private final BlobBasedConfig bbc;
  private final String thisServer;
  private final Branch.NameKey superProjectBranch;
  private final GitRepositoryManager repoManager;

  public SubmoduleSectionParser(final BlobBasedConfig bbc,
      final String thisServer, final Branch.NameKey superProjectBranch,
      final GitRepositoryManager repoManager) {
    this.bbc = bbc;
    this.thisServer = thisServer;
    this.superProjectBranch = superProjectBranch;
    this.repoManager = repoManager;
  }

  public List<SubmoduleSubscription> parseAllSections() {
    List<SubmoduleSubscription> parsedSubscriptions =
        new ArrayList<SubmoduleSubscription>();
    for (final String id : bbc.getSubsections("submodule")) {
      final SubmoduleSubscription subscription = parse(id);
      if (subscription != null) {
        parsedSubscriptions.add(subscription);
      }
    }
    return parsedSubscriptions;
  }

  private SubmoduleSubscription parse(final String id) {
    final String url = bbc.getString("submodule", id, "url");
    final String path = bbc.getString("submodule", id, "path");
    String branch = bbc.getString("submodule", id, "branch");

    try {
      if (url != null && url.length() > 0 && path != null && path.length() > 0
          && branch != null && branch.length() > 0) {
        // All required fields filled.

        boolean urlIsRelative = url.startsWith("../");
        String server = null;
        if (!urlIsRelative) {
          // It is actually an URI. It could be ssh://localhost/project-a.
          server = new URI(url).getHost();
        }
        if ((urlIsRelative)
            || (server != null && server.equalsIgnoreCase(thisServer))) {
          // Subscription really related to this running server.
          if (branch.equals(".")) {
            branch = superProjectBranch.get();
          } else if (!branch.startsWith(Constants.R_REFS)) {
            branch = Constants.R_HEADS + branch;
          }

          final String urlExtractedPath = new URI(url).getPath();
          String projectName = urlExtractedPath;
          int fromIndex = urlExtractedPath.length() - 1;
          while (fromIndex > 0) {
            fromIndex = urlExtractedPath.lastIndexOf('/', fromIndex - 1);
            projectName = urlExtractedPath.substring(fromIndex + 1);

            if (projectName.endsWith(Constants.DOT_GIT_EXT)) {
              projectName = projectName.substring(0, //
                  projectName.length() - Constants.DOT_GIT_EXT.length());
            }

            if (repoManager.list().contains(new Project.NameKey(projectName))) {
              return new SubmoduleSubscription(
                  superProjectBranch,
                  new Branch.NameKey(new Project.NameKey(projectName), branch),
                  path);
            }
          }
        }
      }
    } catch (URISyntaxException e) {
      // Error in url syntax (in fact it is uri syntax)
    }

    return null;
  }
}
