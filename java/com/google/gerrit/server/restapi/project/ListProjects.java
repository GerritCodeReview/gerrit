// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.json.OutputFormat;
import java.io.IOException;
import java.util.SortedMap;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * List projects visible to the calling user.
 *
 * <p>Implement {@code GET /projects/}, without a {@code query=} parameter.
 */
public interface ListProjects extends RestReadView<TopLevelResource> {
  public enum FilterType {
    CODE {
      @Override
      boolean matches(Repository git) throws IOException {
        return !PERMISSIONS.matches(git);
      }

      @Override
      boolean useMatch() {
        return true;
      }
    },
    PERMISSIONS {
      @Override
      boolean matches(Repository git) throws IOException {
        Ref head = git.getRefDatabase().exactRef(Constants.HEAD);
        return head != null
            && head.isSymbolic()
            && RefNames.REFS_CONFIG.equals(head.getLeaf().getName());
      }

      @Override
      boolean useMatch() {
        return true;
      }
    },
    ALL {
      @Override
      boolean matches(Repository git) {
        return true;
      }

      @Override
      boolean useMatch() {
        return false;
      }
    };

    abstract boolean matches(Repository git) throws IOException;

    abstract boolean useMatch();
  }

  void setFormat(OutputFormat fmt);

  void addShowBranch(String branch);

  void setShowTree(boolean showTree);

  void setFilterType(FilterType type);

  void setShowDescription(boolean showDescription);

  void setAll(boolean all);

  void setState(com.google.gerrit.extensions.client.ProjectState state);

  void setLimit(int limit);

  void setStart(int start);

  void setMatchPrefix(String matchPrefix);

  void setMatchSubstring(String matchSubstring);

  void setMatchRegex(String matchRegex);

  void setGroupUuid(AccountGroup.UUID groupUuid);

  @Override
  default Response<Object> apply(TopLevelResource resource) throws Exception {
    return Response.ok(apply());
  }

  SortedMap<String, ProjectInfo> apply() throws Exception;
}
