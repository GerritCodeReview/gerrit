import json
import os
import re

polymerRegex = r"Polymer\({"
polymerCompiledRegex = re.compile(polymerRegex)

removeSelfInvokeRegex = r"\(function\(\) {\n(.+)}\)\(\);"
fnCompiledRegex = re.compile(removeSelfInvokeRegex, re.DOTALL)

regexBehavior = r"<script>(.+)<\/script>"
behaviorCompiledRegex = re.compile(regexBehavior, re.DOTALL)


def _open(filename, mode="r"):
    try:
        return open(filename, mode, encoding="utf-8")
    except TypeError:
        return open(filename, mode)


def replaceBehaviorLikeHTML(fileIn, fileOut):
    with _open(fileIn) as f:
        file_str = f.read()
        match = behaviorCompiledRegex.search(file_str)
        if match:
            with _open("polygerrit-ui/temp/behaviors/" +
                       fileOut.replace("html", "js"), "w+") as f:
                f.write(match.group(1))


def replaceBehaviorLikeJS(fileIn, fileOut):
    with _open(fileIn) as f:
        file_str = f.read()
        with _open("polygerrit-ui/temp/behaviors/" + fileOut, "w+") as f:
            f.write(file_str)


def generateStubBehavior(behaviorName):
    with _open("polygerrit-ui/temp/behaviors/" +
               behaviorName + ".js", "w+") as f:
        f.write("/** @polymerBehavior **/\n" + behaviorName + "= {};")


def replacePolymerElement(fileIn, fileOut, root):
    with _open(fileIn) as f:
        key = fileOut.split('.')[0]
        # Removed self invoked function
        file_str = f.read()
        file_str_no_fn = fnCompiledRegex.search(file_str)

        if file_str_no_fn:
            package = root.replace("/", ".") + "." + fileOut

            with _open("polygerrit-ui/temp/" + fileOut, "w+") as f:
                mainFileContents = re.sub(
                    polymerCompiledRegex,
                    "exports = Polymer({",
                    file_str_no_fn.group(1)).replace("'use strict';", "")
                f.write("/** \n"
                        "* @fileoverview \n"
                        "* @suppress {missingProperties} \n"
                        "*/ \n\n"
                        "goog.module('polygerrit." + package + "')\n\n" +
                        mainFileContents)

            # Add package and javascript to files object.
            elements[key]["js"] = "polygerrit-ui/temp/" + fileOut
            elements[key]["package"] = package


def writeTempFile(file, root):
    # This is included in an extern because it is directly on the window object
    # (for now at least).
    if "gr-reporting" in file:
        return
    key = file.split('.')[0]
    if key not in elements:
        # gr-app doesn't have an additional level
        elements[key] = {
            "directory":
                'gr-app' if len(root.split("/")) < 4 else root.split("/")[3]
        }
    if file.endswith(".html") and not file.endswith("_test.html"):
        # gr-navigation is treated like a behavior rather than a standard
        # element because of the way it added to the Gerrit object.
        if file.endswith("gr-navigation.html"):
            replaceBehaviorLikeHTML(os.path.join(root, file), file)
        else:
            elements[key]["html"] = os.path.join(root, file)
    if file.endswith(".js"):
        replacePolymerElement(os.path.join(root, file), file, root)


if __name__ == "__main__":
    # Create temp directory.
    if not os.path.exists("polygerrit-ui/temp"):
        os.makedirs("polygerrit-ui/temp")

    # Within temp directory create behavior directory.
    if not os.path.exists("polygerrit-ui/temp/behaviors"):
        os.makedirs("polygerrit-ui/temp/behaviors")

    elements = {}

    # Go through every file in app/elements, and re-write accordingly to temp
    # directory, and also added to elements object, which is used to generate a
    # map of html files, package names, and javascript files.
    for root, dirs, files in os.walk("polygerrit-ui/app/elements"):
        for file in files:
            writeTempFile(file, root)

    # Special case for polymer behaviors we are using.
    replaceBehaviorLikeHTML("polygerrit-ui/app/bower_components/iron-a11y-keys-behavior/iron-a11y-keys-behavior.html", "iron-a11y-keys-behavior.html")
    generateStubBehavior("Polymer.IronOverlayBehavior")
    generateStubBehavior("Polymer.IronFitBehavior")

    # TODO figure out something to do with iron-overlay-behavior.
    # it is hard-coded reformatted.

    with _open("polygerrit-ui/temp/map.json", "w+") as f:
        f.write(json.dumps(elements))

    for root, dirs, files in os.walk("polygerrit-ui/app/behaviors"):
        for file in files:
            if file.endswith("behavior.html"):
                replaceBehaviorLikeHTML(os.path.join(root, file), file)
            elif file.endswith("behavior.js"):
                replaceBehaviorLikeJS(os.path.join(root, file), file)
