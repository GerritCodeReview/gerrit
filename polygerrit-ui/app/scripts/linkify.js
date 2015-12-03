// Copyright (c) 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
"use strict";
function LinkTextParser(callback)
{
    this.callback = callback;
    Object.preventExtensions(this);
}
LinkTextParser.CODESITE_TRACKERS = [
    "chromium",
    "chromium-os",
    "chrome-os-partner",
    "gyp",
    "skia",
    "v8",
    "gerrit"
];
LinkTextParser.GITHUB_TRACKERS = [
    "catapult"
];
LinkTextParser.GITHUB_TRACKER_PROJECT_PATHS = {
    catapult: ['catapult-project', 'catapult']
};
LinkTextParser.MONORAIL_TRACKERS = [
    "crashpad",
    "monorail",
    "pdfium"
]
LinkTextParser.prototype.addText = function(text, href)
{
    if (!text)
        return;
    this.callback(text, href);
};
LinkTextParser.prototype.addBugText = function(text, tracker, bugId)
{
    if (tracker && LinkTextParser.CODESITE_TRACKERS.find(tracker)) {
        var href = "https://code.google.com/p/{1}/issues/detail?id={2}".assign(
            encodeURIComponent(tracker), encodeURIComponent(bugId));
        this.addText(text, href);
        return;
    }
    if (tracker && LinkTextParser.GITHUB_TRACKERS.find(tracker)) {
        var trackerOrg = LinkTextParser.GITHUB_TRACKER_PROJECT_PATHS[tracker][0];
        var trackerProj = LinkTextParser.GITHUB_TRACKER_PROJECT_PATHS[tracker][1];
        var href = "https://github.com/{1}/{2}/issues/{3}".assign(
            encodeURIComponent(trackerOrg), encodeURIComponent(trackerProj),
            encodeURIComponent(bugId));
        this.addText(text, href);
        return;
    }
    if (tracker && LinkTextParser.MONORAIL_TRACKERS.find(tracker)) {
        var href = "https://bugs.chromium.org/p/{1}/issues/detail?id={2}".assign(
            encodeURIComponent(tracker), encodeURIComponent(bugId));
        this.addText(text, href);
        return;
    }
    // If there's a bug number, but no tracker, assume Chromium.
    if (!tracker) {
        var href = "https://code.google.com/p/chromium/issues/detail?id={1}".assign(
            encodeURIComponent(bugId));
        this.addText(text, href);
        return;
    }
    this.addText(text)
};
LinkTextParser.prototype.parse = function(text)
{
    linkify(text, {
        callback: this.parseChunk.bind(this)
    });
};
LinkTextParser.prototype.parseChunk = function(text, href)
{
    if (href)
        this.addText(text, href);
    else
        this.parseBugs(text);
};
LinkTextParser.prototype.parseBugs = function(text) {
    // TODO(ojan): bugdroid has more complex rules for bug parsing.
    // A regex which accepts a much greater variety of bug-like lines.
    // And the BUG= line is in a "footer", i.e. the last newline-separated
    // paragraph of the commit message.
    var BUG_PATTERN = /^BUG=(.+)$/m;
    var BUG_ID = /(([a-zA-Z0-9\-]*):)?#?(\d+)/;
    for (var match = text.match(BUG_PATTERN); match; match = text.match(BUG_PATTERN)) {
        var before = text.substring(0, match.index);
        this.addText(before);
        this.addText("BUG=");
        text = text.substring(match.index + match[0].length);
        var bugText = match[1];
        for (var bugMatch = bugText.match(BUG_ID); bugMatch; bugMatch = bugText.match(BUG_ID)) {
            var before = bugText.substring(0, bugMatch.index);
            this.addText(before);
            var tracker = bugMatch[2];
            var bugId = bugMatch[3];
            var matchText = bugMatch[0];
            this.addBugText(matchText, tracker, bugId);
            bugText = bugText.substring(bugMatch.index + bugMatch[0].length);
        }
        this.addText(bugText);
    }
    this.addText(text);
};