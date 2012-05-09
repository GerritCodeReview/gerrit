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

import static org.pegdown.Extensions.ALL;
import org.pegdown.PegDownProcessor;

public class MarkdownFormatter {

  public String getHtmlFromMarkdown(String markdownSource) {
    return new PegDownProcessor(ALL).markdownToHtml(markdownSource);
  }

  // TODO: Add a cache
}
