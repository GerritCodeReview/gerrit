// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_COMMITS;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_FILES;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.COMMIT_FOOTERS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_FILES;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_ACCOUNTS;
import static com.google.gerrit.extensions.client.ListChangesOption.DOWNLOAD_COMMANDS;
import static com.google.gerrit.extensions.client.ListChangesOption.PUSH_CERTIFICATES;
import static com.google.gerrit.extensions.client.ListChangesOption.WEB_LINKS;
import static com.google.gerrit.server.CommonConverters.toGitPerson;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.PushCertificateInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GpgApiAdapter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Produces {@link RevisionInfo} and {@link CommitInfo} which are serialized to JSON afterwards. */
public class RevisionJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    RevisionJson create(Iterable<ListChangesOption> options);
  }

  private final MergeUtil.Factory mergeUtilFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final FileInfoJson fileInfoJson;
  private final GpgApiAdapter gpgApi;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeKindCache changeKindCache;
  private final ActionJson actionJson;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final WebLinks webLinks;
  private final Provider<CurrentUser> userProvider;
  private final ProjectCache projectCache;
  private final ImmutableSet<ListChangesOption> options;
  private final AccountLoader.Factory accountLoaderFactory;
  private final AnonymousUser anonymous;
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final ChangeNotes.Factory notesFactory;
  private final boolean lazyLoad;

  @Inject
  RevisionJson(
      Provider<CurrentUser> userProvider,
      AnonymousUser anonymous,
      ProjectCache projectCache,
      IdentifiedUser.GenericFactory userFactory,
      MergeUtil.Factory mergeUtilFactory,
      FileInfoJson fileInfoJson,
      AccountLoader.Factory accountLoaderFactory,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      WebLinks webLinks,
      ActionJson actionJson,
      GpgApiAdapter gpgApi,
      ChangeResource.Factory changeResourceFactory,
      ChangeKindCache changeKindCache,
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      ChangeNotes.Factory notesFactory,
      @Assisted Iterable<ListChangesOption> options) {
    this.userProvider = userProvider;
    this.anonymous = anonymous;
    this.projectCache = projectCache;
    this.userFactory = userFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.fileInfoJson = fileInfoJson;
    this.accountLoaderFactory = accountLoaderFactory;
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
    this.webLinks = webLinks;
    this.actionJson = actionJson;
    this.gpgApi = gpgApi;
    this.changeResourceFactory = changeResourceFactory;
    this.changeKindCache = changeKindCache;
    this.permissionBackend = permissionBackend;
    this.notesFactory = notesFactory;
    this.repoManager = repoManager;
    this.options = ImmutableSet.copyOf(options);
    this.lazyLoad = containsAnyOf(this.options, ChangeJson.REQUIRE_LAZY_LOAD);
  }

  /**
   * Returns a {@link RevisionInfo} based on a change and patch set. Reads from the repository
   * depending on the options provided when constructing this instance.
   */
  public RevisionInfo getRevisionInfo(ChangeData cd, PatchSet in)
      throws PatchListNotAvailableException, GpgException, IOException, PermissionBackendException {
    AccountLoader accountLoader = accountLoaderFactory.create(has(DETAILED_ACCOUNTS));
    try (Repository repo = openRepoIfNecessary(cd.project());
        RevWalk rw = newRevWalk(repo)) {
      RevisionInfo rev = toRevisionInfo(accountLoader, cd, in, repo, rw, true, null);
      accountLoader.fill();
      return rev;
    }
  }

  /**
   * Returns a {@link CommitInfo} based on a commit and formatting options. Uses the provided
   * RevWalk and assumes it is backed by an open repository.
   */
  public CommitInfo getCommitInfo(
      Project.NameKey project, RevWalk rw, RevCommit commit, boolean addLinks, boolean fillCommit)
      throws IOException {
    CommitInfo info = new CommitInfo();
    if (fillCommit) {
      info.commit = commit.name();
    }
    info.parents = new ArrayList<>(commit.getParentCount());
    info.author = toGitPerson(commit.getAuthorIdent());
    info.committer = toGitPerson(commit.getCommitterIdent());
    info.subject = commit.getShortMessage();
    info.message = commit.getFullMessage();

    if (addLinks) {
      List<WebLinkInfo> links = webLinks.getPatchSetLinks(project, commit.name());
      info.webLinks = links.isEmpty() ? null : links;
    }

    for (RevCommit parent : commit.getParents()) {
      rw.parseBody(parent);
      CommitInfo i = new CommitInfo();
      i.commit = parent.name();
      i.subject = parent.getShortMessage();
      if (addLinks) {
        List<WebLinkInfo> parentLinks = webLinks.getParentLinks(project, parent.name());
        i.webLinks = parentLinks.isEmpty() ? null : parentLinks;
      }
      info.parents.add(i);
    }
    return info;
  }

  /**
   * Returns multiple {@link RevisionInfo}s for a single change. Uses the provided {@link
   * AccountLoader} to lazily populate accounts. Callers have to call {@link AccountLoader#fill()}
   * afterwards to populate all accounts in the returned {@link RevisionInfo}s.
   */
  Map<String, RevisionInfo> getRevisions(
      AccountLoader accountLoader,
      ChangeData cd,
      Map<PatchSet.Id, PatchSet> map,
      Optional<PatchSet.Id> limitToPsId,
      ChangeInfo changeInfo)
      throws PatchListNotAvailableException, GpgException, IOException, PermissionBackendException {
    Map<String, RevisionInfo> res = new LinkedHashMap<>();
    try (Repository repo = openRepoIfNecessary(cd.project());
        RevWalk rw = newRevWalk(repo)) {
      for (PatchSet in : map.values()) {
        PatchSet.Id id = in.getId();
        boolean want;
        if (has(ALL_REVISIONS)) {
          want = true;
        } else if (limitToPsId.isPresent()) {
          want = id.equals(limitToPsId.get());
        } else {
          want = id.equals(cd.change().currentPatchSetId());
        }
        if (want) {
          res.put(
              in.getRevision().get(),
              toRevisionInfo(accountLoader, cd, in, repo, rw, false, changeInfo));
        }
      }
      return res;
    }
  }

  private Map<String, FetchInfo> makeFetchMap(ChangeData cd, PatchSet in)
      throws PermissionBackendException, IOException {
    Map<String, FetchInfo> r = new LinkedHashMap<>();
    for (Extension<DownloadScheme> e : downloadSchemes) {
      String schemeName = e.getExportName();
      DownloadScheme scheme = e.getProvider().get();
      if (!scheme.isEnabled()
          || (scheme.isAuthRequired() && !userProvider.get().isIdentifiedUser())) {
        continue;
      }
      if (!scheme.isAuthSupported() && !isWorldReadable(cd)) {
        continue;
      }

      String projectName = cd.project().get();
      String url = scheme.getUrl(projectName);
      String refName = in.getRefName();
      FetchInfo fetchInfo = new FetchInfo(url, refName);
      r.put(schemeName, fetchInfo);

      if (has(DOWNLOAD_COMMANDS)) {
        DownloadCommandsJson.populateFetchMap(
            scheme, downloadCommands, projectName, refName, fetchInfo);
      }
    }

    return r;
  }

  private RevisionInfo toRevisionInfo(
      AccountLoader accountLoader,
      ChangeData cd,
      PatchSet in,
      @Nullable Repository repo,
      @Nullable RevWalk rw,
      boolean fillCommit,
      @Nullable ChangeInfo changeInfo)
      throws PatchListNotAvailableException, GpgException, IOException, PermissionBackendException {
    Change c = cd.change();
    RevisionInfo out = new RevisionInfo();
    out.isCurrent = in.getId().equals(c.currentPatchSetId());
    out._number = in.getId().get();
    out.ref = in.getRefName();
    out.created = in.getCreatedOn();
    out.uploader = accountLoader.get(in.getUploader());
    out.fetch = makeFetchMap(cd, in);
    out.kind = changeKindCache.getChangeKind(rw, repo != null ? repo.getConfig() : null, cd, in);
    out.description = in.getDescription();

    boolean setCommit = has(ALL_COMMITS) || (out.isCurrent && has(CURRENT_COMMIT));
    boolean addFooters = out.isCurrent && has(COMMIT_FOOTERS);
    if (setCommit || addFooters) {
      checkState(rw != null);
      checkState(repo != null);
      Project.NameKey project = c.getProject();
      String rev = in.getRevision().get();
      RevCommit commit = rw.parseCommit(ObjectId.fromString(rev));
      rw.parseBody(commit);
      if (setCommit) {
        out.commit = getCommitInfo(project, rw, commit, has(WEB_LINKS), fillCommit);
      }
      if (addFooters) {
        Ref ref = repo.exactRef(cd.change().getDest().branch());
        RevCommit mergeTip = null;
        if (ref != null) {
          mergeTip = rw.parseCommit(ref.getObjectId());
          rw.parseBody(mergeTip);
        }
        out.commitWithFooters =
            mergeUtilFactory
                .create(projectCache.get(project))
                .createCommitMessageOnSubmit(commit, mergeTip, cd.notes(), in.getId());
      }
    }

    if (has(ALL_FILES) || (out.isCurrent && has(CURRENT_FILES))) {
      out.files = fileInfoJson.toFileInfoMap(c, in);
      out.files.remove(Patch.COMMIT_MSG);
      out.files.remove(Patch.MERGE_LIST);
    }

    if (out.isCurrent && has(CURRENT_ACTIONS) && userProvider.get().isIdentifiedUser()) {
      actionJson.addRevisionActions(
          changeInfo,
          out,
          new RevisionResource(changeResourceFactory.create(cd.notes(), userProvider.get()), in));
    }

    if (gpgApi.isEnabled() && has(PUSH_CERTIFICATES)) {
      if (in.getPushCertificate() != null) {
        out.pushCertificate =
            gpgApi.checkPushCertificate(
                in.getPushCertificate(), userFactory.create(in.getUploader()));
      } else {
        out.pushCertificate = new PushCertificateInfo();
      }
    }

    return out;
  }

  private boolean has(ListChangesOption option) {
    return options.contains(option);
  }

  /**
   * @return {@link com.google.gerrit.server.permissions.PermissionBackend.ForChange} constructed
   *     from either an index-backed or a database-backed {@link ChangeData} depending on {@code
   *     lazyload}.
   */
  private PermissionBackend.ForChange permissionBackendForChange(
      PermissionBackend.WithUser withUser, ChangeData cd) {
    return lazyLoad
        ? withUser.change(cd)
        : withUser.indexedChange(cd, notesFactory.createFromIndexedChange(cd.change()));
  }

  private boolean isWorldReadable(ChangeData cd) throws PermissionBackendException, IOException {
    try {
      permissionBackendForChange(permissionBackend.user(anonymous), cd)
          .check(ChangePermission.READ);
    } catch (AuthException ae) {
      return false;
    }
    ProjectState projectState = projectCache.checkedGet(cd.project());
    if (projectState == null) {
      logger.atSevere().log("project state for project %s is null", cd.project());
      return false;
    }
    return projectState.statePermitsRead();
  }

  @Nullable
  private Repository openRepoIfNecessary(Project.NameKey project) throws IOException {
    if (has(ALL_COMMITS) || has(CURRENT_COMMIT) || has(COMMIT_FOOTERS)) {
      return repoManager.openRepository(project);
    }
    return null;
  }

  @Nullable
  private RevWalk newRevWalk(@Nullable Repository repo) {
    return repo != null ? new RevWalk(repo) : null;
  }

  private static boolean containsAnyOf(
      ImmutableSet<ListChangesOption> set, ImmutableSet<ListChangesOption> toFind) {
    return !Sets.intersection(toFind, set).isEmpty();
  }
}
