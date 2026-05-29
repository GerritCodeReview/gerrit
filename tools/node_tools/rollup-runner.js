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

// This file is a part of workaround for
// https://github.com/bazelbuild/rules_nodejs/issues/1575
// rollup_bundle doesn't run when it is placed
// in nested node_modules folder and bazel runs with
// --spawn_strategy local flag. The error happens
// because npm_package_bin generates incorrect path to
// rollup bin.
// But npm_package_bin works correctly if entry point
// is an ordinary .js file which depends on some node module.
// This script is a proxy script, which run rollup bin from
// node_modules folder and pass all parameters to it.
const {spawnSync} = require('child_process');
const path = require('path');

const nodePath = process.argv[0];
const scriptArgs = process.argv.slice(2);
const nodeArgs = process.execArgv;

const pathToBin = require.resolve("rollup/dist/bin/rollup");

const options = {
  stdio: 'inherit'
};

const spawnResult = spawnSync(nodePath, [...nodeArgs, pathToBin, ...scriptArgs], options);

if(spawnResult.status !== null) {
  process.exit(spawnResult.status);
}

if(spawnResult.error) {
  console.error(spawnResult.error);
  process.exit(1);
}
