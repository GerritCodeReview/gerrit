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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.TagInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.git.ChangeCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
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
  private final TagCache tagCache;
  private final ChangeCache changeCache;

  @Inject
  public ListTags(GitRepositoryManager repoManager,
      Provider<ReviewDb> dbProvider,
      TagCache tagCache,
      ChangeCache changeCache) {
    this.repoManager = repoManager;
    this.dbProvider = dbProvider;
    this.tagCache = tagCache;
    this.changeCache = changeCache;
  }

  @Override
  public List<TagInfo> apply(ProjectResource resource) throws IOException,
      ResourceNotFoundException {
    List<TagInfo> tags = Lists.newArrayList();

    Repository repo = getRepository(resource.getNameKey());

    try {
      RevWalk rw = new RevWalk(repo);
      try {
        Map<String, Ref> all = visibleTags(resource.getControl(), repo,
            repo.getRefDatabase().getRefs(Constants.R_TAGS));
        for (Ref ref : all.values()) {
          tags.add(createTagInfo(ref, rw));
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

  public TagInfo get(ProjectResource resource, IdString id)
      throws ResourceNotFoundException, IOException {
    try (Repository repo = getRepository(resource.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      String tagName = id.get();
      if (!tagName.startsWith(Constants.R_TAGS)) {
        tagName = Constants.R_TAGS + tagName;
      }
      Ref ref = repo.getRefDatabase().getRef(tagName);
      if (ref != null && !visibleTags(resource.getControl(), repo,
          ImmutableMap.of(ref.getName(), ref)).isEmpty()) {
        return createTagInfo(ref, rw);
      }
    }
    throw new ResourceNotFoundException(id);
  }

  private Repository getRepository(Project.NameKey project)
      throws ResourceNotFoundException, IOException {
    try {
      return repoManager.openRepository(project);
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }
  }

  private Map<String, Ref> visibleTags(ProjectControl control, Repository repo,
      Map<String, Ref> tags) {
    return new VisibleRefFilter(tagCache, changeCache, repo,
        control, dbProvider.get(), false).filter(tags, true);
  }

  private static TagInfo createTagInfo(Ref ref, RevWalk rw)
      throws MissingObjectException, IOException {
    RevObject object = rw.parseAny(ref.getObjectId());
    if (object instanceof RevTag) {
      RevTag tag = (RevTag)object;
      // Annotated or signed tag
      return new TagInfo(
          Constants.R_TAGS + tag.getTagName(),
          tag.getName(),
          tag.getObject().getName(),
          tag.getFullMessage().trim(),
          CommonConverters.toGitPerson(tag.getTaggerIdent()));
    } else {
      // Lightweight tag
      return new TagInfo(
          ref.getName(),
          ref.getObjectId().getName());
    }
  }
}
