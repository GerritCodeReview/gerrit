// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.TagInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.git.ChangeCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Singleton
public class ListTags implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  private TagCache tagCache;

  @Inject
  private ChangeCache changeCache;

  @Inject
  public ListTags(GitRepositoryManager repoManager,
      Provider<ReviewDb> dbProvider) {
    this.repoManager = repoManager;
    this.dbProvider = dbProvider;
  }

  @Override
  public List<TagInfo> apply(ProjectResource resource) throws IOException,
      ResourceNotFoundException {
    List<TagInfo> tags =  Lists.newArrayList();

    Repository repo;
    try {
      repo = repoManager.openRepository(resource.getNameKey());
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }

    try {
      RevWalk rw = new RevWalk(repo);
      try {
        VisibleRefFilter refFilter =
            new VisibleRefFilter(tagCache, changeCache, repo,
                resource.getControl(), dbProvider.get(), false);
        Map<String, Ref> all = refFilter.filter(
            repo.getRefDatabase().getRefs(RefDatabase.ALL), false);
        for (Ref ref : all.values()) {
          String name = ref.getName();
          if (name.startsWith(Constants.R_TAGS)) {
            RevObject object = rw.parseAny(ref.getObjectId());
            if (object instanceof RevTag) {
              // Annotated/signed tag
              tags.add(createTagInfo((RevTag)object, name));
            } else if (object instanceof RevCommit) {
              // Lightweight tag
              tags.add(createTagInfo((RevCommit)object, name));
            }
          }
        }
      } finally {
        rw.dispose();
      }
    } finally {
      repo.close();
    }

    Collections.sort(tags, new Comparator<TagInfo>() {
      @Override
      public int compare(TagInfo a, TagInfo b) {
        return a.ref.compareTo(b.ref);
      }
    });

    return tags;
  }

  private TagInfo createTagInfo(RevTag tag, String name) {
    return new TagInfo(
        name,
        tag.getName(),
        tag.getObject().getName(),
        tag.getFullMessage().trim(),
        CommonConverters.toGitPerson(tag.getTaggerIdent()));
  }

  private TagInfo createTagInfo(RevCommit commit, String name) {
    return new TagInfo(name, commit.getName());
  }
}
