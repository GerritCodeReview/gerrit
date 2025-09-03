// Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Files;
import org.kohsuke.args4j.Option;

public class GetPrompt implements RestReadView<RevisionResource> {
  public static final String PATCH_PLACE_HOLDER = "{{Patch}}";
  public static final String PROMPT_TEMPLATE_TXT = "_prompt_template.txt";
  private final SitePaths sitePaths;
  private final GetPatch getPatch;

  @Option(
      name = "--command",
      usage =
          "Type of AI prompt to generate, valid values are: CODE_REVIEW, COMMIT_MESSAGE,"
              + " PATCH_CONTENT")
  public String command;

  @Inject
  GetPrompt(SitePaths sitePaths, GetPatch getPatch) {
    this.sitePaths = sitePaths;
    this.getPatch = getPatch;
  }

  @Override
  public Response<String> apply(RevisionResource revisionResource) throws Exception {
    String patchContent = getPatch.apply(revisionResource).value().asString();
    if (Strings.isNullOrEmpty(command)) {
      throw new BadRequestException("Missing required parameter: command");
    }
    CommandType cmd = CommandType.valueOf(command);
    String responseText =
        switch (cmd) {
          case CODE_REVIEW ->
              buildResponse(patchContent, CommandType.CODE_REVIEW + PROMPT_TEMPLATE_TXT);
          case COMMIT_MESSAGE ->
              buildResponse(patchContent, CommandType.COMMIT_MESSAGE + PROMPT_TEMPLATE_TXT);
          case PATCH_CONTENT -> patchContent;
        };
    return Response.ok(responseText);
  }

  private String buildResponse(String patchContent, String fileName) throws Exception {
    String responseText;
    String fileContent = readFileContent(fileName);
    responseText = fileContent.replace(PATCH_PLACE_HOLDER, patchContent);
    return responseText;
  }

  public String readFileContent(String fileName) throws Exception {
    return Files.readString(sitePaths.etc_dir.resolve(fileName));
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
  CODE_REVIEW,

  /** Command for generating a prompt to improve a commit message. */
  COMMIT_MESSAGE,

  /** Command for directly returning the raw patch content without template substitution. */
  PATCH_CONTENT
}
