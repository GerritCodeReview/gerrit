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

package com.google.gerrit.server.util.git;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;

/**
 * It parses from a configuration file submodule sections.
 *
 * <p>Example of submodule sections:
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

  private final Config config;
  private final String canonicalWebUrl;
  private final BranchNameKey superProjectBranch;

  public SubmoduleSectionParser(
      Config config, String canonicalWebUrl, BranchNameKey superProjectBranch) {
    this.config = config;
    this.canonicalWebUrl = canonicalWebUrl;
    this.superProjectBranch = superProjectBranch;
  }

  public Set<SubmoduleSubscription> parseAllSections() {
    Set<SubmoduleSubscription> parsedSubscriptions = new HashSet<>();
    for (String id : config.getSubsections("submodule")) {
      final SubmoduleSubscription subscription = parse(id);
      if (subscription != null) {
        parsedSubscriptions.add(subscription);
      }
    }
    return parsedSubscriptions;
  }

  private SubmoduleSubscription parse(String id) {
    final String url = config.getString("submodule", id, "url");
    final String path = config.getString("submodule", id, "path");
    String branch = config.getString("submodule", id, "branch");

    try {
      if (url != null
          && url.length() > 0
          && path != null
          && path.length() > 0
          && branch != null
          && branch.length() > 0) {
        // All required fields filled.
        String project;

        if (branch.equals(".")) {
          branch = superProjectBranch.branch();
        }

        // relative URL
        if (url.startsWith("../")) {
          // prefix with a slash for easier relative path walks
          project = '/' + superProjectBranch.project().get();
          String hostPart = url;
          while (hostPart.startsWith("../")) {
            int lastSlash = project.lastIndexOf('/');
            if (lastSlash < 0) {
              // too many levels up, ignore for now
              return null;
            }
            project = project.substring(0, lastSlash);
            hostPart = hostPart.substring(3);
          }
          project = project + "/" + hostPart;

          // remove leading '/'
          project = project.substring(1);
        } else {
          // It is actually an URI. It could be ssh://localhost/project-a.
          URI targetServerURI = new URI(url);
          URI thisServerURI = new URI(canonicalWebUrl);
          String thisHost = thisServerURI.getHost();
          String targetHost = targetServerURI.getHost();
          if (thisHost == null || targetHost == null || !targetHost.equalsIgnoreCase(thisHost)) {
            return null;
          }
          String p1 = targetServerURI.getPath();
          String p2 = thisServerURI.getPath();
          if (!p1.startsWith(p2)) {
            // When we are running the server at
            // http://server/my-gerrit/ but the subscription is for
            // http://server/other-teams-gerrit/
            return null;
          }
          // skip common part
          project = p1.substring(p2.length());
        }

        while (project.startsWith("/")) {
          project = project.substring(1);
        }

        if (project.endsWith(Constants.DOT_GIT_EXT)) {
          project =
              project.substring(
                  0, //
                  project.length() - Constants.DOT_GIT_EXT.length());
        }
        Project.NameKey projectKey = Project.nameKey(project);
        return new SubmoduleSubscription(
            superProjectBranch, BranchNameKey.create(projectKey, branch), path);
      }
    } catch (URISyntaxException e) {
      // Error in url syntax (in fact it is uri syntax)
    }
    return null;
  }
}
