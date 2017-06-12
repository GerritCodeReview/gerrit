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

/** Test parser for a Html email client response from Gmail. */
public class GmailHtmlParserTest extends HtmlParserTest {
  @Override
  protected String newHtmlBody(
      String changeMessage, String c1, String c2, String c3, String f1, String f2, String fc1) {
    String email =
        ""
            + "<div dir=\"ltr\">"
            + (changeMessage != null ? changeMessage : "")
            + "<div class=\"gmail_extra\"><br><div class=\"gmail_quote\">"
            + "On Fri, Nov 18, 2016 at 11:15 AM, foobar (Gerrit) noreply@gerrit.com"
            + "<span dir=\"ltr\">&lt;<a href=\"mailto:noreply@gerrit.com\" "
            + "target=\"_blank\">noreply@gerrit.com</a>&gt;</span> wrote:<br>"
            + "<blockquote class=\"gmail_quote\" "
            + "<p>foobar <strong>posted comments</strong> on this change.</p>"
            + "<p><a href=\""
            + changeURL
            + "/1\" "
            + "target=\"_blank\">View Change</a></p><div>Patch Set 2: CR-1\n"
            + "\n"
            + "(3 comments)</div><ul><li>"
            + "<p>"
            + // File #1: test.txt
            "<a href=\""
            + changeURL
            + "/1/gerrit-server/test.txt\">"
            + "File gerrit-server/<wbr>test.txt:</a></p>"
            + commentBlock(f1)
            + "<li><p>"
            + "<a href=\""
            + changeURL
            + "/1/gerrit-server/test.txt\">"
            + "Patch Set #2:</a> </p>"
            + "<blockquote><pre>Some inline comment from Gerrit</pre>"
            + "</blockquote><p>Some comment on file 1</p>"
            + "</li>"
            + commentBlock(fc1)
            + "<li><p>"
            + "<a href=\""
            + changeURL
            + "/1/gerrit-server/test.txt@2\">"
            + "Patch Set #2, Line 31:</a> </p>"
            + "<blockquote><pre>Some inline comment from Gerrit</pre>"
            + "</blockquote><p>Some text from original comment</p>"
            + "</li>"
            + commentBlock(c1)
            + ""
            + // Inline comment #2
            "<li><p>"
            + "<a href=\""
            + changeURL
            + "/1/gerrit-server/test.txt@3\">"
            + "Patch Set #2, Line 47:</a> </p>"
            + "<blockquote><pre>Some comment posted on Gerrit</pre>"
            + "</blockquote><p>Some more comments from Gerrit</p>"
            + "</li>"
            + commentBlock(c2)
            + "<li><p>"
            + "<a href=\""
            + changeURL
            + "/1/gerrit-server/test.txt@115\">"
            + "Patch Set #2, Line 115:</a> <code>some code</code></p>"
            + "<p>some comment</p></li></ul></li>"
            + ""
            + "<li><p>"
            + // File #2: test.txt
            "<a href=\""
            + changeURL
            + "/1/gerrit-server/readme.txt\">"
            + "File gerrit-server/<wbr>readme.txt:</a></p>"
            + commentBlock(f2)
            + "<li><p>"
            + "<a href=\""
            + changeURL
            + "/1/gerrit-server/readme.txt@3\">"
            + "Patch Set #2, Line 31:</a> </p>"
            + "<blockquote><pre>Some inline comment from Gerrit</pre>"
            + "</blockquote><p>Some text from original comment</p>"
            + "</li>"
            + commentBlock(c3)
            + ""
            + // Inline comment #2
            "</ul></li></ul>"
            + ""
            + // Footer
            "<p>To view, visit <a href=\""
            + changeURL
            + "/1\">this change</a>. "
            + "To unsubscribe, visit <a href=\"https://someurl\">settings</a>."
            + "</p><p>Gerrit-MessageType: comment<br>"
            + "Footer omitted</p>"
            + "<div><div></div></div>"
            + "<p>Gerrit-HasComments: Yes</p></blockquote></div><br></div></div>";
    return email;
  }

  private static String commentBlock(String comment) {
    if (comment == null) {
      return "";
    }
    return "</ul></li></ul></blockquote><div>"
        + comment
        + "</div><blockquote class=\"gmail_quote\"><ul><li><ul>";
  }
}
