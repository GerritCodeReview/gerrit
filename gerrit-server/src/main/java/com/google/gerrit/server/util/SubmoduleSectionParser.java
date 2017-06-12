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

  private final Config bbc;
  private final String canonicalWebUrl;
  private final Branch.NameKey superProjectBranch;

  public SubmoduleSectionParser(
      Config bbc, String canonicalWebUrl, Branch.NameKey superProjectBranch) {
    this.bbc = bbc;
    this.canonicalWebUrl = canonicalWebUrl;
    this.superProjectBranch = superProjectBranch;
  }

  public Set<SubmoduleSubscription> parseAllSections() {
    Set<SubmoduleSubscription> parsedSubscriptions = new HashSet<>();
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
      if (url != null
          && url.length() > 0
          && path != null
          && path.length() > 0
          && branch != null
          && branch.length() > 0) {
        // All required fields filled.
        String project;

        if (branch.equals(".")) {
          branch = superProjectBranch.get();
        }

        // relative URL
        if (url.startsWith("../")) {
          // prefix with a slash for easier relative path walks
          project = '/' + superProjectBranch.getParentKey().get();
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
        Project.NameKey projectKey = new Project.NameKey(project);
        return new SubmoduleSubscription(
            superProjectBranch, new Branch.NameKey(projectKey, branch), path);
      }
    } catch (URISyntaxException e) {
      // Error in url syntax (in fact it is uri syntax)
    }
    return null;
  }
}
