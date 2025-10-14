#!/usr/bin/env node

// Copyright 2022 Aspect Build Systems, Inc.
// Copyright 2025 The Android Open Source Project

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//     http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Modified and minified version of https://github.com/aspect-build/rules_terser
// to deal with node_modules locations in the Gerrit project. Further, style was
// modified to match Gerrit's coding style.

/**
 * @fileoverview wraps the terser CLI to support minifying a directory
 * Terser doesn't support it; see https://github.com/terser/terser/issues/75
 * This file was copied from rules_nodejs which is Apache-2 licensed:
 * https://github.com/bazelbuild/rules_nodejs/blob/43e478dd48cea53dbcc580da92ccb517534b832d/packages/terser/index.js
 */
const fs = require('fs')
const path = require('path')
const child_process = require('child_process')
const os = require('os')

// Run Bazel with --define=VERBOSE_LOGS=1 to enable this logging
const VERBOSE_LOGS = !!process.env['VERBOSE_LOGS']

function log_verbose(...m) {
  if (VERBOSE_LOGS) console.error('[terser/index.js]', ...m)
}

function log_error(...m) {
  console.error('[terser/index.js]', ...m)
}

function isDirectory(input) {
  return fs.lstatSync(path.join(process.cwd(), input)).isDirectory()
}

// Returns a single quotes version of str
function singleQuotes(str) {
  return `'${str.replace(/'/g, '').replace(/"/g, '')}'`
}

// Ensures that args are well formed.
// Work-around for an issue on Windows when exec bin path is not quoted.
// In --source-map, base=bazel-out/x64_windows-opt-exec-2B5CBBC6/bin must
// be quoted such as base='bazel-out/x64_windows-opt-exec-2B5CBBC6/bin' pr
// terser fails with
// ERROR: `includeSources,base=bazel-out/x64_windows-opt-exec-2B5CBBC6/bin,content=inline,url=bundle.min.js.map` is not a supported option
function fixArgs(args) {
  const sourceMapIndex = args.indexOf('--source-map')
  if (sourceMapIndex === -1) {
    return args
  }
  let sourceMapOptions = args[sourceMapIndex + 1].split(',')
  sourceMapOptions = sourceMapOptions.map((o) => {
    const s = o.split('=')
    if (s.length == 1) {
      return o
    }
    switch (s[0]) {
      case 'base':
      case 'content':
      case 'url':
        return `${s[0]}=${singleQuotes(s[1])}`
      default:
        return o
    }
  })

  return [
    ...args.slice(0, sourceMapIndex + 1),
    sourceMapOptions.join(','),
    ...args.slice(sourceMapIndex + 2),
  ]
}

/**
 * Replaces directory url with the outputFile name in the url option of source-map argument
 */
function directoryArgs(residualArgs, inputFile, outputFile) {
  const sourceMapIndex = residualArgs.indexOf('--source-map')
  if (sourceMapIndex === -1) {
    return residualArgs
  }

  let sourceMapOptions = residualArgs[sourceMapIndex + 1].split(',')

  // set the correct sourcemap url for this output file
  sourceMapOptions = sourceMapOptions.map((o) =>
    o.startsWith('url=') ? `url='${path.basename(outputFile)}.map'` : o
  )

  // if an input .map file exists then set the correct sourcemap content option
  if (fs.existsSync(`${inputFile}.map`)) {
    // even on Windows terser expects '/' path separators so we normalize these in the sourcemap
    // content file path below
    sourceMapOptions = sourceMapOptions.map((o) =>
      o.startsWith('content=')
        ? `content='${inputFile.replace(/\\/g, '/')}.map'`
        : o
    )
  }

  return [
    ...residualArgs.slice(0, sourceMapIndex + 1),
    sourceMapOptions.join(','),
    ...residualArgs.slice(sourceMapIndex + 2),
  ]
}

