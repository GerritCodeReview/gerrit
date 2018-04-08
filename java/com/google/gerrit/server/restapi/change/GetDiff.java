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
import com.google.gerrit.git.LargeObjectException;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class GetDiff implements RestReadView<FileResource> {
  private static final ImmutableMap<Patch.ChangeType, ChangeType> CHANGE_TYPE =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<Patch.ChangeType, ChangeType>()
              .put(Patch.ChangeType.ADDED, ChangeType.ADDED)
              .put(Patch.ChangeType.MODIFIED, ChangeType.MODIFIED)
              .put(Patch.ChangeType.DELETED, ChangeType.DELETED)
              .put(Patch.ChangeType.RENAMED, ChangeType.RENAMED)
              .put(Patch.ChangeType.COPIED, ChangeType.COPIED)
              .put(Patch.ChangeType.REWRITE, ChangeType.REWRITE)
              .build());

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

  @Override
  public Response<DiffInfo> apply(FileResource resource)
      throws ResourceConflictException, ResourceNotFoundException, OrmException, AuthException,
          InvalidChangeOperationException, IOException, PermissionBackendException {
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

    PatchScriptFactory psf;
    PatchSet basePatchSet = null;
    PatchSet.Id pId = resource.getPatchKey().getParentKey();
    String fileName = resource.getPatchKey().getFileName();
    ChangeNotes notes = resource.getRevision().getNotes();
    if (base != null) {
      RevisionResource baseResource =
          revisions.parse(resource.getRevision().getChangeResource(), IdString.fromDecoded(base));
      basePatchSet = baseResource.getPatchSet();
      psf = patchScriptFactoryFactory.create(notes, fileName, basePatchSet.getId(), pId, prefs);
    } else if (parentNum > 0) {
      psf = patchScriptFactoryFactory.create(notes, fileName, parentNum - 1, pId, prefs);
    } else {
      psf = patchScriptFactoryFactory.create(notes, fileName, null, pId, prefs);
    }

    try {
      psf.setLoadHistory(false);
      psf.setLoadComments(context != DiffPreferencesInfo.WHOLE_FILE_CONTEXT);
      PatchScript ps = psf.call();
      Content content = new Content(ps);
      Set<Edit> editsDueToRebase = ps.getEditsDueToRebase();
      for (Edit edit : ps.getEdits()) {
        if (edit.getType() == Edit.Type.EMPTY) {
          continue;
        }
        content.addCommon(edit.getBeginA());

        checkState(
            content.nextA == edit.getBeginA(),
            "nextA = %s; want %s",
            content.nextA,
            edit.getBeginA());
        checkState(
            content.nextB == edit.getBeginB(),
            "nextB = %s; want %s",
            content.nextB,
            edit.getBeginB());
        switch (edit.getType()) {
          case DELETE:
          case INSERT:
          case REPLACE:
            List<Edit> internalEdit =
                edit instanceof ReplaceEdit ? ((ReplaceEdit) edit).getInternalEdits() : null;
            boolean dueToRebase = editsDueToRebase.contains(edit);
            content.addDiff(edit.getEndA(), edit.getEndB(), internalEdit, dueToRebase);
            break;
          case EMPTY:
          default:
            throw new IllegalStateException();
        }
      }
      content.addCommon(ps.getA().size());

      ProjectState state = projectCache.get(resource.getRevision().getChange().getProject());

      DiffInfo result = new DiffInfo();
      String revA = basePatchSet != null ? basePatchSet.getRefName() : content.commitIdA;
      String revB =
          resource.getRevision().getEdit().isPresent()
              ? resource.getRevision().getEdit().get().getRefName()
              : resource.getRevision().getPatchSet().getRefName();

      List<DiffWebLinkInfo> links =
          webLinks.getDiffLinks(
              state.getName(),
              resource.getPatchKey().getParentKey().getParentKey().get(),
              basePatchSet != null ? basePatchSet.getId().get() : null,
              revA,
              MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
              resource.getPatchKey().getParentKey().get(),
              revB,
              ps.getNewName());
      result.webLinks = links.isEmpty() ? null : links;

      if (!webLinksOnly) {
        if (ps.isBinary()) {
          result.binary = true;
        }
        if (ps.getDisplayMethodA() != DisplayMethod.NONE) {
          result.metaA = new FileMeta();
          result.metaA.name = MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName());
          result.metaA.contentType =
              FileContentUtil.resolveContentType(
                  state, result.metaA.name, ps.getFileModeA(), ps.getMimeTypeA());
          result.metaA.lines = ps.getA().size();
          result.metaA.webLinks = getFileWebLinks(state.getProject(), revA, result.metaA.name);
          result.metaA.commitId = content.commitIdA;
        }

        if (ps.getDisplayMethodB() != DisplayMethod.NONE) {
          result.metaB = new FileMeta();
          result.metaB.name = ps.getNewName();
          result.metaB.contentType =
              FileContentUtil.resolveContentType(
                  state, result.metaB.name, ps.getFileModeB(), ps.getMimeTypeB());
          result.metaB.lines = ps.getB().size();
          result.metaB.webLinks = getFileWebLinks(state.getProject(), revB, result.metaB.name);
          result.metaB.commitId = content.commitIdB;
        }

        if (intraline) {
          if (ps.hasIntralineTimeout()) {
            result.intralineStatus = IntraLineStatus.TIMEOUT;
          } else if (ps.hasIntralineFailure()) {
            result.intralineStatus = IntraLineStatus.FAILURE;
          } else {
            result.intralineStatus = IntraLineStatus.OK;
          }
        }

        result.changeType = CHANGE_TYPE.get(ps.getChangeType());
        if (result.changeType == null) {
          throw new IllegalStateException("unknown change type: " + ps.getChangeType());
        }

        if (ps.getPatchHeader().size() > 0) {
          result.diffHeader = ps.getPatchHeader();
        }
        result.content = content.lines;
      }

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

  private List<WebLinkInfo> getFileWebLinks(Project project, String rev, String file) {
    List<WebLinkInfo> links = webLinks.getFileLinks(project.getName(), rev, file);
    return links.isEmpty() ? null : links;
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

  private static class Content {
    final List<ContentEntry> lines;
    final SparseFileContent fileA;
    final SparseFileContent fileB;
    final boolean ignoreWS;
    final String commitIdA;
    final String commitIdB;

    int nextA;
    int nextB;

    Content(PatchScript ps) {
      lines = Lists.newArrayListWithExpectedSize(ps.getEdits().size() + 2);
      fileA = ps.getA();
      fileB = ps.getB();
      ignoreWS = ps.isIgnoreWhitespace();
      commitIdA = ps.getCommitIdA();
      commitIdB = ps.getCommitIdB();
    }

    void addCommon(int end) {
      end = Math.min(end, fileA.size());
      if (nextA >= end) {
        return;
      }

      while (nextA < end) {
        if (!fileA.contains(nextA)) {
          int endRegion = Math.min(end, nextA == 0 ? fileA.first() : fileA.next(nextA - 1));
          int len = endRegion - nextA;
          entry().skip = len;
          nextA = endRegion;
          nextB += len;
          continue;
        }

        ContentEntry e = null;
        for (int i = nextA; i == nextA && i < end; i = fileA.next(i), nextA++, nextB++) {
          if (ignoreWS && fileB.contains(nextB)) {
            if (e == null || e.common == null) {
              e = entry();
              e.a = Lists.newArrayListWithCapacity(end - nextA);
              e.b = Lists.newArrayListWithCapacity(end - nextA);
              e.common = true;
            }
            e.a.add(fileA.get(nextA));
            e.b.add(fileB.get(nextB));
          } else {
            if (e == null || e.common != null) {
              e = entry();
              e.ab = Lists.newArrayListWithCapacity(end - nextA);
            }
            e.ab.add(fileA.get(nextA));
          }
        }
      }
    }

    void addDiff(int endA, int endB, List<Edit> internalEdit, boolean dueToRebase) {
      int lenA = endA - nextA;
      int lenB = endB - nextB;
      checkState(lenA > 0 || lenB > 0);

      ContentEntry e = entry();
      if (lenA > 0) {
        e.a = Lists.newArrayListWithCapacity(lenA);
        for (; nextA < endA; nextA++) {
          e.a.add(fileA.get(nextA));
        }
      }
      if (lenB > 0) {
        e.b = Lists.newArrayListWithCapacity(lenB);
        for (; nextB < endB; nextB++) {
          e.b.add(fileB.get(nextB));
        }
      }
      if (internalEdit != null && !internalEdit.isEmpty()) {
        e.editA = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
        e.editB = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
        int lastA = 0;
        int lastB = 0;
        for (Edit edit : internalEdit) {
          if (edit.getBeginA() != edit.getEndA()) {
            e.editA.add(
                ImmutableList.of(edit.getBeginA() - lastA, edit.getEndA() - edit.getBeginA()));
            lastA = edit.getEndA();
          }
          if (edit.getBeginB() != edit.getEndB()) {
            e.editB.add(
                ImmutableList.of(edit.getBeginB() - lastB, edit.getEndB() - edit.getBeginB()));
            lastB = edit.getEndB();
          }
        }
      }
      e.dueToRebase = dueToRebase ? true : null;
    }

    private ContentEntry entry() {
      ContentEntry e = new ContentEntry();
      lines.add(e);
      return e;
    }
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
              String.format(
                  "\"%s\" is not a valid value for \"%s\"",
                  value, ((NamedOptionDef) option).name()));
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
