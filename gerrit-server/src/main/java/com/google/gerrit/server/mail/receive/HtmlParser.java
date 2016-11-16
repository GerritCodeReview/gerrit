// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.receive;

import com.google.common.base.Strings;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Comment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Collection;
import java.util.List;

public class HtmlParser {
  public static ParsedComments parse(String html, Collection<File> comments) {
    ParsedComments parsed = new ParsedComments();
    Document d = Jsoup.parse(html);

    int fileOffset = -1;
    int psOffset = -1;
    Comment currentComment = null;

    for (Element e : d.body().getAllElements()) {
      String name = e.tagName();

      boolean isInBlockQuote = false;
      for (Element parent : e.parents()) {
        if (parent.tagName().equals("blockquote")) {
          isInBlockQuote = true;
        }
      }

      if (name.equals("a")) {
        String href = e.attr("href");
        int i = 0;
        for (File f : comments) {
          if (href.contains(f.name)) {
            fileOffset = i;
          }
          int j = 0;
          for (LineComment c : f.lineComments) {
            if (e.ownText().contains(c.getName())) {
              psOffset = j;
              currentComment = c.comment;
            }
            j++;
          }
          i++;
        }
      } else if (!isInBlockQuote && name.equals("div") && !e.className().startsWith("gmail")) {

        StringBuffer sb = new StringBuffer();
        sb.append("File: " + fileOffset + " PS: " + psOffset + " ");
        String content = e.ownText();
        if (!Strings
            .isNullOrEmpty(content.replace("\n", "").replace(" ", ""))) {
          sb.append(content);

          if (fileOffset == -1 && psOffset == -1) {
            parsed.changeComment = parsed.changeComment == null ? content : parsed.changeComment + "\n" + content;
          } else {
            Comment c = new Comment(new Comment.Key("", currentComment.key.filename, currentComment.key.patchSetId), new Id(100000), null, (short) 0, content, null);
            c.parentUuid = currentComment.key.uuid;
            c.lineNbr = currentComment.lineNbr;
            c.side = currentComment.side;
            c.tag = currentComment.tag;
            c.range = currentComment.range;
            parsed.comments.add(c);
          }

          System.out.println(sb);
        }
      }
    }



    return parsed;
  }
}