function terserDirectory(input, output, residual, terserBinary) {
  if (!fs.existsSync(output)) {
    fs.mkdirSync(output)
  }

  const TERSER_CONCURENCY =
    process.env.TERSER_CONCURRENCY || os.cpus().length - 1 || 1

  let work = []
  let active = 0
  let errors = []

  function exec([inputFile, outputFile]) {
    active++
    let args = [
      terserBinary,
      inputFile,
      '--output',
      outputFile,
      ...directoryArgs(residual, inputFile, outputFile),
    ]

    spawn(process.execPath, [...process.execArgv, ...args]).then(
      (data) => {
        if (data.code) {
          errors.push(inputFile)
          // NOTE: Even though a terser process has errored we continue here to collect all of
          // the errors. this behavior is another candidate for user configuration because
          // there is value in stopping at the first error in some use cases.

          log_error(
            `errored: ${inputFile}\nOUT: ${data.out}\nERR: ${data.err}\ncode: ${data.code}`
          )
        } else {
          log_verbose('finished: ', inputFile)
        }
        --active
        next()
      },
      (err) => {
        --active
        log_error('errored: [spawn exception]', inputFile, '\n' + err)
        errors.push(inputFile)
        next()
      }
    )
  }

  function next() {
    if (work.length) {
      exec(work.shift())
    } else if (!active) {
      if (errors.length) {
        log_error('terser errored processing javascript in directory.')
        process.exitCode = 2
      }
      // NOTE: work is done at this point and node should exit here.
    }
  }

  fs.readdirSync(input).forEach((f) => {
    if (path.extname(f) === '.js' || path.extname(f) === '.mjs') {
      const inputFile = path.join(input, path.basename(f))
      const outputFile = path.join(output, path.basename(f))

      if (active < TERSER_CONCURENCY) {
        exec([inputFile, outputFile])
      } else {
        work.push([inputFile, outputFile])
      }
    }
  })
}

function spawn(cmd, args) {
  return new Promise((resolve, reject) => {
    const err = []
    const out = []
    // this may throw syncronously if the process cannot be created.
    let proc = child_process.spawn(cmd, args)

    proc.stdout.on('data', (buf) => {
      out.push(buf)
    })
    proc.stderr.on('data', (buf) => {
      err.push(buf)
    })
    proc.on('exit', (code) => {
      // we never reject here based on exit code because an error is a valid result of running a
      // process.
      resolve({
        out: Buffer.concat(out),
        err: err.length ? Buffer.concat(err) : false,
        code,
      })
    })
  })
}

function main() {
  process.argv = fixArgs(process.argv)

  // Peek at the arguments to find any directories declared as inputs
  let argv = process.argv.slice(2)
  // terser.bzl always passes the inputs first,
  // then --output [out], then remaining args
  // We want to keep those remaining ones to pass to terser
  // Avoid a dependency on a library like minimist; keep it simple.
  const outputArgIndex = argv.findIndex((arg) => arg.startsWith('--'))

  // We don't want to implement a command-line parser for terser
  // so we invoke its CLI as child processes when a directory is provided, just altering the
  // input/output arguments. See discussion: https://github.com/bazelbuild/rules_nodejs/issues/822

  const inputs = argv.slice(0, outputArgIndex)
  const output = argv[outputArgIndex + 1]
  const residual = argv.slice(outputArgIndex + 2)

  // Allow for user to override terser binary via TERSER_BINARY for testing
  let terserBinary = process.env.TERSER_BINARY
  if (!terserBinary) {
    try {
      // Node 12 and above will respect exports field in package.json, Terser 5 added these
      // but did not add ./bin/terser as an export so we instead resolve to terser/package.json
      // and strip the /package.json and add /bin/terser in its place. This has now been
      // fixed upstream in https://github.com/terser/terser/pull/971 but this code should remain
      // so we support all versions of terser.
      // NB: slice(0,-13) trims the '/pacakge.json' from the end of the resolved path.
      const nodeToolsModulesDir = path.join(__dirname, '../../../tools/node_tools/node_modules');
      const terserNpmPath = require.resolve('terser/package.json', {paths: [__dirname, nodeToolsModulesDir]} ).slice(0, -13)
      terserBinary = `${terserNpmPath}/bin/terser`
      if (!fs.existsSync(terserBinary)) {
        // Try the old `uglifyjs` binary from <4.3.0
        terserBinary = `${terserNpmPath}/bin/uglify`
      }
    } catch (_) {
      // fall through here; will check for valid terserBinary below
    }
  }
  if (!terserBinary || !fs.existsSync(terserBinary)) {
    throw new Error(
      'terser binary not found. Maybe you need to set the terser_bin attribute?'
    )
  }

  // choose a default concurrency of the number of cores -1 but at least 1.

  log_verbose(`Running terser/index.js
   inputs: ${inputs}
   output: ${output}
   residual: ${residual}`)
  if (!inputs.find(isDirectory)) {
    // Inputs were only files
    // Just use terser CLI exactly as it works outside bazel
    require(terserBinary)
  } else if (inputs.length > 1) {
    // We don't know how to merge multiple input dirs to one output dir
    throw new Error(
      'terser only allows a single input when minifying a directory'
    )
  } else if (inputs[0]) {
    terserDirectory(inputs[0], output, residual, terserBinary)
  }
}

// export this for unit testing purposes
exports.directoryArgs = directoryArgs

if (require.main === module) {
  main()
}
