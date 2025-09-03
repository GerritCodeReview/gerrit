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

    // Create prompt files.
    createFileIfMissing(etcDir.resolve(CODE_REVIEW), readPromptFile(CODE_REVIEW));
    createFileIfMissing(etcDir.resolve(COMMIT_MSG), readPromptFile(COMMIT_MSG));
  }

  /**
   * Creates a file with the given content in /etc dir if it does not already exist.
   *
   * @param path Path to the file
   * @param content Initial content
   * @throws IOException if writing the file fails
   */
  private void createFileIfMissing(Path path, String content) throws IOException {
    if (!Files.exists(path)) {
      Files.writeString(path, content, StandardCharsets.UTF_8);
      System.out.println("Created file: " + path);
    }
  }

  /**
   * Reads the content of a resource file under src/main/resources/com/google/gerrit/pgm/init/prompt
   *
   * @param fileName The name of the file
   * @return File content as a String
   * @throws IOException if reading fails
   */
  public String readPromptFile(String fileName) throws IOException {
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
