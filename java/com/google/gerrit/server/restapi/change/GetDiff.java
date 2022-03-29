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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.diff.DiffInfoCreator;
import com.google.gerrit.server.diff.DiffSide;
import com.google.gerrit.server.diff.DiffWebLinksProvider;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class GetDiff implements RestReadView<FileResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final Revisions revisions;
  private final WebLinks webLinks;
  private final Provider<CurrentUser> currentUser;

  @Option(name = "--base", metaVar = "REVISION")
  String base;

  /** 1-based index of the parent's position in the commit object. */
  @Option(name = "--parent", metaVar = "parent-number")
  int parentNum;

  @Deprecated
  @Option(name = "--ignore-whitespace")
  IgnoreWhitespace ignoreWhitespace;

  @Option(name = "--whitespace")
  Whitespace whitespace;

  // TODO(hiesel): Remove parameter when not used by callers (e.g. frontend) anymore.
  @Option(name = "--context", handler = ContextOptionHandler.class)
  int context;

  @Option(name = "--intraline")
  boolean intraline;

  @Inject
  GetDiff(
      ProjectCache projectCache,
      PatchScriptFactory.Factory patchScriptFactoryFactory,
      Revisions revisions,
      WebLinks webLinks,
      Provider<CurrentUser> currentUser) {
    this.projectCache = projectCache;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.revisions = revisions;
    this.webLinks = webLinks;
    this.currentUser = currentUser;
  }

  @Override
  public Response<DiffInfo> apply(FileResource resource)
      throws BadRequestException, ResourceConflictException, ResourceNotFoundException,
          AuthException, InvalidChangeOperationException, IOException, PermissionBackendException {
    DiffPreferencesInfo prefs = new DiffPreferencesInfo();
    if (whitespace != null) {
      prefs.ignoreWhitespace = whitespace;
    } else if (ignoreWhitespace != null) {
      prefs.ignoreWhitespace = ignoreWhitespace.whitespace;
    } else {
      prefs.ignoreWhitespace = Whitespace.IGNORE_LEADING_AND_TRAILING;
    }
    prefs.intralineDifference = intraline;
    logger.atFine().log(
        "diff preferences: ignoreWhitespace = %s, intralineDifference = %s",
        prefs.ignoreWhitespace, prefs.intralineDifference);

    PatchScriptFactory psf;
    PatchSet basePatchSet = null;
    PatchSet.Id pId = resource.getPatchKey().patchSetId();
    String fileName = resource.getPatchKey().fileName();
    logger.atFine().log(
        "patchSetId = %d, fileName = %s, base = %s, parentNum = %d",
        pId.get(), fileName, base, parentNum);
    ChangeNotes notes = resource.getRevision().getNotes();
    if (base != null) {
      RevisionResource baseResource =
          revisions.parse(resource.getRevision().getChangeResource(), IdString.fromDecoded(base));
      basePatchSet = baseResource.getPatchSet();
      if (basePatchSet.id().get() == 0) {
        throw new BadRequestException("edit not allowed as base");
      }
      psf =
          patchScriptFactoryFactory.create(
              notes, fileName, basePatchSet.id(), pId, prefs, currentUser.get());
    } else if (parentNum > 0) {
      psf =
          patchScriptFactoryFactory.create(
              notes, fileName, parentNum, pId, prefs, currentUser.get());
    } else {
      psf = patchScriptFactoryFactory.create(notes, fileName, null, pId, prefs, currentUser.get());
    }

    try {
      PatchScript ps = psf.call();
      Project.NameKey projectName = resource.getRevision().getChange().getProject();
      ProjectState state = projectCache.get(projectName).orElseThrow(illegalState(projectName));
      DiffSide sideA =
          DiffSide.create(
              ps.getFileInfoA(),
              MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
              DiffSide.Type.SIDE_A);
      DiffSide sideB = DiffSide.create(ps.getFileInfoB(), ps.getNewName(), DiffSide.Type.SIDE_B);
      DiffWebLinksProvider webLinksProvider =
          new DiffWebLinksProviderImpl(sideA, sideB, projectName, basePatchSet, webLinks, resource);
      DiffInfoCreator diffInfoCreator = new DiffInfoCreator(state, webLinksProvider, intraline);
      DiffInfo result = diffInfoCreator.create(ps, sideA, sideB);

      Response<DiffInfo> r = Response.ok(result);
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } catch (LargeObjectException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
  }

  private static class DiffWebLinksProviderImpl implements DiffWebLinksProvider {

    private final WebLinks webLinks;
    private final Project.NameKey projectName;
    private final DiffSide sideA;
    private final DiffSide sideB;
    private final String revA;
    private final String revB;
    private final String hashA;
    private final String hashB;
    private final FileResource resource;
    @Nullable private final PatchSet basePatchSet;

    DiffWebLinksProviderImpl(
        DiffSide sideA,
        DiffSide sideB,
        Project.NameKey projectName,
        @Nullable PatchSet basePatchSet,
        WebLinks webLinks,
        FileResource resource) {
      this.projectName = projectName;
      this.webLinks = webLinks;
      this.basePatchSet = basePatchSet;
      this.resource = resource;
      this.sideA = sideA;
      this.sideB = sideB;

      revA = basePatchSet != null ? basePatchSet.refName() : sideA.fileInfo().commitId;
      hashA = sideA.fileInfo().commitId;

      RevisionResource revision = resource.getRevision();
      revB =
          revision
              .getEdit()
              .map(edit -> edit.getRefName())
              .orElseGet(() -> revision.getPatchSet().refName());
      hashB = sideB.fileInfo().commitId;

      logger.atFine().log("revA = %s, hashA = %s, revB = %s, hashB = %s", revA, hashA, revB, hashB);
    }

    @Override
    public ImmutableList<DiffWebLinkInfo> getDiffLinks() {
      return webLinks.getDiffLinks(
          projectName.get(),
          resource.getPatchKey().patchSetId().changeId().get(),
          basePatchSet != null ? basePatchSet.id().get() : null,
          revA,
          sideA.fileName(),
          resource.getPatchKey().patchSetId().get(),
          revB,
          sideB.fileName());
    }

    @Override
    public ImmutableList<WebLinkInfo> getEditWebLinks() {
      return webLinks.getEditLinks(projectName.get(), revB, sideB.fileName());
    }

    @Override
    public ImmutableList<WebLinkInfo> getFileWebLinks(DiffSide.Type type) {
      String rev = getSideRev(type);
      String hash = getSideHash(type);
      DiffSide side = getDiffSide(type);
      return webLinks.getFileLinks(projectName.get(), rev, hash, side.fileName());
    }

    private String getSideRev(DiffSide.Type sideType) {
      return DiffSide.Type.SIDE_A == sideType ? revA : revB;
    }

    private String getSideHash(DiffSide.Type sideType) {
      return DiffSide.Type.SIDE_A == sideType ? hashA : hashB;
    }

    private DiffSide getDiffSide(DiffSide.Type sideType) {
      return DiffSide.Type.SIDE_A == sideType ? sideA : sideB;
    }
  }

  public GetDiff setBase(String base) {
    this.base = base;
    return this;
  }

  public GetDiff setParent(int parentNum) {
    this.parentNum = parentNum;
    return this;
  }

  public GetDiff setIntraline(boolean intraline) {
    this.intraline = intraline;
    return this;
  }

  public GetDiff setWhitespace(Whitespace whitespace) {
    this.whitespace = whitespace;
    return this;
  }

  @Deprecated
  enum IgnoreWhitespace {
    NONE(DiffPreferencesInfo.Whitespace.IGNORE_NONE),
    TRAILING(DiffPreferencesInfo.Whitespace.IGNORE_TRAILING),
    CHANGED(DiffPreferencesInfo.Whitespace.IGNORE_LEADING_AND_TRAILING),
    ALL(DiffPreferencesInfo.Whitespace.IGNORE_ALL);

    private final DiffPreferencesInfo.Whitespace whitespace;

    IgnoreWhitespace(DiffPreferencesInfo.Whitespace whitespace) {
      this.whitespace = whitespace;
    }
  }

  // TODO(hiesel): Remove this class once clients don't send the context parameter anymore.
  public static class ContextOptionHandler extends OptionHandler<Short> {

    public ContextOptionHandler(CmdLineParser parser, OptionDef option, Setter<Short> setter) {
      super(parser, option, setter);
    }

    @Override
    public final int parseArguments(Parameters params) {
      // Return 1 to consume the context parameter.
      return 1;
    }

    @Override
    public final String getDefaultMetaVariable() {
      return "ignored";
    }
  }
}
