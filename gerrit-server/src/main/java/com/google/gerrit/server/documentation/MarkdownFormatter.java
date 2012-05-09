// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.documentation;

import org.pegdown.PegDownProcessor;
import static org.pegdown.Extensions.*;

public class MarkdownFormatter {
  private PegDownProcessor pegProcessor;

  public String getHtmlFromMarkdown(String markdownSource) {
    pegProcessor = new PegDownProcessor(ALL);
    String htmled;
    htmled = pegProcessor.markdownToHtml(markdownSource);
    return htmled;
  }

//  public String getHtmlFromMarkdown(File markdownFile) {
//
//  }

  // TODO: Add a cache
  // TODO: Write a ssh command plugin that exercises this
  // Command takes a plugin name and a doc name, renders to stdout?
}
