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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.util.cli.Localizable.localizable;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.extensions.common.DiffInfo.FileMeta;
import com.google.gerrit.extensions.common.DiffInfo.IntraLineStatus;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.diff.DiffCalculator;
import com.google.gerrit.server.diff.DiffCalculator.DiffContent;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.diff.Edit;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class GetDiff implements RestReadView<FileResource> {

  private static class WebLinksUtils implements DiffCalculator.WebLinksUtils {
    static WebLinksUtils create(FileResource resource, PatchSet basePatchSet, WebLinks webLinks, DiffContent.DiffFileInfo fileA, DiffContent.DiffFileInfo fileB, ProjectState state) {
      String revA = basePatchSet != null ? basePatchSet.refName() : fileA.psFileInfo.getCommitId();
      String revB =
          resource.getRevision().getEdit().isPresent()
              ? resource.getRevision().getEdit().get().getRefName()
              : resource.getRevision().getPatchSet().refName();

      return new WebLinksUtils(webLinks, fileA, fileB, state, basePatchSet != null ? basePatchSet.id() : null, resource.getPatchKey().patchSetId(), revA, revB);
    }

    private String revA;
    private String revB;

    private DiffContent.DiffFileInfo fileA;
    private DiffContent.DiffFileInfo fileB;
    private WebLinks webLinks;
    private Project project;
    private ProjectState state;
    private PatchSet.Id patchSetIdA;
    private PatchSet.Id patchSetIdB;

    WebLinksUtils(WebLinks webLinks, DiffContent.DiffFileInfo fileA, DiffContent.DiffFileInfo fileB, ProjectState state, PatchSet.Id patchSetIdA, PatchSet.Id patchSetIdB, String revA, String revB) {
      this.webLinks = webLinks;
      this.fileA = fileA;
      this.fileB = fileB;
      this.state = state;
      this.project = state.getProject();
      this.patchSetIdA = patchSetIdA;
      this.patchSetIdB = patchSetIdB;
      this.revA = revA;
      this.revB = revB;

    }

    @Override
    public ImmutableList<DiffWebLinkInfo> createDiffWebLinks() {
      return webLinks.getDiffLinks(
              state.getName(),
              patchSetIdB.changeId().get(),
              patchSetIdA != null ? patchSetIdA.get() : null,
              revA,
              fileA.name,
              patchSetIdB.get(),
              revB,
              fileB.name);
    }

    @Override
    public List<WebLinkInfo> getFileAWebLinks() {
      return getFileWebLinks(fileA, revA);
    }

    @Override
    public List<WebLinkInfo> getFileBWebLinks() {
      return getFileWebLinks(fileB, revB);
    }

    private List<WebLinkInfo> getFileWebLinks(DiffContent.DiffFileInfo file, String rev) {
      ImmutableList<WebLinkInfo> links = webLinks.getFileLinks(project.getName(), rev, file.name);
      return links.isEmpty() ? null : links;
    }
  }

  private final ProjectCache projectCache;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final Revisions revisions;
  private final WebLinks webLinks;

  @Option(name = "--base", metaVar = "REVISION")
  String base;

  @Option(name = "--parent", metaVar = "parent-number")
  int parentNum;

  @Deprecated
  @Option(name = "--ignore-whitespace")
  IgnoreWhitespace ignoreWhitespace;

  @Option(name = "--whitespace")
  Whitespace whitespace;

  @Option(name = "--context", handler = ContextOptionHandler.class)
  int context = DiffPreferencesInfo.DEFAULT_CONTEXT;

  @Option(name = "--intraline")
  boolean intraline;

  @Option(name = "--weblinks-only")
  boolean webLinksOnly;

  @Inject
  GetDiff(
      ProjectCache projectCache,
      PatchScriptFactory.Factory patchScriptFactoryFactory,
      Revisions revisions,
      WebLinks webLinks) {
    this.projectCache = projectCache;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.revisions = revisions;
    this.webLinks = webLinks;
  }

  private DiffPreferencesInfo getDiffPreferncesInfo() {
    DiffPreferencesInfo prefs = new DiffPreferencesInfo();
    if (whitespace != null) {
      prefs.ignoreWhitespace = whitespace;
    } else if (ignoreWhitespace != null) {
      prefs.ignoreWhitespace = ignoreWhitespace.whitespace;
    } else {
      prefs.ignoreWhitespace = Whitespace.IGNORE_LEADING_AND_TRAILING;
    }
    prefs.context = context;
    prefs.intralineDifference = intraline;
    return prefs;
  }

  private WebLinksUtils createWebLinksUtils(FileResource resource, PatchSet basePatchSet, DiffContent.DiffFileInfo fileA, DiffContent.DiffFileInfo fileB, ProjectState state) {
    return WebLinksUtils.create(resource, basePatchSet, webLinks, fileA, fileB, state);

  }



  @Override
  public Response<DiffInfo> apply(FileResource resource)
      throws ResourceConflictException, ResourceNotFoundException, AuthException,
          InvalidChangeOperationException, IOException, PermissionBackendException {
    DiffPreferencesInfo prefs = getDiffPreferncesInfo();

    PatchScriptFactory psf;
    PatchSet basePatchSet = null;
    PatchSet.Id pId = resource.getPatchKey().patchSetId();
    String fileName = resource.getPatchKey().fileName();
    ChangeNotes notes = resource.getRevision().getNotes();
    if (base != null) {
      RevisionResource baseResource =
          revisions.parse(resource.getRevision().getChangeResource(), IdString.fromDecoded(base));
      basePatchSet = baseResource.getPatchSet();
      psf = patchScriptFactoryFactory.create(notes, fileName, basePatchSet.id(), pId, prefs);
    } else if (parentNum > 0) {
      psf = patchScriptFactoryFactory.create(notes, fileName, parentNum - 1, pId, prefs);
    } else {
      psf = patchScriptFactoryFactory.create(notes, fileName, null, pId, prefs);
    }

    try {
      psf.setLoadHistory(false);
      psf.setLoadComments(context != DiffPreferencesInfo.WHOLE_FILE_CONTEXT);
      PatchScript ps = psf.call();

      DiffCalculator diffCalculator = new DiffCalculator();

      ProjectState state = projectCache.get(resource.getRevision().getChange().getProject());

      String fileNameA = MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName());
      String fileNameB = ps.getNewName();

      DiffContent.DiffFileInfo fileA = new DiffContent.DiffFileInfo(ps.getFileA(), fileNameA);
      DiffContent.DiffFileInfo fileB = new DiffContent.DiffFileInfo(ps.getFileB(), fileNameB);
      WebLinksUtils webLinksUtils = createWebLinksUtils(resource, basePatchSet, fileA, fileB, state);


      DiffInfo result = diffCalculator.createDiffInfo(fileA, fileB, webLinksUtils, ps, state, webLinksOnly);

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

  public GetDiff setBase(String base) {
    this.base = base;
    return this;
  }

  public GetDiff setParent(int parentNum) {
    this.parentNum = parentNum;
    return this;
  }

  public GetDiff setContext(int context) {
    this.context = context;
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

  public static class ContextOptionHandler extends OptionHandler<Short> {
    public ContextOptionHandler(CmdLineParser parser, OptionDef option, Setter<Short> setter) {
      super(parser, option, setter);
    }

    @Override
    public final int parseArguments(Parameters params) throws CmdLineException {
      final String value = params.getParameter(0);
      short context;
      if ("all".equalsIgnoreCase(value)) {
        context = DiffPreferencesInfo.WHOLE_FILE_CONTEXT;
      } else {
        try {
          context = Short.parseShort(value, 10);
          if (context < 0) {
            throw new NumberFormatException();
          }
        } catch (NumberFormatException e) {
          throw new CmdLineException(
              owner,
              localizable("\"%s\" is not a valid value for \"%s\""),
              value,
              ((NamedOptionDef) option).name());
        }
      }
      setter.addValue(context);
      return 1;
    }

    @Override
    public final String getDefaultMetaVariable() {
      return "ALL|# LINES";
    }
  }
}
