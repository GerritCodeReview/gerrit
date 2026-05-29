// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.util;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class MarkdownImagesUtilTest {

  private static final String IMAGE_DATA =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
  private static final String MARKDOWN_IMAGE = "![alt text](" + IMAGE_DATA + ")";

  @Test
  public void stripImages_featureDisabled() {
    MarkdownImagesUtil util = new MarkdownImagesUtil(new Config());
    String comment = "Hello " + MARKDOWN_IMAGE + " world";
    assertThat(util.stripImages(comment)).isEqualTo(comment);
  }

  @Test
  public void replaceImages_featureDisabled() {
    MarkdownImagesUtil util = new MarkdownImagesUtil(new Config());
    String comment = "Hello " + MARKDOWN_IMAGE + " world";
    assertThat(util.replaceImagesWithPlaceholder(comment)).isEqualTo(comment);
  }

  @Test
  public void stripImages_featureEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    assertThat(util.stripImages("Hello " + MARKDOWN_IMAGE + " world")).isEqualTo("Hello   world");
    assertThat(util.stripImages(MARKDOWN_IMAGE)).isEqualTo(" ");
    assertThat(util.stripImages("No image here")).isEqualTo("No image here");
    assertThat(util.stripImages(null)).isNull();
    assertThat(util.stripImages("")).isEmpty();
    assertThat(util.stripImages(" " + MARKDOWN_IMAGE + " ")).isEqualTo("   ");
    assertThat(util.stripImages(MARKDOWN_IMAGE + " " + MARKDOWN_IMAGE).trim()).isEqualTo("");
    assertThat(util.stripImages(MARKDOWN_IMAGE + " at the beginning"))
        .isEqualTo("  at the beginning");
    assertThat(util.stripImages("at the end " + MARKDOWN_IMAGE)).isEqualTo("at the end  ");
  }

  @Test
  public void replaceImages_featureEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    assertThat(util.replaceImagesWithPlaceholder("Hello " + MARKDOWN_IMAGE + " world"))
        .isEqualTo("Hello ![base64-image] world");
    assertThat(util.replaceImagesWithPlaceholder(MARKDOWN_IMAGE)).isEqualTo("![base64-image]");
    assertThat(util.replaceImagesWithPlaceholder("No image here")).isEqualTo("No image here");
    assertThat(util.replaceImagesWithPlaceholder(null)).isNull();
    assertThat(util.replaceImagesWithPlaceholder("")).isEmpty();
  }

  @Test
  public void stripImages_withSpaces() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    String comment = "![alt]( " + IMAGE_DATA + " )";
    assertThat(util.stripImages(comment)).doesNotContain("base64");
  }

  @Test
  public void stripImages_withUppercaseMime() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    String upperData = IMAGE_DATA.replace("image/png", "IMAGE/PNG");
    String comment = "![alt](" + upperData + ")";
    assertThat(util.stripImages(comment)).doesNotContain("base64");
  }

  @Test
  public void stripImages_withMixedCaseProtocol() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    String mixedData = IMAGE_DATA.replace("data:", "Data:");
    String comment = "![alt](" + mixedData + ")";
    assertThat(util.stripImages(comment)).doesNotContain("base64");
  }

  @Test
  public void stripImages_withExtraParams() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    String dataWithParam = IMAGE_DATA.replace("base64,", "name=foo.png;base64,");
    String comment = "![alt](" + dataWithParam + ")";
    assertThat(util.stripImages(comment)).doesNotContain("base64");
  }

  @Test
  public void stripImages_withNewlineInBase64() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "allowMarkdownBase64ImagesInComments", true);
    MarkdownImagesUtil util = new MarkdownImagesUtil(cfg);

    String dataWithNewline = IMAGE_DATA.substring(0, 30) + "\n" + IMAGE_DATA.substring(30);
    String comment = "![alt](" + dataWithNewline + ")";
    assertThat(util.stripImages(comment)).doesNotContain("base64");
  }
}
