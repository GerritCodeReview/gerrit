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

import com.google.common.base.Objects;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.common.data.PatchScript.FileMode;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class GetDiff implements RestReadView<FileResource> {
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final Provider<Revisions> revisions;

  @Option(name = "--base", metaVar = "REVISION")
  String base;

  @Option(name = "--ignore-whitespace")
  IgnoreWhitespace ignoreWhitespace = IgnoreWhitespace.NONE;

  @Option(name = "--context", handler = ContextOptionHandler.class)
  short context = AccountDiffPreference.DEFAULT_CONTEXT;

  @Option(name = "--intraline")
  boolean intraline;

  @Inject
  GetDiff(PatchScriptFactory.Factory patchScriptFactoryFactory,
      Provider<Revisions> revisions) {
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.revisions = revisions;
  }

  @Override
  public Object apply(FileResource resource)
      throws OrmException, NoSuchChangeException, LargeObjectException, ResourceNotFoundException {
    PatchSet.Id basePatchSet = null;
    if (base != null) {
      RevisionResource baseResource = revisions.get().parse(
          resource.getRevision().getChangeResource(), IdString.fromDecoded(base));
      basePatchSet = baseResource.getPatchSet().getId();
    }
    AccountDiffPreference prefs = new AccountDiffPreference(new Account.Id(0));
    prefs.setIgnoreWhitespace(ignoreWhitespace.whitespace);
    prefs.setContext(context);
    prefs.setIntralineDifference(intraline);

    PatchScript ps = patchScriptFactoryFactory.create(
        resource.getRevision().getControl(),
        resource.getPatchKey().getFileName(),
        basePatchSet,
        resource.getPatchKey().getParentKey(),
        prefs)
          .call();

    Result result = new Result();
    if (ps.getDisplayMethodA() != DisplayMethod.NONE) {
      result.metaA = new FileMeta();
      result.metaA.name = Objects.firstNonNull(ps.getOldName(), ps.getNewName());
      result.metaA.setContentType(ps.getFileModeA(), ps.getMimeTypeA());
    }

    if (ps.getDisplayMethodB() != DisplayMethod.NONE) {
      result.metaB = new FileMeta();
      result.metaB.name = ps.getNewName();
      result.metaB.setContentType(ps.getFileModeB(), ps.getMimeTypeB());
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

    DiffContent content = new DiffContent(ps);
    result.content = content.getLines();

    Response<Result> r = Response.ok(result);
    if (resource.isCacheable()) {
      r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
    }
    return r;
  }

  static class Result {
    FileMeta metaA;
    FileMeta metaB;
    IntraLineStatus intralineStatus;
    ChangeType changeType;
    List<String> diffHeader;
    List<DiffContent.Entry> content;
  }

  static class FileMeta {
    String name;
    String contentType;
    String url;

    void setContentType(FileMode fileMode, String mimeType) {
      switch (fileMode) {
        case FILE:
          contentType = mimeType;
          break;
        case GITLINK:
          contentType = "x-git/gitlink";
          break;
        case SYMLINK:
          contentType = "x-git/symlink";
          break;
        default:
          throw new IllegalStateException("file mode: " + fileMode);
      }
    }
  }

  enum IntraLineStatus {
    OK,
    TIMEOUT,
    FAILURE;
  }

  enum IgnoreWhitespace {
    NONE(AccountDiffPreference.Whitespace.IGNORE_NONE),
    TRAILING(AccountDiffPreference.Whitespace.IGNORE_SPACE_AT_EOL),
    CHANGED(AccountDiffPreference.Whitespace.IGNORE_SPACE_CHANGE),
    ALL(AccountDiffPreference.Whitespace.IGNORE_ALL_SPACE);

    private final AccountDiffPreference.Whitespace whitespace;

    private IgnoreWhitespace(AccountDiffPreference.Whitespace whitespace) {
      this.whitespace = whitespace;
    }
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
        context = AccountDiffPreference.WHOLE_FILE_CONTEXT;
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
