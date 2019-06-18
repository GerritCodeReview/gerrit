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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.TreeEntry;
import com.google.gerrit.extensions.common.TreeInfo;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.List;
import org.eclipse.jgit.lib.Constants; 
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Option;

public class GetTree implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private String path;
  private int recursive;

  @Inject
  GetTree(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Option(name = "--recursive", usage = "recurse into sub-trees")
  public GetTree setRecursive(int recursive) {
    this.recursive = recursive;
    return this;
  }
  
  @Option(name = "--path", usage = "filter a path in the repository")
  public GetTree setPath(String path) {
    this.path = path;
    return this;
  }

  @Override
  public Response<TreeInfo> apply(RevisionResource rsrc) 
		  throws IOException, RestApiException {
    Project.NameKey p = rsrc.getChange().getProject();
    try (Repository repo = repoManager.openRepository(p);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId oid = rsrc.getPatchSet().commitId();
      RevTree tree = rw.parseTree(oid);
      
      Response<TreeInfo> r = Response.ok(toTreeInfo (repo, tree));
      if (rsrc.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    }
  }
  
  private TreeInfo toTreeInfo(Repository repo, RevTree tree)
      throws IOException, RestApiException {
    List<TreeEntry> treeEntries = new ArrayList<>();
    try (RevWalk rw = new RevWalk(repo);
        TreeWalk tw = new TreeWalk(repo)){
      //Filter path if it's provided by client
      if (!Strings.isNullOrEmpty(path)) {
        try {
          ObjectId oid = getSubTree(rw, tree, path);
          tree = rw.parseTree(oid); 
        } catch(IllegalArgumentException e) {
          throw new ResourceNotFoundException(
              "Can't find " + path + " in the repository");
        }
      }
      
      tw.reset(tree);
      tw.setRecursive(false); //Set false to obtain both trees and blobs
      while (tw.next()) {
        TreeEntry entry = new TreeEntry();
        FileMode mode = tw.getFileMode(0);
        entry.id = tw.getObjectId(0).name();
        entry.mode = mode.toString();
        entry.path = tw.getPathString();
        entry.type = Constants.typeString(mode.getObjectType());
        treeEntries.add(entry);
        
      //Get subtrees if it's asked by client 
        if (tw.isSubtree() && (recursive == 1)) {
          tw.enterSubtree();
        }
      }
      tw.close();
      rw.close();
    } catch (IOException e){
      throw new IOException(e);
    }
    
    TreeInfo info = new TreeInfo();
    info.sha = tree.getName();
    info.tree = treeEntries;
    return info;
  }
  
  private ObjectId getSubTree(RevWalk rw, RevTree tree, String path)
      throws IOException {
    try (TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
      tw.setFilter(PathFilterGroup.createFromStrings(
          Collections.singleton(path)));
      tw.reset(tree);
      while (tw.next()) {
        if (tw.isSubtree() && !path.equals(tw.getPathString())) {
          tw.enterSubtree();
          continue;
        }
        return tw.getObjectId(0);
      }
    }
    return null; // never reached.
  }
}
