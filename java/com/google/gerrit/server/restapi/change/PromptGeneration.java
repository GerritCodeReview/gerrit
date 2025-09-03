package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.nio.file.Files;
import java.nio.file.Path;

public class PromptGeneration implements RestReadView<RevisionResource> {
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
      responseText = switch (command) {
        case "code-review" -> buildResponse(patchContent, "codeReviewPromptFileName");
        case "commit-message" -> buildResponse(patchContent, "commitMessagePromptFileName");
        case "patch-content" -> patchContent;
        default ->
            throw new IllegalArgumentException("command parameter accepts only: code-review, commit-message, patch-content");
      };
    }
    return Response.ok(responseText);
  }

  private String buildResponse(String patchContent, String cfgFileName) throws Exception {
    String responseText;
    String fileName = cfg.getString("aireview", null, cfgFileName);
    String fileContent = readFileContent(fileName);
    responseText = fileContent.replace("{{Patch}}", patchContent);
    return responseText;
  }

  public String readFileContent(String fileName) throws Exception {
    Path file = sitePaths.etc_dir.resolve(fileName);
    return Files.readString(file);
  }

}
