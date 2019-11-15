
package com.google.gerrit.server.restapi.project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import com.google.gerrit.extensions.api.projects.SubmoduleInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ListSubmodules implements RestReadView<BranchResource> {
  private final GitRepositoryManager repoManager;
  
  @Inject
  ListSubmodules(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public Response<List<SubmoduleInfo>> apply(BranchResource resource) throws ConfigInvalidException, RestApiException, IOException {
    List<SubmoduleInfo> result = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(resource.getProjectState().getNameKey())) {
    //try (Repository repo = repoManager.openRepository(resource.getProjectState().getNameKey());
    //    RevWalk rw = new RevWalk(repo)) {
    //  RevTree tree = rw.parseCommit(repo.exactRef(resource.getRef()).getObjectId()).getTree();
      //try (SubmoduleWalk sw = SubmoduleWalk.forPath(repo, tree, ".")) {
      
      if (SubmoduleWalk.containsGitModulesFile(repo)) {
        try (SubmoduleWalk sw = SubmoduleWalk.forIndex(repo)) {
          while (sw != null && sw.next()) {
            SubmoduleInfo info = new SubmoduleInfo();
            info.url = sw.getModulesUrl();
            info.path = sw.getModulesPath();
            info.revision = sw.getObjectId().name();
            result.add(info);
          }
        }
      }
    }
    return Response.ok(result);
  }
}
