// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,//
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.restapi.change.CommandType.CODE_REVIEW;
import static com.google.gerrit.server.restapi.change.CommandType.COMMIT_MESSAGE;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class PromptGeneration implements RestReadView<RevisionResource> {
  public static final String AI_REVIEW = "aireview";
  public static final String PATCH_PLACE_HOLDER = "{{Patch}}";
  private final Config cfg;
  private final SitePaths sitePaths;
  private final GetPatch getPatch;

  @Option(
      name = "--command",
      usage = "Change query expression for which it should be checked if the change matches.")
  public String command;

  @Inject
  PromptGeneration(@GerritServerConfig Config cfg, SitePaths sitePaths, GetPatch getPatch) {
    this.cfg = cfg;
    this.sitePaths = sitePaths;
    this.getPatch = getPatch;
  }

  @Override
  public Response<String> apply(RevisionResource revisionResource) throws Exception {
    String responseText = "";
    String patchContent = getPatch.apply(revisionResource).value().asString();
    if (!Strings.isNullOrEmpty(command)) {
      CommandType cmd = CommandType.fromString(command);
      responseText =
          switch (cmd) {
            case CODE_REVIEW -> buildResponse(patchContent, CODE_REVIEW.getConfigKey());
            case COMMIT_MESSAGE -> buildResponse(patchContent, COMMIT_MESSAGE.getConfigKey());
            case PATCH_CONTENT -> patchContent;
          };
    }
    return Response.ok(responseText);
  }

  private String buildResponse(String patchContent, String cfgFileName) throws Exception {
    String responseText;
    String fileName = cfg.getString(AI_REVIEW, null, cfgFileName);
    String fileContent = readFileContent(fileName);
    responseText = fileContent.replace(PATCH_PLACE_HOLDER, patchContent);
    return responseText;
  }

  public String readFileContent(String fileName) throws Exception {
    Path file = sitePaths.etc_dir.resolve(fileName);
    return Files.readString(file);
  }
}

/**
 * Enum representing the supported AI prompt command types.
 *
 * <p>Each command corresponds to a specific use case for generating an AI prompt, such as
 * requesting code review, improving a commit message, or simply returning the raw patch content.
 */
enum CommandType {
  /** Command for generating a prompt to request AI assistance in reviewing code. */
  CODE_REVIEW("code-review", "codeReviewPromptFileName"),

  /** Command for generating a prompt to improve a commit message. */
  COMMIT_MESSAGE("commit-message", "commitMessagePromptFileName"),

  /** Command for directly returning the raw patch content without template substitution. */
  PATCH_CONTENT("patch-content", null);

  /** The string value used in the .../ai_prompt API request (e.g., query parameter). */
  private final String value;

  /** The configuration key in {@code gerrit.config} for the template file. */
  private final String configKey;

  CommandType(String value, String configKey) {
    this.value = value;
    this.configKey = configKey;
  }

  /** Returns the command value as string value for this command. */
  public String getValue() {
    return value;
  }

  /** Returns the configuration key in {@code gerrit.config} for this command, */
  public String getConfigKey() {
    return configKey;
  }

  /**
   * Parses the given string into a {@link CommandType}.
   *
   * @return the matching {@link CommandType}
   */
  public static CommandType fromString(String value) {
    for (CommandType cmd : values()) {
      if (cmd.value.equalsIgnoreCase(value)) {
        return cmd;
      }
    }
    throw new IllegalArgumentException(
        "command parameter accepts only: code-review, commit-message, patch-content");
  }
}
