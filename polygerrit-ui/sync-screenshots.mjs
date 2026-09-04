#!/usr/bin/env node
/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_GERRIT_HOST = 'https://gerrit-review.googlesource.com';
const RESULTDB_HOST = 'https://results.api.cr.dev';

function printUsage() {
  console.log(`
Usage:
  yarn test:screenshot-sync [options] [<change-id-or-number>]

Description:
  Synchronizes PolyGerrit screenshot baselines from failed LUCI CI runs.
  Fetches the authoritative 'actual_image' artifacts generated in CI and
  updates local baselines in polygerrit-ui/screenshots/Chromium/baseline/.

Options:
  --change <id>   Gerrit change number or Change-Id (defaults to current git HEAD)
  --build <id>    Specific Buildbucket build ID or full URL (e.g. 8671637310360164977)
  --host <url>    Gerrit host URL (default: ${DEFAULT_GERRIT_HOST})
  --dry-run       Show which baselines would be updated without writing files
  -h, --help      Display this help message

Examples:
  yarn test:screenshot-sync
  yarn test:screenshot-sync 625465
  yarn test:screenshot-sync --change Ie19a240c1986f352c6fdcc91d01604ceee0f95bb
  yarn test:screenshot-sync --build 8671637310360164977
`);
}

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    change: null,
    build: null,
    host: DEFAULT_GERRIT_HOST,
    dryRun: false,
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '-h' || arg === '--help') {
      printUsage();
      process.exit(0);
    } else if (arg === '--dry-run') {
      options.dryRun = true;
    } else if (arg === '--change') {
      options.change = args[++i];
    } else if (arg === '--build') {
      options.build = args[++i];
    } else if (arg === '--host') {
      options.host = args[++i].replace(/\/+$/, '');
    } else if (!arg.startsWith('-') && !options.change && !options.build) {
      if (/^\d{15,}$/.test(arg) || arg.includes('cr-buildbucket') || arg.includes('ci.chromium.org')) {
        options.build = arg;
      } else {
        options.change = arg;
      }
    } else {
      console.error(`Unknown argument: ${arg}`);
      printUsage();
      process.exit(1);
    }
  }

  // Clean build argument if URL was passed
  if (options.build) {
    const match = options.build.match(/(\d{15,})/);
    if (match) {
      options.build = match[1];
    }
  }

  return options;
}

function getCurrentGitChangeId() {
  try {
    const commitMsg = execSync('git log -1 --format=%B', { encoding: 'utf8' });
    const match = commitMsg.match(/Change-Id:\s*(I[0-9a-fA-F]+)/);
    if (match) {
      return match[1];
    }
  } catch (e) {
    // Ignore git failure
  }
  return null;
}

