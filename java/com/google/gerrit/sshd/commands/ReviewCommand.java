// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.util.cli.Localizable.localizable;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.CharStreams;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.OptionUtil;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

@CommandMetaData(name = "review", description = "Apply reviews to one or more patch sets")
public class ReviewCommand extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected final CmdLineParser newCmdLineParser(Object options) {
    CmdLineParser parser = super.newCmdLineParser(options);
    optionMap.forEach((o, s) -> parser.addOption(s, o));
    return parser;
  }

  private final Set<PatchSet> patchSets = new HashSet<>();

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "{COMMIT | CHANGE,PATCHSET}",
      usage = "list of commits or patch sets to review")
  void addPatchSetId(String token) {
    try {
      PatchSet ps = psParser.parsePatchSet(token, projectState, branch);
      patchSets.add(ps);
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (StorageException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  @Option(
      name = "--project",
      aliases = "-p",
      usage = "project containing the specified patch set(s)")
  private ProjectState projectState;

  @Option(name = "--branch", aliases = "-b", usage = "branch containing the specified patch set(s)")
  private String branch;

  @Option(
      name = "--message",
      aliases = "-m",
      usage = "cover message to publish on change(s)",
      metaVar = "MESSAGE")
  private String changeComment;

  @Option(
      name = "--notify",
      aliases = "-n",
      usage = "Who to send email notifications to after the review is stored.",
      metaVar = "NOTIFYHANDLING")
  private NotifyHandling notify;

  @Option(name = "--abandon", usage = "abandon the specified change(s)")
  private boolean abandonChange;

  @Option(name = "--restore", usage = "restore the specified abandoned change(s)")
  private boolean restoreChange;

  @Option(name = "--rebase", usage = "rebase the specified change(s)")
  private boolean rebaseChange;

  @Option(name = "--move", usage = "move the specified change(s)", metaVar = "BRANCH")
  private String moveToBranch;

  @Option(name = "--submit", aliases = "-s", usage = "submit the specified patch set(s)")
  private boolean submitChange;

  @Option(name = "--json", aliases = "-j", usage = "read review input json from stdin")
  private boolean json;

  @Option(
      name = "--tag",
      aliases = "-t",
      usage = "applies a tag to the given review",
      metaVar = "TAG")
  private String changeTag;

  @Option(
      name = "--label",
      aliases = "-l",
      usage = "custom label(s) to assign",
      metaVar = "LABEL=VALUE")
  void addLabel(String token) {
    LabelVote v = LabelVote.parseWithEquals(token);
    LabelType.checkName(v.label()); // Disallow SUBM.
    customLabels.put(v.label(), v.value());
  }

  @Inject private ProjectCache projectCache;

  @Inject private AllProjectsName allProjects;

  @Inject private GerritApi gApi;

  @Inject private PatchSetParser psParser;

  private Map<Option, LabelSetter> optionMap;
  private Map<String, Short> customLabels;

  @Override
  protected void run() throws UnloggedFailure {
    if (abandonChange) {
      if (restoreChange) {
        throw die("abandon and restore actions are mutually exclusive");
      }
      if (submitChange) {
        throw die("abandon and submit actions are mutually exclusive");
      }
      if (rebaseChange) {
        throw die("abandon and rebase actions are mutually exclusive");
      }
      if (moveToBranch != null) {
        throw die("abandon and move actions are mutually exclusive");
      }
    }
    if (json) {
      if (restoreChange) {
        throw die("json and restore actions are mutually exclusive");
      }
      if (submitChange) {
        throw die("json and submit actions are mutually exclusive");
      }
      if (abandonChange) {
        throw die("json and abandon actions are mutually exclusive");
      }
      if (changeComment != null) {
        throw die("json and message are mutually exclusive");
      }
      if (rebaseChange) {
        throw die("json and rebase actions are mutually exclusive");
      }
      if (moveToBranch != null) {
        throw die("json and move actions are mutually exclusive");
      }
      if (changeTag != null) {
        throw die("json and tag actions are mutually exclusive");
      }
    }
    if (rebaseChange) {
      if (submitChange) {
        throw die("rebase and submit actions are mutually exclusive");
      }
    }

    boolean ok = true;
    ReviewInput input = null;
    if (json) {
      input = reviewFromJson();
    }

    for (PatchSet patchSet : patchSets) {
      try {
        if (input != null) {
          applyReview(patchSet, input);
        } else {
          reviewPatchSet(patchSet);
        }
      } catch (RestApiException | UnloggedFailure e) {
        ok = false;
        writeError("error", e.getMessage() + "\n");
      } catch (NoSuchChangeException e) {
        ok = false;
        writeError("error", "no such change " + patchSet.id().changeId().get());
      } catch (Exception e) {
        ok = false;
        writeError("fatal", "internal server error while reviewing " + patchSet.id() + "\n");
        logger.atSevere().withCause(e).log("internal error while reviewing %s", patchSet.id());
      }
    }

    if (!ok) {
      throw die("one or more reviews failed; review output above");
    }
  }

  private void applyReview(PatchSet patchSet, ReviewInput review) throws RestApiException {
    gApi.changes()
        .id(patchSet.id().changeId().get())
        .revision(patchSet.commitId().name())
        .review(review);
  }

  private ReviewInput reviewFromJson() throws UnloggedFailure {
    try (InputStreamReader r = new InputStreamReader(in, UTF_8)) {
      return OutputFormat.JSON.newGson().fromJson(CharStreams.toString(r), ReviewInput.class);
    } catch (IOException | JsonSyntaxException e) {
      writeError("error", e.getMessage() + '\n');
      throw die("internal error while reading review input");
    }
  }

  private void reviewPatchSet(PatchSet patchSet) throws Exception {

    ReviewInput review = new ReviewInput();
    review.message = Strings.emptyToNull(changeComment);
    review.tag = Strings.emptyToNull(changeTag);
    review.notify = notify;
    review.labels = new TreeMap<>();
    review.drafts = ReviewInput.DraftHandling.PUBLISH;
    for (LabelSetter setter : optionMap.values()) {
      setter.getValue().ifPresent(v -> review.labels.put(setter.getLabelName(), v));
    }
    review.labels.putAll(customLabels);

    // We don't need to add the review comment when abandoning/restoring.
    if (abandonChange || restoreChange || moveToBranch != null) {
      review.message = null;
    }

    try {
      if (abandonChange) {
        AbandonInput input = new AbandonInput();
        input.message = Strings.emptyToNull(changeComment);
        applyReview(patchSet, review);
        changeApi(patchSet).abandon(input);
      } else if (restoreChange) {
        RestoreInput input = new RestoreInput();
        input.message = Strings.emptyToNull(changeComment);
        changeApi(patchSet).restore(input);
        applyReview(patchSet, review);
      } else {
        applyReview(patchSet, review);
      }

      if (moveToBranch != null) {
        MoveInput moveInput = new MoveInput();
        moveInput.destinationBranch = moveToBranch;
        moveInput.message = Strings.emptyToNull(changeComment);
        changeApi(patchSet).move(moveInput);
      }

      if (rebaseChange) {
        revisionApi(patchSet).rebase();
      }

      if (submitChange) {
        revisionApi(patchSet).submit();
      }

    } catch (IllegalStateException | RestApiException e) {
      throw die(e);
    }
  }

  private ChangeApi changeApi(PatchSet patchSet) throws RestApiException {
    return gApi.changes().id(patchSet.id().changeId().get());
  }

  private RevisionApi revisionApi(PatchSet patchSet) throws RestApiException {
    return changeApi(patchSet).revision(patchSet.commitId().name());
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    optionMap = new LinkedHashMap<>();
    customLabels = new HashMap<>();

    ProjectState allProjectsState;
    try {
      allProjectsState = projectCache.getAllProjects();
    } catch (Exception e) {
      throw die("missing " + allProjects.get(), e);
    }

    for (LabelType type : allProjectsState.getLabelTypes().getLabelTypes()) {
      StringBuilder usage = new StringBuilder("score for ").append(type.getName()).append("\n");

      for (LabelValue v : type.getValues()) {
        usage.append(v.format()).append("\n");
      }

      optionMap.put(newApproveOption(type, usage.toString()), new LabelSetter(type));
    }

    super.parseCommandLine();
  }

  private static String asOptionName(LabelType type) {
    return "--" + type.getName().toLowerCase();
  }

  private static Option newApproveOption(LabelType type, String usage) {
    return OptionUtil.newOption(
        asOptionName(type),
        ImmutableList.of(),
        usage,
        "N",
        false,
        false,
        false,
        LabelHandler.class,
        ImmutableList.of(),
        ImmutableList.of());
  }

  private static class LabelSetter implements Setter<Short> {
    private final LabelType type;
    private Optional<Short> value;

    LabelSetter(LabelType type) {
      this.type = requireNonNull(type);
      this.value = Optional.empty();
    }

    Optional<Short> getValue() {
      return value;
    }

    LabelType getLabelType() {
      return type;
    }

    String getLabelName() {
      return type.getName();
    }

    @Override
    public void addValue(Short value) {
      this.value = Optional.of(value);
    }

    @Override
    public Class<Short> getType() {
      return Short.class;
    }

    @Override
    public boolean isMultiValued() {
      return false;
    }

    @Override
    public FieldSetter asFieldSetter() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AnnotatedElement asAnnotatedElement() {
      throw new UnsupportedOperationException();
    }
  }

  public static class LabelHandler extends OneArgumentOptionHandler<Short> {
    private final LabelType type;

    public LabelHandler(
        org.kohsuke.args4j.CmdLineParser parser, OptionDef option, Setter<Short> setter) {
      super(parser, option, setter);
      this.type = ((LabelSetter) setter).getLabelType();
    }

    @Override
    protected Short parse(String token) throws NumberFormatException, CmdLineException {
      String argument = token;
      if (argument.startsWith("+")) {
        argument = argument.substring(1);
      }

      short value = Short.parseShort(argument);
      LabelValue min = type.getMin();
      LabelValue max = type.getMax();

      if (value < min.getValue() || value > max.getValue()) {
        String e =
            "\""
                + token
                + "\" must be in range "
                + min.formatValue()
                + ".."
                + max.formatValue()
                + " for \""
                + asOptionName(type)
                + "\"";
        throw new CmdLineException(owner, localizable(e));
      }
      return value;
    }
  }
}
