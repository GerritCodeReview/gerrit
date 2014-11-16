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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.common.data.PatchScript.FileMode;
import com.google.gerrit.extensions.common.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GetDiff implements RestReadView<FileResource> {
  private final ProjectCache projectCache;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final Revisions revisions;
  private final WebLinks webLinks;

  @Option(name = "--base", metaVar = "REVISION")
  String base;

  @Option(name = "--ignore-whitespace")
  IgnoreWhitespace ignoreWhitespace = IgnoreWhitespace.NONE;

  @Option(name = "--context", handler = ContextOptionHandler.class)
  short context = DiffPreferencesInfo.DEFAULT_CONTEXT;

  @Option(name = "--intraline")
  boolean intraline;

  @Inject
  GetDiff(ProjectCache projectCache,
      PatchScriptFactory.Factory patchScriptFactoryFactory,
      Revisions revisions,
      WebLinks webLinks) {
    this.projectCache = projectCache;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.revisions = revisions;
    this.webLinks = webLinks;
  }

  @Override
  public Response<Result> apply(FileResource resource)
      throws ResourceConflictException, ResourceNotFoundException,
      OrmException, AuthException, InvalidChangeOperationException, IOException {
    PatchSet basePatchSet = null;
    if (base != null) {
      RevisionResource baseResource = revisions.parse(
          resource.getRevision().getChangeResource(), IdString.fromDecoded(base));
      basePatchSet = baseResource.getPatchSet();
    }
    DiffPreferencesInfo prefs = new DiffPreferencesInfo();
    prefs.ignoreWhitespace = ignoreWhitespace.whitespace;
    prefs.context = context;
    prefs.intralineDifference = intraline;

    try {
      PatchScriptFactory psf = patchScriptFactoryFactory.create(
          resource.getRevision().getControl(),
          resource.getPatchKey().getFileName(),
          basePatchSet != null ? basePatchSet.getId() : null,
          resource.getPatchKey().getParentKey(),
          prefs);
      psf.setLoadHistory(false);
      psf.setLoadComments(context != DiffPreferencesInfo.WHOLE_FILE_CONTEXT);
      PatchScript ps = psf.call();
      Content content = new Content(ps);
      for (Edit edit : ps.getEdits()) {
        if (edit.getType() == Edit.Type.EMPTY) {
          continue;
        }
        content.addCommon(edit.getBeginA());

        checkState(content.nextA == edit.getBeginA(),
            "nextA = %d; want %d", content.nextA, edit.getBeginA());
        checkState(content.nextB == edit.getBeginB(),
            "nextB = %d; want %d", content.nextB, edit.getBeginB());
        switch (edit.getType()) {
          case DELETE:
          case INSERT:
          case REPLACE:
            List<Edit> internalEdit = edit instanceof ReplaceEdit
              ? ((ReplaceEdit) edit).getInternalEdits()
              : null;
            content.addDiff(edit.getEndA(), edit.getEndB(), internalEdit);
            break;
          case EMPTY:
          default:
            throw new IllegalStateException();
        }
      }
      content.addCommon(ps.getA().size());

      ProjectState state =
          projectCache.get(resource.getRevision().getChange().getProject());

      Result result = new Result();
      if (ps.getDisplayMethodA() != DisplayMethod.NONE) {
        result.metaA = new FileMeta();
        result.metaA.name = MoreObjects.firstNonNull(ps.getOldName(),
            ps.getNewName());
        setContentType(result.metaA, state, ps.getFileModeA(), ps.getMimeTypeA());
        result.metaA.lines = ps.getA().size();

        // TODO referring to the parent commit by refs/changes/12/60012/1^1
        // will likely not work for inline edits
        String rev = basePatchSet != null
            ? basePatchSet.getRefName()
            : resource.getRevision().getPatchSet().getRefName() + "^1";
        result.metaA.webLinks =
            getFileWebLinks(state.getProject(), rev, result.metaA.name);
      }

      if (ps.getDisplayMethodB() != DisplayMethod.NONE) {
        result.metaB = new FileMeta();
        result.metaB.name = ps.getNewName();
        setContentType(result.metaB, state, ps.getFileModeB(), ps.getMimeTypeB());
        result.metaB.lines = ps.getB().size();
        String rev = resource.getRevision().getEdit().isPresent()
            ? resource.getRevision().getEdit().get().getRefName()
            : resource.getRevision().getPatchSet().getRefName();
        result.metaB.webLinks =
            getFileWebLinks(state.getProject(), rev, result.metaB.name);
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

      result.changeType = ps.getChangeType();
      if (ps.getPatchHeader().size() > 0) {
        result.diffHeader = ps.getPatchHeader();
      }
      result.content = content.lines;
      Response<Result> r = Response.ok(result);
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage());
    } catch (LargeObjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  private List<WebLinkInfo> getFileWebLinks(Project project, String rev,
      String file) {
    FluentIterable<WebLinkInfo> links =
        webLinks.getFileLinks(project.getName(), rev, file);
    return links.isEmpty() ? null : links.toList();
  }

  static class Result {
    FileMeta metaA;
    FileMeta metaB;
    IntraLineStatus intralineStatus;
    ChangeType changeType;
    List<String> diffHeader;
    List<ContentEntry> content;
  }

  static class FileMeta {
    String name;
    String contentType;
    Integer lines;
    List<WebLinkInfo> webLinks;
  }

  private void setContentType(FileMeta meta, ProjectState project,
      FileMode fileMode, String mimeType) {
    switch (fileMode) {
      case FILE:
        if (Patch.COMMIT_MSG.equals(meta.name)) {
          mimeType = "text/x-gerrit-commit-message";
        } else if (project != null) {
          for (ProjectState p : project.tree()) {
            String t = p.getConfig().getMimeTypes().getMimeType(meta.name);
            if (t != null) {
              mimeType = t;
              break;
            }
          }
        }
        meta.contentType = mimeType;
        break;
      case GITLINK:
        meta.contentType = "x-git/gitlink";
        break;
      case SYMLINK:
        meta.contentType = "x-git/symlink";
        break;
      default:
        throw new IllegalStateException("file mode: " + fileMode);
    }
  }

  enum IntraLineStatus {
    OK,
    TIMEOUT,
    FAILURE
  }

  private static class Content {
    final List<ContentEntry> lines;
    final SparseFileContent fileA;
    final SparseFileContent fileB;
    final boolean ignoreWS;

    int nextA;
    int nextB;

    Content(PatchScript ps) {
      lines = Lists.newArrayListWithExpectedSize(ps.getEdits().size() + 2);
      fileA = ps.getA();
      fileB = ps.getB();
      ignoreWS = ps.isIgnoreWhitespace();
    }

    void addCommon(int end) {
      end = Math.min(end, fileA.size());
      if (nextA >= end) {
        return;
      }

      while (nextA < end) {
        if (!fileA.contains(nextA)) {
          int endRegion = Math.min(
              end,
              nextA == 0 ? fileA.first() : fileA.next(nextA - 1));
          int len = endRegion - nextA;
          entry().skip = len;
          nextA = endRegion;
          nextB += len;
          continue;
        }

        ContentEntry e = null;
        for (int i = nextA;
            i == nextA && i < end;
            i = fileA.next(i), nextA++, nextB++) {
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

    void addDiff(int endA, int endB, List<Edit> internalEdit) {
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
            e.editA.add(ImmutableList.of(edit.getBeginA() - lastA, edit.getEndA() - edit.getBeginA()));
            lastA = edit.getEndA();
          }
          if (edit.getBeginB() != edit.getEndB()) {
            e.editB.add(ImmutableList.of(edit.getBeginB() - lastB, edit.getEndB() - edit.getBeginB()));
            lastB = edit.getEndB();
          }
        }
      }
    }

    private ContentEntry entry() {
      ContentEntry e = new ContentEntry();
      lines.add(e);
      return e;
    }
  }

  enum IgnoreWhitespace {
    NONE(DiffPreferencesInfo.Whitespace.IGNORE_NONE),
    TRAILING(DiffPreferencesInfo.Whitespace.IGNORE_SPACE_AT_EOL),
    CHANGED(DiffPreferencesInfo.Whitespace.IGNORE_SPACE_CHANGE),
    ALL(DiffPreferencesInfo.Whitespace.IGNORE_ALL_SPACE);

    private final DiffPreferencesInfo.Whitespace whitespace;

    private IgnoreWhitespace(DiffPreferencesInfo.Whitespace whitespace) {
      this.whitespace = whitespace;
    }
  }

  static final class ContentEntry {
    // Common lines to both sides.
    List<String> ab;
    // Lines of a.
    List<String> a;
    // Lines of b.
    List<String> b;

    // A list of changed sections of the corresponding line list.
    // Each entry is a character <offset, length> pair. The offset is from the
    // beginning of the first line in the list. Also, the offset includes an
    // implied trailing newline character for each line.
    List<List<Integer>> editA;
    List<List<Integer>> editB;

    // a and b are actually common with this whitespace ignore setting.
    Boolean common;

    // Number of lines to skip on both sides.
    Integer skip;
  }

  public static class ContextOptionHandler extends OptionHandler<Short> {
    public ContextOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<Short> setter) {
      super(parser, option, setter);
    }

    @Override
    public final int parseArguments(final Parameters params)
        throws CmdLineException {
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
          throw new CmdLineException(owner,
              String.format("\"%s\" is not a valid value for \"%s\"",
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
