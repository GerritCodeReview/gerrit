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
    console.log(patterns[p].match)
    var pattern = new RegExp(patterns[p].match, 'g');

    var match;
    while (match = pattern.exec(text)) {
      var link = match[0].replace(pattern, patterns[p].link);

      // PolyGerrit doesn't use hash-based navigation like GWT.
      // Account for this.
      if (link[0] == '#') {
        link = link.substr(1);
      }
      var before = text.substr(0, match.index);
      this.addText(before);
      text = text.substr(match.index + match[0].length);
      this.addText(match[0], link);
    }
  }
  this.addText(text);
};
