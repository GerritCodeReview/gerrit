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

"use strict";

function GrLinkTextParser(callback) {
  this.callback = callback;
  Object.preventExtensions(this);
}

// TODO(mmccoy): Move these patterns to Gerrit project config
// (https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#get-config)
GrLinkTextParser.CUSTOM_LINKS = [
  {
    'pattern': /^(Change-Id: )(.+)$/mi,
    'url': 'https://gerrit.googlesource.com/gerrit/+/'
  },
  {
    'pattern': /^(Feature: )Issue ?(.+)$/mi,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id='
  },
  {
    'pattern': /^(Bug: )Issue ?(.+)$/mi,
    'url': 'https://code.google.com/p/gerrit/issues/detail?id='
  }
]

GrLinkTextParser.prototype.addText = function(text, href) {
  if (!text) {
    return;
  }
  this.callback(text, href);
};

GrLinkTextParser.prototype.addBugText = function(text, tracker, bugId) {
  if (tracker) {
    var href = tracker.url + encodeURIComponent(bugId);
    this.addText(text, href);
    return;
  }
  this.addText(text)
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
    this.parseLinks(text, GrLinkTextParser.CUSTOM_LINKS);
  }
};

GrLinkTextParser.prototype.parseLinks = function(text, patterns) {
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
    if (match[1] !== 'Change-Id: ') {
      this.addBugText('Issue ' + match[2], patterns[i], match[2]);
    } else {
      this.addBugText(match[2], patterns[i], match[2]);
    };

  };
  this.addText(text);
};
