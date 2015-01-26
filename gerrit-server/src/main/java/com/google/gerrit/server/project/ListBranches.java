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

package com.google.gerrit.server.project;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ListBranches implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final DynamicMap<RestView<BranchResource>> branchViews;
  private final WebLinks webLinks;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "maximum number of branches to list")
  private int limit;

  @Option(name = "--start", aliases = {"-s"}, metaVar = "CNT", usage = "number of branches to skip")
  private int start;

  @Option(name = "--match", aliases = {"-m"}, metaVar = "MATCH", usage = "match branches substring")
  private String matchSubstring;

  @Option(name = "--regex", aliases = {"-r"}, metaVar = "REGEX", usage = "match branches regex")
  private String matchRegex;

  @Inject
  public ListBranches(GitRepositoryManager repoManager,
      DynamicMap<RestView<BranchResource>> branchViews,
      WebLinks webLinks) {
    this.repoManager = repoManager;
    this.branchViews = branchViews;
    this.webLinks = webLinks;
  }

  @Override
  public List<BranchInfo> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, IOException, BadRequestException {
    List<BranchInfo> branches = Lists.newArrayList();

    BranchInfo headBranch = null;
    BranchInfo configBranch = null;
    final Set<String> targets = Sets.newHashSet();

    final Repository db;
    try {
      db = repoManager.openRepository(rsrc.getNameKey());
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }

    try {
      final Map<String, Ref> all = db.getRefDatabase().getRefs(RefDatabase.ALL);

      if (!all.containsKey(Constants.HEAD)) {
        // The branch pointed to by HEAD doesn't exist yet, so getAllRefs
        // filtered it out. If we ask for it individually we can find the
        // underlying target and put it into the map anyway.
        //
        try {
          Ref head = db.getRef(Constants.HEAD);
          if (head != null) {
            all.put(Constants.HEAD, head);
          }
        } catch (IOException e) {
          // Ignore the failure reading HEAD.
        }
      }

      for (final Ref ref : all.values()) {
        if (ref.isSymbolic()) {
          targets.add(ref.getTarget().getName());
        }
      }

      for (final Ref ref : all.values()) {
        if (ref.isSymbolic()) {
          // A symbolic reference to another branch, instead of
          // showing the resolved value, show the name it references.
          //
          String target = ref.getTarget().getName();
          RefControl targetRefControl = rsrc.getControl().controlForRef(target);
          if (!targetRefControl.isVisible()) {
            continue;
          }
          if (target.startsWith(Constants.R_HEADS)) {
            target = target.substring(Constants.R_HEADS.length());
          }

          BranchInfo b = new BranchInfo(ref.getName(), target, false);

          if (Constants.HEAD.equals(ref.getName())) {
            headBranch = b;
          } else {
            b.setCanDelete(targetRefControl.canDelete());
            branches.add(b);
          }
          continue;
        }

        final RefControl refControl = rsrc.getControl().controlForRef(ref.getName());
        if (refControl.isVisible()) {
          if (ref.getName().startsWith(Constants.R_HEADS)) {
            branches.add(createBranchInfo(ref, refControl, targets));
          } else if (RefNames.REFS_CONFIG.equals(ref.getName())) {
            configBranch = createBranchInfo(ref, refControl, targets);
          }
        }
      }
    } finally {
      db.close();
    }
    Collections.sort(branches, new Comparator<BranchInfo>() {
      @Override
      public int compare(final BranchInfo a, final BranchInfo b) {
        return a.ref.compareTo(b.ref);
      }
    });
    if (configBranch != null) {
      branches.add(0, configBranch);
    }
    if (headBranch != null) {
      branches.add(0, headBranch);
    }

    List<BranchInfo> filteredBranches;
    if ((matchSubstring != null && !matchSubstring.isEmpty())
        || (matchRegex != null && !matchRegex.isEmpty())) {
      filteredBranches = filterBranches(branches);
    } else {
      filteredBranches = branches;
    }
    if (!filteredBranches.isEmpty()) {
      int end = filteredBranches.size();
      if (limit > 0 && start + limit < end) {
        end = start + limit;
      }
      if (start <= end) {
        filteredBranches = filteredBranches.subList(start, end);
      } else {
        filteredBranches = Collections.emptyList();
      }
    }
    return filteredBranches;
  }

  private List<BranchInfo> filterBranches(List<BranchInfo> branches)
      throws BadRequestException {
    if (matchSubstring != null) {
      return Lists.newArrayList(Iterables.filter(branches,
          new Predicate<BranchInfo>() {
            @Override
            public boolean apply(BranchInfo in) {
              if (!in.ref.startsWith(Constants.R_HEADS)){
                return in.ref.toLowerCase(Locale.US).contains(
                    matchSubstring.toLowerCase(Locale.US));
              } else {
                return in.ref.substring(Constants.R_HEADS.length())
                    .toLowerCase(Locale.US).contains(matchSubstring.toLowerCase(Locale.US));
              }
            }
          }));
    } else if (matchRegex != null) {
      if (matchRegex.startsWith("^")) {
        matchRegex = matchRegex.substring(1);
        if (matchRegex.endsWith("$") && !matchRegex.endsWith("\\$")) {
          matchRegex = matchRegex.substring(0, matchRegex.length() - 1);
        }
      }
      if (matchRegex.equals(".*")) {
        return branches;
      }
      try {
        final RunAutomaton a =
            new RunAutomaton(new RegExp(matchRegex).toAutomaton());
        return Lists.newArrayList(Iterables.filter(
            branches, new Predicate<BranchInfo>() {
              @Override
              public boolean apply(BranchInfo in) {
                if (!in.ref.startsWith(Constants.R_HEADS)){
                  return a.run(in.ref);
                } else {
                  return a.run(in.ref.substring(Constants.R_HEADS.length()));
                }
              }
            }));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(e.getMessage());
      }
    }
    return branches;
  }

  private BranchInfo createBranchInfo(Ref ref, RefControl refControl,
      Set<String> targets) {
    BranchInfo info = new BranchInfo(ref.getName(),
        ref.getObjectId() != null ? ref.getObjectId().name() : null,
        !targets.contains(ref.getName()) && refControl.canDelete());
    for (UiAction.Description d : UiActions.from(
        branchViews,
        new BranchResource(refControl.getProjectControl(), info),
        Providers.of(refControl.getCurrentUser()))) {
      if (info.actions == null) {
        info.actions = new TreeMap<>();
      }
      info.actions.put(d.getId(), new ActionInfo(d));
    }
    FluentIterable<WebLinkInfo> links =
        webLinks.getBranchLinks(
            refControl.getProjectControl().getProject().getName(), ref.getName());
    info.webLinks = links.isEmpty() ? null : links.toList();
    return info;
  }

  public static class BranchInfo {
    public String ref;
    public String revision;
    public Boolean canDelete;
    public Map<String, ActionInfo> actions;
    public List<WebLinkInfo> webLinks;

    public BranchInfo(String ref, String revision, boolean canDelete) {
      this.ref = ref;
      this.revision = revision;
      this.canDelete = canDelete;
    }

    void setCanDelete(boolean canDelete) {
      this.canDelete = canDelete ? true : null;
    }
  }
}
