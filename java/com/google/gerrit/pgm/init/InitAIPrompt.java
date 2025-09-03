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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class InitAIPrompt implements InitStep {

  public static final String CODE_REVIEW = "code_review_prompt_template.txt";
  public static final String COMMIT_MSG = "commit_message_prompt_template.txt";

  private final SitePaths sitePaths;

  @Inject
  public InitAIPrompt(SitePaths sitePaths) {
    this.sitePaths = sitePaths;
  }

  @Override
  public void run() throws Exception {
    Path etcDir = sitePaths.resolve("etc");
    Files.createDirectories(etcDir);

    createFileIfMissing(etcDir.resolve(CODE_REVIEW), readPromptFile(CODE_REVIEW));
    createFileIfMissing(etcDir.resolve(COMMIT_MSG), readPromptFile(COMMIT_MSG));
  }

  private void createFileIfMissing(Path path, String content) throws IOException {
    if (!Files.exists(path)) {
      Files.writeString(path, content, StandardCharsets.UTF_8);
    }
  }

  private String readPromptFile(String fileName) throws IOException {
    try (InputStream in =
        getClass().getResourceAsStream("/com/google/gerrit/pgm/init/prompt/" + fileName)) {
      if (in == null) {
        throw new IOException("Resource file not found: " + fileName);
      }

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append("\n");
        }
        return sb.toString();
      }
    }
  }
}
