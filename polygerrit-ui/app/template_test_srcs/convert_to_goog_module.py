import os, re, json
from shutil import copyfile, rmtree

def replaceBehaviorLikeHTML (fileIn, fileOut):
  with open(fileIn) as f:
    file_str = f.read()
    match = behaviorCompiledRegex.search(file_str)
    if (match):
      replacement = match.group(1)
      with open('polygerrit-ui/temp/behaviors/' + fileOut.replace('html', 'js') , "w+") as f:
        f.write(replacement)

def replaceBehaviorLikeJS (fileIn, fileOut):
  with open(fileIn) as f:
    file_str = f.read()
    with open('polygerrit-ui/temp/behaviors/' + fileOut , "w+") as f:
      f.write(file_str)

def replacePolymerElement (fileIn, fileOut):
  with open(fileIn) as f:
    # Removed self invoked function
    file_str = f.read()
    file_str_no_fn = fnCompiledRegex.search(file_str)

    if file_str_no_fn:
      new = re.sub(polymerCompiledRegex,'exports = Polymer({', file_str_no_fn.group(1))
      package = root.replace('/', '.') + '.' + fileOut
      new = "goog.module('polygerrit." + package + "')\n\n" + new

      with open('polygerrit-ui/temp/' + fileOut, "w+") as f:
        f.write(new)

      # Add package and javascript to files object.
      elements[root]['js'] = "polygerrit-ui/temp/" + fileOut
      elements[root]['package'] = package

def writeTempFile( file ):
  if (not root in elements):
    elements[root] = {}
  if file.endswith('.html') and not file.endswith("_test.html"):
    # gr-navigation is treated like a behavior rather than a standard element
    # because of the way it added to the Gerrit object.
    if file.endswith("gr-navigation.html"):
      replaceBehaviorLikeHTML(os.path.join(root, file), file)
    else:
      elements[root]['html'] = os.path.join(root, file)
  if file.endswith(".js"):
    replacePolymerElement(os.path.join(root, file), file)


if __name__ == "__main__":
  polymerRegex = r"Polymer\({"
  polymerCompiledRegex = re.compile(polymerRegex)

  removeSelfInvokeRegex = r"\(function\(\) {\n([\s\S]*)}\)\(\);"
  fnCompiledRegex = re.compile(removeSelfInvokeRegex)

  regexBehavior = r"<script>([\s\S]+)<\/script>"
  behaviorCompiledRegex = re.compile(regexBehavior)

  # Create temp directory.
  if not os.path.exists('polygerrit-ui/temp'):
    os.makedirs('polygerrit-ui/temp')

  # Within temp directory create behavior directory.
  if not os.path.exists('polygerrit-ui/temp/behaviors'):
    os.makedirs('polygerrit-ui/temp/behaviors')

  elements = {}

  # Go through every file in app/elements, and re-write accordingly to temp
  # directory, and also added to elements object, which is used to generate a
  # map of html files, package names, and javascript files.
  for root, dirs, files in os.walk("polygerrit-ui/app/elements"):
    for file in files:
      writeTempFile(file)

  # Special case for polymer behaviors we are using.
  replaceBehaviorLikeHTML('polygerrit-ui/app/bower_components/iron-a11y-keys-behavior/iron-a11y-keys-behavior.html', 'iron-a11y-keys-behavior.html')

  #TODO figure out something to do with iron-overlay-behavior. it is hard-coded reformatted.

  with open('polygerrit-ui/temp/map.json', "w+") as f:
    f.write(json.dumps(elements))

  for root, dirs, files in os.walk("polygerrit-ui/app/behaviors"):
    for file in files:
      if file.endswith("behavior.html"):
        replaceBehaviorLikeHTML(os.path.join(root, file), file)
      elif file.endswith("behavior.js"):
        replaceBehaviorLikeJS(os.path.join(root, file), file)