async function fetchGerritMessages(host, changeId) {
  const url = `${host}/changes/${encodeURIComponent(changeId)}/messages`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch Gerrit change messages from ${url} (HTTP ${res.status}): ${await res.text()}`);
  }
  const text = await res.text();
  const jsonStr = text.startsWith(")]}'") ? text.substring(text.indexOf('\n') + 1) : text;
  return JSON.parse(jsonStr);
}

async function findLatestFailedBuild(host, changeId) {
  console.log(`Querying Gerrit change ${changeId} on ${host}...`);
  const messages = await fetchGerritMessages(host, changeId);

  // Search messages from newest to oldest
  for (let i = messages.length - 1; i >= 0; i--) {
    const msg = messages[i].message || '';
    const match = msg.match(/cr-buildbucket\.appspot\.com\/build\/(\d+)/);
    if (match) {
      const isScreenshotFailure =
        msg.includes("Step('run screenshot tests') (retcode: 1)") ||
        msg.includes('run screenshot tests') ||
        msg.includes('Frontend-Verified-1');
      if (isScreenshotFailure) {
        console.log(`Found failed screenshot build ${match[1]} from message on ${messages[i].date}`);
        return match[1];
      }
    }
  }

  // Fallback: Return any buildbucket link found in messages
  for (let i = messages.length - 1; i >= 0; i--) {
    const match = (messages[i].message || '').match(/cr-buildbucket\.appspot\.com\/build\/(\d+)/);
    if (match) {
      console.log(`Found build ${match[1]} from message on ${messages[i].date}`);
      return match[1];
    }
  }

  return null;
}

async function queryResultDbFailures(buildId) {
  const url = `${RESULTDB_HOST}/prpc/luci.resultdb.v1.ResultDB/QueryTestResults`;
  console.log(`Querying ResultDB for build ${buildId}...`);
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({
      invocations: [`invocations/build-${buildId}`],
      predicate: {
        expectancy: 'VARIANTS_WITH_UNEXPECTED_RESULTS',
      },
    }),
  });

  if (!res.ok) {
    throw new Error(`ResultDB QueryTestResults failed (HTTP ${res.status}): ${await res.text()}`);
  }

  const rawText = await res.text();
  const jsonStr = rawText.startsWith(")]}'") ? rawText.substring(rawText.indexOf('\n') + 1) : rawText;
  const data = JSON.parse(jsonStr);
  return data.testResults || [];
}

async function listTestArtifacts(testResultName) {
  const url = `${RESULTDB_HOST}/prpc/luci.resultdb.v1.ResultDB/ListArtifacts`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({ parent: testResultName }),
  });

  if (!res.ok) {
    throw new Error(`ResultDB ListArtifacts failed (HTTP ${res.status}): ${await res.text()}`);
  }

  const rawText = await res.text();
  const jsonStr = rawText.startsWith(")]}'") ? rawText.substring(rawText.indexOf('\n') + 1) : rawText;
  const data = JSON.parse(jsonStr);
  return data.artifacts || [];
}

function resolveBaselineName(testResult) {
  const errorMsg = testResult.failureReason?.primaryErrorMessage || '';

  // 1. Check if error message explicitly points to failed diff or failed png
  const diffMatch = errorMsg.match(/failed\/(.+?)-diff\.png/);
  if (diffMatch && diffMatch[1]) {
    return `${diffMatch[1]}.png`;
  }

  const failedMatch = errorMsg.match(/failed\/(.+?)\.png/);
  if (failedMatch && failedMatch[1]) {
    return `${failedMatch[1]}.png`;
  }

  // 2. Try parsing test case name from testId
  // e.g. "gerrit > polygerrit-ui > gr-change-view_screenshot_test.ts > gr-change-view screenshot tests > full page at 801px width"
  const testId = testResult.testId || '';
  const isDark = testId.toLowerCase().includes('dark') || errorMsg.toLowerCase().includes('dark');

  // Search existing baseline files to find the best match
  const baselineDir = path.join(__dirname, 'screenshots/Chromium/baseline');
  if (fs.existsSync(baselineDir)) {
    const existingFiles = fs.readdirSync(baselineDir).filter(f => f.endsWith('.png'));
    const testTokens = testId.toLowerCase().split(/[^a-z0-9]+/);

    let bestFile = null;
    let maxMatches = 0;
    for (const file of existingFiles) {
      const fileIsDark = file.includes('-dark');
      if (isDark !== fileIsDark) continue;

      const fileTokens = file.replace(/\.png$/, '').toLowerCase().split(/[-_]/);
      let matchCount = 0;
      for (const token of fileTokens) {
        if (testTokens.includes(token)) matchCount++;
      }
      if (matchCount > maxMatches) {
        maxMatches = matchCount;
        bestFile = file;
      }
    }
    if (bestFile && maxMatches >= 2) {
      return bestFile;
    }
  }

  return null;
}

async function main() {
  const options = parseArgs();

  let buildId = options.build;
  if (!buildId) {
    let changeId = options.change;
    if (!changeId) {
      changeId = getCurrentGitChangeId();
      if (changeId) {
        console.log(`Detected Change-Id from HEAD: ${changeId}`);
      }
    }

    if (!changeId) {
      console.error('Error: No Change-Id or build ID provided, and none found in git commit message.');
      printUsage();
      process.exit(1);
    }

    buildId = await findLatestFailedBuild(options.host, changeId);
    if (!buildId) {
      console.error(`Could not find a failed LUCI build for change ${changeId}.`);
      console.error('Ensure you have uploaded a patchset and LUCI CI has executed.');
      process.exit(1);
    }
  }

  const failedTests = await queryResultDbFailures(buildId);
  if (failedTests.length === 0) {
    console.log(`No failed screenshot tests found in build ${buildId}. Everything looks good!`);
    return;
  }

  console.log(`Found ${failedTests.length} failed test(s) in build ${buildId}.\n`);

  const baselineDir = path.join(__dirname, 'screenshots/Chromium/baseline');
  if (!fs.existsSync(baselineDir)) {
    fs.mkdirSync(baselineDir, { recursive: true });
  }

  const updatedFiles = [];

  for (const tr of failedTests) {
    const baselineName = resolveBaselineName(tr);
    if (!baselineName) {
      console.warn(`⚠️  Could not determine baseline filename for test: ${tr.testId}`);
      continue;
    }

    const artifacts = await listTestArtifacts(tr.name);
    const actualArtifact = artifacts.find(a => a.artifactId === 'actual_image');
    if (!actualArtifact) {
      console.warn(`⚠️  No 'actual_image' artifact found for test: ${tr.testId}`);
      continue;
    }

    const targetPath = path.join(baselineDir, baselineName);
    const exists = fs.existsSync(targetPath);

    if (options.dryRun) {
      console.log(`[DRY RUN] Would update: ${baselineName} (${actualArtifact.sizeBytes} bytes) ${exists ? '[OVERWRITE]' : '[NEW]'}`);
      updatedFiles.push(baselineName);
      continue;
    }

    // Download actual image
    const imgRes = await fetch(actualArtifact.fetchUrl);
    if (!imgRes.ok) {
      console.error(`Failed to download ${actualArtifact.fetchUrl} (HTTP ${imgRes.status})`);
      continue;
    }

    const buffer = Buffer.from(await imgRes.arrayBuffer());
    fs.writeFileSync(targetPath, buffer);
    const status = exists ? 'UPDATED' : 'NEW';
    console.log(`✓ [${status}] ${baselineName} (${buffer.length} bytes)`);
    updatedFiles.push(baselineName);
  }

  console.log('\n------------------------------------------------------------');
  if (options.dryRun) {
    console.log(`Dry run complete. ${updatedFiles.length} file(s) would be updated.`);
  } else {
    console.log(`Successfully synced ${updatedFiles.length} screenshot baseline(s) from LUCI CI! 🎉`);
    console.log('\nNext steps:');
    console.log('  1. Review changes: git diff polygerrit-ui/screenshots/Chromium/baseline');
    console.log('  2. Stage & amend:  git commit -a --amend');
    console.log('  3. Re-upload:      git push origin HEAD:refs/for/master');
  }
}

main().catch(err => {
  console.error('\nFatal error:', err);
  process.exit(1);
});
