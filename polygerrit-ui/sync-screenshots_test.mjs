/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import test from "node:test";
import assert from "node:assert/strict";
import { parseArgs, resolveBaselineName } from "./sync-screenshots.mjs";

test("parseArgs defaults", () => {
  const options = parseArgs([]);
  assert.equal(options.change, null);
  assert.equal(options.build, null);
  assert.equal(options.host, "https://gerrit-review.googlesource.com");
  assert.equal(options.dryRun, false);
});

test("parseArgs flags and options", () => {
  const options = parseArgs([
    "--dry-run",
    "--change",
    "625465",
    "--build",
    "8671637310360164977",
    "--host",
    "https://custom-gerrit.example.com/",
  ]);
  assert.equal(options.dryRun, true);
  assert.equal(options.change, "625465");
  assert.equal(options.build, "8671637310360164977");
  assert.equal(options.host, "https://custom-gerrit.example.com");
});

test("parseArgs extracts build ID from URLs", () => {
  const options1 = parseArgs([
    "--build",
    "https://ci.chromium.org/ui/b/8671637310360164977",
  ]);
  assert.equal(options1.build, "8671637310360164977");

  const options2 = parseArgs([
    "https://cr-buildbucket.appspot.com/build/8671637310360164977",
  ]);
  assert.equal(options2.build, "8671637310360164977");
});

test("parseArgs handles positional arguments", () => {
  const optionsChange = parseArgs(["625465"]);
  assert.equal(optionsChange.change, "625465");

  const optionsBuild = parseArgs(["8671637310360164977"]);
  assert.equal(optionsBuild.build, "8671637310360164977");
});

test("resolveBaselineName extracts name from visual diff error", () => {
  const testResult = {
    testId:
      "gerrit > polygerrit-ui > gr-change-view_screenshot_test.ts > gr-change-view screenshot tests > full page at 801px width",
    failureReason: {
      primaryErrorMessage:
        "Visual diff failed. New screenshot is 0.07% different.\nSee diff for details: /b/s/w/ir/x/w/rc/checkout/polygerrit-ui/screenshots/Chromium/failed/gr-change-view-801px-dark-diff.png",
    },
    summaryHtml: "<pre>at async visualDiffDarkTheme</pre>",
  };

  const name = resolveBaselineName(testResult);
  assert.equal(name, "gr-change-view-801px-dark.png");
});

test("resolveBaselineName handles dimension mismatch with visualDiffDarkTheme stack trace", () => {
  const testResult = {
    testId:
      "gerrit > polygerrit-ui > gr-change-view_screenshot_test.ts > gr-change-view screenshot tests > full page at 801px width",
    failureReason: {
      primaryErrorMessage:
        "Screenshot is not the same width and height as the baseline.",
    },
    summaryHtml:
      "<pre>Screenshot is not the same width...\n  at async visualDiffDarkTheme (app/test/test-utils.ts:333:4)</pre>",
  };

  const name = resolveBaselineName(testResult);
  assert.equal(name, "gr-change-view-801px-dark.png");
});

test("resolveBaselineName handles dimension mismatch for light theme", () => {
  const testResult = {
    testId:
      "gerrit > polygerrit-ui > gr-change-view_screenshot_test.ts > gr-change-view screenshot tests > full page at 801px width",
    failureReason: {
      primaryErrorMessage:
        "Screenshot is not the same width and height as the baseline.",
    },
    summaryHtml:
      "<pre>Screenshot is not the same width...\n  at async visualDiff (app/test/test-utils.ts:320:4)</pre>",
  };

  const name = resolveBaselineName(testResult);
  assert.equal(name, "gr-change-view-801px.png");
});
