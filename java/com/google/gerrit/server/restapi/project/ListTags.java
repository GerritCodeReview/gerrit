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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefFilter;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class ListTags implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final WebLinks links;

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
    aliases = {"-S", "-s"},
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
      GitRepositoryManager repoManager, PermissionBackend permissionBackend, WebLinks webLinks) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.links = webLinks;
  }

  public ListTags request(ListRefsRequest<TagInfo> request) {
    this.setLimit(request.getLimit());
    this.setStart(request.getStart());
    this.setMatchSubstring(request.getSubstring());
    this.setMatchRegex(request.getRegex());
    return this;
  }

  @Override
  public List<TagInfo> apply(ProjectResource resource)
      throws IOException, ResourceNotFoundException, RestApiException, PermissionBackendException {
    resource.getProjectState().checkStatePermitsRead();

    List<TagInfo> tags = new ArrayList<>();

    PermissionBackend.ForProject perm =
        permissionBackend.currentUser().project(resource.getNameKey());
    try (Repository repo = getRepository(resource.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      Map<String, Ref> all =
          visibleTags(resource.getNameKey(), repo, repo.getRefDatabase().getRefs(Constants.R_TAGS));
      for (Ref ref : all.values()) {
        tags.add(
            createTagInfo(perm.ref(ref.getName()), ref, rw, resource.getProjectState(), links));
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
      throws ResourceNotFoundException, IOException, PermissionBackendException {
    try (Repository repo = getRepository(resource.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      String tagName = id.get();
      if (!tagName.startsWith(Constants.R_TAGS)) {
        tagName = Constants.R_TAGS + tagName;
      }
      Ref ref = repo.getRefDatabase().exactRef(tagName);
      if (ref != null
          && !visibleTags(resource.getNameKey(), repo, ImmutableMap.of(ref.getName(), ref))
              .isEmpty()) {
        return createTagInfo(
            permissionBackend
                .user(resource.getUser())
                .project(resource.getNameKey())
                .ref(ref.getName()),
            ref,
            rw,
            resource.getProjectState(),
            links);
      }
    }
    throw new ResourceNotFoundException(id);
  }

  public static TagInfo createTagInfo(
      PermissionBackend.ForRef perm, Ref ref, RevWalk rw, ProjectState projectState, WebLinks links)
      throws IOException {
    RevObject object = rw.parseAny(ref.getObjectId());

    Boolean canDelete = null;
    if (!isConfigRef(ref.getName())) {
      // Never allow to delete the meta config branch.
      canDelete =
          perm.testOrFalse(RefPermission.DELETE) && projectState.statePermitsWrite() ? true : null;
    }

    List<WebLinkInfo> webLinks = links.getTagLinks(projectState.getName(), ref.getName());
    if (object instanceof RevTag) {
      // Annotated or signed tag
      RevTag tag = (RevTag) object;
      PersonIdent tagger = tag.getTaggerIdent();
      return new TagInfo(
          ref.getName(),
          tag.getName(),
          tag.getObject().getName(),
          tag.getFullMessage().trim(),
          tagger != null ? CommonConverters.toGitPerson(tagger) : null,
          canDelete,
          webLinks.isEmpty() ? null : webLinks,
          tagger != null ? new Timestamp(tagger.getWhen().getTime()) : null);
    }

    Timestamp timestamp =
        object instanceof RevCommit
            ? new Timestamp(((RevCommit) object).getCommitterIdent().getWhen().getTime())
            : null;

    // Lightweight tag
    return new TagInfo(
        ref.getName(),
        ref.getObjectId().getName(),
        canDelete,
        webLinks.isEmpty() ? null : webLinks,
        timestamp);
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
      Project.NameKey project, Repository repo, Map<String, Ref> tags)
      throws PermissionBackendException {
    return permissionBackend
        .currentUser()
        .project(project)
        .filter(
            tags,
            repo,
            RefFilterOptions.builder().setFilterMeta(true).setFilterTagsSeparately(true).build());
  }
}
