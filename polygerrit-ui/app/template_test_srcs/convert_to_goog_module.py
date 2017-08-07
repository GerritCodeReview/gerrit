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

def writeTempFile( file ):
  # print root
  # print file
  if 'plugins' in root:
    return
  if 'gr-public-js-api' in file:
    return
  if 'gr-js-api-interface' in file:
    return
  if (not root in elements):
    elements[root] = {}
  if file.endswith('.html') and not file.endswith("_test.html"):
    elements[root]['html'] = os.path.join(root, file)
  if file.endswith(".js"):
    with open(os.path.join(root, file)) as f:
      file_str = f.read()
      match = p.search(file_str)
      if (match):
        elements[root]['js'] = "temp/" + file
        package = root.replace('/', '.') + '.' + file
        elements[root]['package'] = package
        replacement = "/** \n" \
        "* @fileoverview \n" \
        "* @suppress {missingProperties} \n" \
        "*/ \n\n" \
        "goog.module('polygerrit." + package + "')\n\n"+match.group(1)+"\nexports = Polymer({\n\t"+match.group(2)+"\n});"
        with open('temp/' + file, "w+") as f:
          f.write(replacement)

for root, dirs, files in os.walk("app/elements"):
  for file in files:
    writeTempFile(file)

# Special case for polymer behaviors we are using.
with open(os.path.join(root, '../../bower_components/iron-a11y-keys-behavior/iron-a11y-keys-behavior.html')) as f:
  file_str = f.read()
  with open('temp/behaviors/iron-a11y-keys-behavior.js' , "w+") as f:
    match = p2.search(file_str)
    if (match):
      replacement = match.group(1)
      with open('temp/behaviors/iron-a11y-keys-behavior.js' , "w+") as f:
          f.write(replacement)
#TODO figure out something to do with iron-overlay-behavior. it is hard-coded reformatted.

with open('temp/map.json', "w+") as f:
  f.write(json.dumps(elements))

for root, dirs, files in os.walk("app/behaviors"):
  for file in files:
    if file.endswith("behavior.html"):
      with open(os.path.join(root, file)) as f:
        file_str = f.read()
        match = p2.search(file_str)
        if (match):
          replacement = match.group(1)
          with open('temp/behaviors/' + file.replace('html', 'js') , "w+") as f:
            f.write(replacement)
    elif file.endswith("behavior.js"):
      with open(os.path.join(root, file)) as f:
        file_str = f.read()
        with open('temp/behaviors/' + file , "w+") as f:
            f.write(file_str)


