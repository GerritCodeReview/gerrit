/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ESLint resolves relative paths from the current working directory.
//
// In workspace mode (`lint_bin`), this works with the regular Node.js module
// layout under `polygerrit-ui/app/node_modules`.
//
// In Bazel test mode (`lint_test`), npm dependencies are exposed through
// multiple runfiles trees instead. Typed linting with TypeScript does not see
// the same module and type environment from that layout as from the workspace
// layout.
//
// To align `lint_test` with `lint_bin`, synthesize a local
// `polygerrit-ui/app/node_modules` by symlinking entries from the runfiles
// npm trees, then prepend it to NODE_PATH so resolution matches workspace mode.

const fs = require('fs');
const Module = require('module');
const path = require('path');

function pathExists(filePath) {
  try {
    return fs.existsSync(filePath);
  } catch {
    return false;
  }
}

function readDirEntries(dirPath) {
  try {
    return fs.readdirSync(dirPath, {withFileTypes: true});
  } catch {
    return [];
  }
}

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, {recursive: true});
}

function symlinkIfMissing(targetPath, linkPath) {
  if (pathExists(linkPath)) return;

  try {
    fs.symlinkSync(targetPath, linkPath);
  } catch {
    // Ignore races and pre-existing entries.
  }
}

function mergeNodeModules(destinationDir, sourceDirs) {
  ensureDir(destinationDir);

  for (const sourceDir of sourceDirs) {
    for (const entry of readDirEntries(sourceDir)) {
      const sourceEntry = path.join(sourceDir, entry.name);
      const destEntry = path.join(destinationDir, entry.name);

      if (entry.name.startsWith('@') && entry.isDirectory()) {
        ensureDir(destEntry);

        for (const scopedEntry of readDirEntries(sourceEntry)) {
          symlinkIfMissing(
            path.join(sourceEntry, scopedEntry.name),
            path.join(destEntry, scopedEntry.name)
          );
        }
        continue;
      }

      symlinkIfMissing(sourceEntry, destEntry);
    }
  }
}

function getRunfilesRoot() {
  return process.env.RUNFILES_DIR || process.env.TEST_SRCDIR || '';
}

function getRunfilesNodeModules(runfilesRoot) {
  return [
    path.join(runfilesRoot, 'ui_dev_npm/node_modules'),
    path.join(runfilesRoot, 'ui_npm/node_modules'),
    path.join(runfilesRoot, '_main/node_modules'),
  ].filter(pathExists);
}

function prependNodePath(paths) {
  const existing = process.env.NODE_PATH
    ? process.env.NODE_PATH.split(path.delimiter).filter(Boolean)
    : [];

  process.env.NODE_PATH = [...paths, ...existing].join(path.delimiter);
  Module._initPaths();
}

function getConfigDirFromArgv(argv) {
  const configArgIndex = argv.findIndex(arg => arg === '-c' || arg === '--config');
  if (configArgIndex < 0 || configArgIndex + 1 >= argv.length) return '';

  return path.dirname(argv[configArgIndex + 1]);
}

const configDir = getConfigDirFromArgv(process.argv);
if (!configDir) return;

process.chdir(configDir);

const runfilesRoot = getRunfilesRoot();
if (!runfilesRoot) return;

const runfilesNodeModules = getRunfilesNodeModules(runfilesRoot);
if (runfilesNodeModules.length === 0) return;

const localNodeModules = path.join(process.cwd(), 'node_modules');
mergeNodeModules(localNodeModules, runfilesNodeModules);
prependNodePath([localNodeModules, ...runfilesNodeModules]);
