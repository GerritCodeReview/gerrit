// Copyright (C) 2015 The Android Open Source Project
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

'use strict';

function GrLinkTextParser(linkConfig, callback) {
  this.linkConfig = linkConfig;
  this.callback = callback;
  Object.preventExtensions(this);
}

GrLinkTextParser.prototype.addText = function(text, href) {
  if (!text) {
    return;
  }
  this.callback(text, href);
};

GrLinkTextParser.prototype.addHTML = function(html) {
  this.callback(null, null, html);
};

GrLinkTextParser.prototype.parse = function(text) {
  linkify(text, {
    callback: this.parseChunk.bind(this)
  });
};

GrLinkTextParser.prototype.parseChunk = function(text, href) {
  if (href) {
    this.addText(text, href);
  } else {
    this.parseLinks(text, this.linkConfig);
  }
};

GrLinkTextParser.prototype.parseLinks = function(text, patterns) {
  for (var p in patterns) {
    if (patterns[p].enabled != null && patterns[p].enabled == false) {
      continue;
    }
    // PolyGerrit doesn't use hash-based navigation like GWT.
    // Account for this.
    if (patterns[p].html) {
      patterns[p].html =
          patterns[p].html.replace(/<a href=\"#\//g, '<a href="/');
    } else if (patterns[p].link) {
      if (patterns[p].link[0] == '#') {
        patterns[p].link = patterns[p].link.substr(1);
      }
    }

    var pattern = new RegExp(patterns[p].match, 'g');

    var match;
    while (match = pattern.exec(text)) {
      var before = text.substr(0, match.index);
      this.addText(before);
      text = text.substr(match.index + match[0].length);
      console.log(patterns[p])
      var result = match[0].replace(pattern,
          patterns[p].html || patterns[p].link);

      if (patterns[p].html) {
        this.addHTML(result);
      } else if (patterns[p].link) {
        this.addText(match[0], result);
      } else {
        throw Error('linkconfig entry ' + p +
            ' doesn’t contain a link or html attribute.');
      }
    }
  }
  this.addText(text);
};
