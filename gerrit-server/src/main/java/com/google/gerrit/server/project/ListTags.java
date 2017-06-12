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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class ListTags implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> dbProvider;
  private final TagCache tagCache;
  private final ChangeNotes.Factory changeNotesFactory;
  @Nullable private final SearchingChangeCacheImpl changeCache;

  @Option(
    name = "--limit",
    aliases = {"-n"},
    metaVar = "CNT",
    usage = "maximum number of tags to list"
  )
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
    name = "--start",
    aliases = {"-s"},
    metaVar = "CNT",
    usage = "number of tags to skip"
  )
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
    name = "--match",
    aliases = {"-m"},
    metaVar = "MATCH",
    usage = "match tags substring"
  )
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(
    name = "--regex",
    aliases = {"-r"},
    metaVar = "REGEX",
    usage = "match tags regex"
  )
  public void setMatchRegex(String matchRegex) {
    this.matchRegex = matchRegex;
  }

  private int limit;
  private int start;
  private String matchSubstring;
  private String matchRegex;

  @Inject
  public ListTags(
      GitRepositoryManager repoManager,
      Provider<ReviewDb> dbProvider,
      TagCache tagCache,
      ChangeNotes.Factory changeNotesFactory,
      @Nullable SearchingChangeCacheImpl changeCache) {
    this.repoManager = repoManager;
    this.dbProvider = dbProvider;
    this.tagCache = tagCache;
    this.changeNotesFactory = changeNotesFactory;
    this.changeCache = changeCache;
  }

  @Override
  public List<TagInfo> apply(ProjectResource resource)
      throws IOException, ResourceNotFoundException, BadRequestException {
    List<TagInfo> tags = new ArrayList<>();

    try (Repository repo = getRepository(resource.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      Map<String, Ref> all =
          visibleTags(resource.getControl(), repo, repo.getRefDatabase().getRefs(Constants.R_TAGS));
      for (Ref ref : all.values()) {
        tags.add(createTagInfo(ref, rw));
      }
    }

    Collections.sort(
        tags,
        new Comparator<TagInfo>() {
          @Override
          public int compare(TagInfo a, TagInfo b) {
            return a.ref.compareTo(b.ref);
          }
        });

    return new RefFilter<TagInfo>(Constants.R_TAGS)
        .start(start)
        .limit(limit)
        .subString(matchSubstring)
        .regex(matchRegex)
        .filter(tags);
  }

  public TagInfo get(ProjectResource resource, IdString id)
      throws ResourceNotFoundException, IOException {
    try (Repository repo = getRepository(resource.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      String tagName = id.get();
      if (!tagName.startsWith(Constants.R_TAGS)) {
        tagName = Constants.R_TAGS + tagName;
      }
      Ref ref = repo.getRefDatabase().exactRef(tagName);
      if (ref != null
          && !visibleTags(resource.getControl(), repo, ImmutableMap.of(ref.getName(), ref))
              .isEmpty()) {
        return createTagInfo(ref, rw);
      }
    }
    throw new ResourceNotFoundException(id);
  }

  public static TagInfo createTagInfo(Ref ref, RevWalk rw)
      throws MissingObjectException, IOException {
    RevObject object = rw.parseAny(ref.getObjectId());
    if (object instanceof RevTag) {
      // Annotated or signed tag
      RevTag tag = (RevTag) object;
      PersonIdent tagger = tag.getTaggerIdent();
      return new TagInfo(
          ref.getName(),
          tag.getName(),
          tag.getObject().getName(),
          tag.getFullMessage().trim(),
          tagger != null ? CommonConverters.toGitPerson(tag.getTaggerIdent()) : null);
    }
    // Lightweight tag
    return new TagInfo(ref.getName(), ref.getObjectId().getName());
  }

  private Repository getRepository(Project.NameKey project)
      throws ResourceNotFoundException, IOException {
    try {
      return repoManager.openRepository(project);
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }
  }

  private Map<String, Ref> visibleTags(
      ProjectControl control, Repository repo, Map<String, Ref> tags) {
    return new VisibleRefFilter(
            tagCache, changeNotesFactory, changeCache, repo, control, dbProvider.get(), false)
        .filter(tags, true);
  }
}
