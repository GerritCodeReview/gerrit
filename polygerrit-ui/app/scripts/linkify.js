// Copyright (c) 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

"use strict";

function LinkTextParser(callback) {
  this.callback = callback;
  Object.preventExtensions(this);
}

LinkTextParser.CUSTOM_LINKS = [
  {
    'pattern': /^(Change-Id: )(.+)$/mi,
    'url': 'https://gerrit.googlesource.com/gerrit/+/'
  },
  {
    'pattern': /^(Feature: )(.+)$/mi,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id='
  },
  {
    'pattern': /^(Bug: )(.+)$/mi,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id='
  }
]

LinkTextParser.prototype.addText = function(text, href) {
  if (!text) {
    return;
  }
  this.callback(text, href);
};

LinkTextParser.prototype.addBugText = function(text, tracker, bugId) {
  if (tracker) {
    var href = tracker.url + encodeURIComponent(bugId);
    this.addText(text, href);
    return;
  }
  this.addText(text)
};

LinkTextParser.prototype.parse = function(text) {
  console.log(text);
  linkify(text, {
    callback: this.parseChunk.bind(this)
  });
};

LinkTextParser.prototype.parseChunk = function(text, href) {
  if (href) {
    this.addText(text, href);
  } else {
    this.parseLinks(text, LinkTextParser.CUSTOM_LINKS);
  }
};

LinkTextParser.prototype.parseLinks = function(text, patterns) {
  for (var i = patterns.length - 1; i >= 0; i--) {
    var PATTERN = patterns[i].pattern;
    var URL = patterns[i].url;

    var match = text.match(PATTERN);
    if (!match){
      continue;
    }

    var before = text.substr(0, match.index);
    this.addText(before);
    this.addText(match[1]);
    text = text.substr(match.index + match[0].length);
    this.addBugText(match[2], patterns[i], match[2]);
  };
  this.addText(text);
};
