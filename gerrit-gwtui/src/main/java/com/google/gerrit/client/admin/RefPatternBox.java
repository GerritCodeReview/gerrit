// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.text.shared.Parser;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.user.client.ui.ValueBox;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import java.io.IOException;
import java.text.ParseException;

public class RefPatternBox extends ValueBox<String> {
  private static final Renderer<String> RENDERER =
      new Renderer<String>() {
        @Override
        public String render(String ref) {
          return ref;
        }

        @Override
        public void render(String ref, Appendable dst) throws IOException {
          dst.append(render(ref));
        }
      };

  private static final Parser<String> PARSER =
      new Parser<String>() {
        @Override
        public String parse(CharSequence text) throws ParseException {
          String ref = text.toString();

          if (ref.isEmpty()) {
            throw new ParseException(Util.C.refErrorEmpty(), 0);
          }

          if (ref.charAt(0) == '/') {
            throw new ParseException(Util.C.refErrorBeginSlash(), 0);
          }

          if (ref.charAt(0) == '^') {
            if (!ref.startsWith("^refs/")) {
              ref = "^refs/heads/" + ref.substring(1);
            }
          } else if (!ref.startsWith("refs/")) {
            ref = "refs/heads/" + ref;
          }

          for (int i = 0; i < ref.length(); i++) {
            final char c = ref.charAt(i);

            if (c == '/' && 0 < i && ref.charAt(i - 1) == '/') {
              throw new ParseException(Util.C.refErrorDoubleSlash(), i);
            }

            if (c == ' ') {
              throw new ParseException(Util.C.refErrorNoSpace(), i);
            }

            if (c < ' ') {
              throw new ParseException(Util.C.refErrorPrintable(), i);
            }
          }
          return ref;
        }
      };

  public RefPatternBox() {
    super(Document.get().createTextInputElement(), RENDERER, PARSER);
    addKeyPressHandler(GlobalKey.STOP_PROPAGATION);
    addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (event.getCharCode() == ' ') {
              event.preventDefault();
            }
          }
        });
  }
}
