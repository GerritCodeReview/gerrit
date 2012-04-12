//Copyright (C) 2009 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.httpd.gitweb;

import com.google.gerrit.common.data.GerritConfig;
import com.google.inject.Inject;

import org.htmlparser.Parser;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class HTMLReplaceProcessor {

  private Logger log = LoggerFactory.getLogger(HTMLReplaceProcessor.class);

  private GerritConfig config;

  @Inject
  public HTMLReplaceProcessor(final GerritConfig config) {
    this.config = config;
  }

  public void processStreams(InputStream in, OutputStream out,
      String encoding)
      throws IOException {

    PrintWriter writer = new PrintWriter(out);
    try {
      Parser parser = new Parser(new Lexer(new Page(in, encoding)));
      NodeList srcPage = parser.parse(null);

      srcPage.visitAllNodesWith(new HTMLNodeFindReplace(config.getCommentLinks()));

      String outDoc = srcPage.toHtml();
      writer.print(outDoc);
    } catch (ParserException e) {
      log.error(
          "Cannot process HTML stream transformation because of a parsing error. Returning input stream untouched ",
          e);
      processIdentityStream(in, out);
    } finally {
      writer.close();
    }
  }


  private void processIdentityStream(InputStream in, OutputStream out) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    PrintWriter writer = new PrintWriter(out);
    try {
      String buff;
      while (null != (buff = reader.readLine())) {
        writer.println(buff);
      }

    } finally {
      reader.close();
      writer.close();
    }
  }


}
