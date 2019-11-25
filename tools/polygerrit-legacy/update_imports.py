#!/usr/bin/env python
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Suggested call sequences:
1. Update single .js file
python tools/polygerrit-legacy/update_imports.py \
 -r polygerrit-ui/polymer-legacy-support/src \
 -f polymer/polymer-element_bridge.js \
 -n polygerrit-ui/node_modules

2. Update directory recursively
python tools/polygerrit-legacy/update_imports.py \
 -r polygerrit-ui/polymer-legacy-support/src \
 -d polymer \
 -n polygerrit-ui/node_modules

3. Update everything in root folder
python tools/polygerrit-legacy/update_imports.py \
 -r polygerrit-ui/polymer-legacy-support/src \
 -d ./ \
 -n polygerrit-ui/node_modules

"""
from __future__ import print_function

from functools import partial
import optparse
import os
import re
import sys

def updateImportPath(path, rootPath, basePath, nodeModulesPath):
  normPath = os.path.normpath(os.path.join(rootPath, basePath, path))
  if os.path.isfile(normPath):
    return path

  nodeModules = ["@polymer"]
  for nodeModule in nodeModules:
    filePathInNodeModules = os.path.normpath(os.path.join(nodeModule, basePath, path))
    nodeModulePath = os.path.normpath(os.path.join(nodeModulesPath, filePathInNodeModules))
    if os.path.isfile(nodeModulePath):
      return filePathInNodeModules

  raise Exception("Cannot resolve import path '{}'".format(path))

def importReplace(match, rootPath, sourceFilePath, nodeModulesPath):
  path = match.group("path")
  isRelPath = path.startswith("./") or path.startswith("../")
  if not isRelPath:
    return match.group(0);

  return match.expand('\g<prefix>' + updateImportPath(path, rootPath, os.path.dirname(sourceFilePath), nodeModulesPath) + '\g<suffix>')

def updateRecursively(rootPath, dir, nodeModulesPath):
  fullPath = os.path.normpath(os.path.join(rootPath, dir))
  for item in os.listdir(fullPath):
    if os.path.isdir(os.path.join(fullPath, item)):
      updateRecursively(rootPath, os.path.join(dir, item), nodeModulesPath)
    else:
      name = os.path.join(dir, item)
      ext = os.path.splitext(name)[1]
      if ext == ".js":
        print("Updating file: {}". format(name))
        updateSingleFile(rootPath, name, nodeModulesPath)


def updateSingleFile(rootPath, filePath, nodeModulesPath):
  joinedPath  = os.path.join(rootPath, filePath)
  with open(joinedPath, 'r') as content_file:
    content = content_file.read()
  result = re.sub(r"(?P<prefix>^import.*\')(?P<path>.*)(?P<suffix>\';$)", partial(importReplace, rootPath=rootPath, sourceFilePath=filePath, nodeModulesPath=nodeModulesPath), content, flags=re.MULTILINE)

  with open(joinedPath, 'w') as content_file:
    content_file.write(result)

def main(args):
  parser = optparse.OptionParser("Usage:\n\t%prog -r root_dir -f path_to_js_file.js -n path_to_node_modules_dir\nOr\n\t%prog -r root_dir -d path_to_dir -n path_to_node_modules_dir")
  parser.add_option('-r', help='root dir for legacy code')
  parser.add_option('-f', help='js file to update, relative to root directory')
  parser.add_option('-d', help='dir to update, relative to root directory')
  parser.add_option('-n', help='Path to node modules')

  opts, args = parser.parse_args()

  if not opts.r:
    parser.error("Paramer -r is required")

  if not opts.n:
    parser.error("Paramer -n is required")
  if ((not opts.f) and (not opts.d)) or (opts.f and opts.d):
    parser.error("Specify either -f or -d parameter. Only one of 2 parameters is possible")

  if opts.d:
    updateRecursively(opts.r, opts.d, opts.n)

  if opts.f:
    updateSingleFile(opts.r, opts.f, opts.n)


if __name__ == '__main__':
  main(sys.argv[1:])

