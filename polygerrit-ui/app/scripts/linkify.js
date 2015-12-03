// Copyright (c) 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

"use strict";

function LinkTextParser(callback) {
  this.callback = callback;
  Object.preventExtensions(this);
}

LinkTextParser.prototype.customLinks = [
  {
    'pattern': /^Feature:(.+)$/m,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id={1}'
  },
  {
    'pattern': /^Change-Id:(.+)$/m,
    'url': 'https://gerrit.googlesource.com/gerrit/+/{1}'
  },
  {
    'pattern': /^Bug:(.+)$/m,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id={1}'
  }
]

LinkTextParser.prototype.addText = function(text, href) {
  if (!text)
    return;
  this.callback(text, href);
};

LinkTextParser.prototype.addBugText = function(text, tracker, bugId) {
  this.addText(text)
};

LinkTextParser.prototype.parse = function(text) {
  linkify(text, {
    callback: this.parseChunk.bind(this)
  });
};

LinkTextParser.prototype.parseChunk = function(text, href) {
  if (href)
    this.addText(text, href);
  else
    this.parseLinks(text, this.customLinks);
};

LinkTextParser.prototype.parseLinks = function(text, patterns) {

  for (var i = patterns.length - 1; i >= 0; i--) {
    var PATTERN = patterns[i].pattern;
    var URL = patterns[i].url;

    var match = text.match(PATTERN);
    if (!match)
      continue;

    console.log(match);

  };

  // var BUG_PATTERN = /^BUG=(.+)$/m;
  // var BUG_ID = /(([a-zA-Z0-9\-]*):)?#?(\d+)/;
  // for (var match = text.match(PATTERN); match; match = text.match(PATTERN)) {
  //   var before = text.substring(0, match.index);
  //   this.addText(before);
  //   this.addText("BUG=");
  //   text = text.substring(match.index + match[0].length);
  //   var bugText = match[1];
  //   for (var bugMatch = bugText.match(BUG_ID); bugMatch; bugMatch = bugText.match(BUG_ID)) {
  //     var before = bugText.substring(0, bugMatch.index);
  //     this.addText(before);
  //     // var tracker = bugMatch[2];
  //     var tracker = 'gerrit';
  //     var bugId = bugMatch[3];
  //     var matchText = bugMatch[0];
  //     this.addBugText(matchText, tracker, bugId);
  //     bugText = bugText.substring(bugMatch.index + bugMatch[0].length);
  //   }
  //   this.addText(bugText);
  // }
  this.addText(text);
};
