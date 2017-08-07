import os, re, json
from shutil import copyfile, rmtree

regex = r"'use strict';([\s\S]*)Polymer\({\s*(\S[\s\S]*\S)\s*\}\);"
p = re.compile(regex)

regexBehavior = r"<script>([\s\S]+)<\/script>"
p2 = re.compile(regexBehavior)

if not os.path.exists('temp'):
  os.makedirs('temp')

if not os.path.exists('temp/behaviors'):
  os.makedirs('temp/behaviors')

elements = {}

def replaceBehaviorLikeHTML (fileIn, fileOut):
  with open(os.path.join(root, fileIn)) as f:
    file_str = f.read()
    match = p2.search(file_str)
    if (match):
      replacement = match.group(1)
      with open('temp/behaviors/' + fileOut.replace('html', 'js') , "w+") as f:
        f.write(replacement)

def replaceBehaviorLikeJS (fileIn, fileOut):
  with open(os.path.join(root, fileIn)) as f:
    file_str = f.read()
    with open('temp/behaviors/' + fileOut , "w+") as f:
      f.write(file_str)

def replacePolymerElement (fileIn, fileOut):
  with open(os.path.join(root, fileIn)) as f:
    file_str = f.read()
    match = p.search(file_str)
    if (match):
      elements[root]['js'] = "temp/" + fileOut
      package = root.replace('/', '.') + '.' + fileOut
      elements[root]['package'] = package
      replacement = "/** \n" \
      "* @fileoverview \n" \
      "* @suppress {missingProperties} \n" \
      "*/ \n\n" \
      "goog.module('polygerrit." + package + "')\n\n"+match.group(1)+"\nexports = Polymer({\n\t"+match.group(2)+"\n});"
      with open('temp/' + fileOut, "w+") as f:
        f.write(replacement)

def writeTempFile( file ):
  if 'plugins' in root:
    return
  if 'gr-public-js-api' in file:
    return
  if 'gr-js-api-interface' in file:
    return
  if (not root in elements):
    elements[root] = {}
  if file.endswith('.html') and not file.endswith("_test.html"):
    if file.endswith("gr-navigation.html"):
      replaceBehaviorLikeHTML(file, file)
    else:
      elements[root]['html'] = os.path.join(root, file)
  if file.endswith(".js"):
    replacePolymerElement(file, file)

for root, dirs, files in os.walk("app/elements"):
  for file in files:
    writeTempFile(file)

# Special case for polymer behaviors we are using.
replaceBehaviorLikeHTML('../../bower_components/iron-a11y-keys-behavior/iron-a11y-keys-behavior.html', 'iron-a11y-keys-behavior.html')

#TODO figure out something to do with iron-overlay-behavior. it is hard-coded reformatted.

with open('temp/map.json', "w+") as f:
  f.write(json.dumps(elements))

for root, dirs, files in os.walk("app/behaviors"):
  for file in files:
    if file.endswith("behavior.html"):
      replaceBehaviorLikeHTML(file, file)
    elif file.endswith("behavior.js"):
      replaceBehaviorLikeJS(file, file)



