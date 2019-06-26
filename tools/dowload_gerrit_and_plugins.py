#!/usr/bin/env python3
# Copyright (C) 2019 The Android Open Source Project
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

# This scirpt download the artifacts needed to install Gerrit (Gerrit WAR, plugins and libs).
# It always downloads the latest version of the plugins.
#
# usage: dowload_gerrit_and_plugins.py [-h] [--version-file VERSION_FILE]
#                                      [--gerrit-home GERRIT_HOME]
#
# Download desired Gerrit and plugin versions. Always download latest version of
# plugins.
#
# optional arguments:
#   -h, --help            show this help message and exit
#   --version-file VERSION_FILE
#                         File to read Gerrit and plugins versions from
#   --gerrit-home GERRIT_HOME
#                         Directory to store the downloaded artifacts
#
# The script tries to dowload the plugins from the folllowing URLs:
# - "/plugin-" + plugin_name + "-stable-" + minor_gerrit_version + '/lastSuccessfulBuild/artifact/buck-out/gen/plugins/' + plugin_name + "/" + plugin_name + ".jar"
# - "/plugin-" + plugin_name + "-bazel-stable-" + minor_gerrit_version + '/lastSuccessfulBuild/artifact/bazel-bin/plugins/' + plugin_name + "/" + plugin_name + ".jar"
# - "/plugin-" + plugin_name + "-bazel-master-stable-" + minor_gerrit_version + '/lastSuccessfulBuild/artifact/bazel-bin/plugins/' + plugin_name + "/" + plugin_name + ".jar"
#
# It is possible to specify an alternative url by providing the "override_artifact_url" value in the artifacts configuration.
#
# An example of artifacts configuration file can be found in "gerrit-and-plugin-version.yml.example"

import re
import argparse
import yaml
import urllib.request
import urllib.error
import shutil
from os import link, makedirs, path, remove

def try_plugin_download(plugin_name, url, destination_dir):
    status = 200
    print("*** Trying to download plugin '" + plugin_name + "' from " + url, end='')
    try:
        with urllib.request.urlopen(url) as response, open(destination_dir + "/" + plugin_name + ".jar" , 'wb') as out_file:
            print(" [OK]")
            shutil.copyfileobj(response, out_file)
    except urllib.error.HTTPError as err:
        print(" [Fail]")
        status = err.code
    return status

parser = argparse.ArgumentParser(description='Download desired Gerrit and plugin versions. Always download latest version of plugins.')
parser.add_argument('--version-file', help="File to read Gerrit and plugins versions from",
                  default="./gerrit-and-plugin-version.yml",
                  dest="version_file",)
parser.add_argument('--gerrit-home',
                  default="/tmp/gerritcodereview-artifacts",
                  dest="gerrit_home",
                  help="Directory to store the downloaded artifacts",)

args = parser.parse_args()

print("*** Creating Gerrit home: " + args.gerrit_home)

try:
    makedirs(args.gerrit_home, exist_ok=True)
except OSError as err:
    print('error creating directory %s: %s' %
        (path.dirname(args.gerrit_home), err), file=stderr)
    exit(1)

with open(args.version_file, 'r') as ymlfile:
    artifacts_version = yaml.safe_load(ymlfile)

result = re.search("^(\d+\.\d+).*?", artifacts_version['gerrit'])

if not result or result is None:
    print("Invalid Gerrit version " + artifacts_version['gerrit'])
    exit(1)

minor_gerrit_version = result.group(1)

print("*** Downloading Minor Gerrit version: " + minor_gerrit_version)

gerrit_war_url = "https://gerrit-releases.storage.googleapis.com/gerrit-" + artifacts_version['gerrit'] + ".war"
with urllib.request.urlopen(gerrit_war_url) as response, open(args.gerrit_home + "/gerrit.war" , 'wb') as out_file:
    shutil.copyfileobj(response, out_file)
base_plugins_url = "https://gerrit-ci.gerritforge.com/view/Plugins-stable-" + minor_gerrit_version + "/job"

for plugin in artifacts_version['plugins']:
    plugin_name = list(plugin.keys())[0]
    buck_plugin_url = base_plugins_url + "/plugin-" + plugin_name + "-stable-" + minor_gerrit_version + '/lastSuccessfulBuild/artifact/buck-out/gen/plugins/' + plugin_name + "/" + plugin_name + ".jar"
    bazel_plugin_url = base_plugins_url + "/plugin-" + plugin_name + "-bazel-stable-" + minor_gerrit_version + '/lastSuccessfulBuild/artifact/bazel-bin/plugins/' + plugin_name + "/" + plugin_name + ".jar"
    bazel_master_plugin_url = base_plugins_url + "/plugin-" + plugin_name + "-bazel-master-stable-" + minor_gerrit_version + '/lastSuccessfulBuild/artifact/bazel-bin/plugins/' + plugin_name + "/" + plugin_name + ".jar"

    plugin_urls = [bazel_plugin_url, bazel_master_plugin_url, buck_plugin_url]
    if plugin[plugin_name] and 'override_artifact_url' in plugin[plugin_name]:
        plugin_urls = [ base_plugins_url + plugin[plugin_name]['override_artifact_url'] ]

    for url in plugin_urls:
        status = try_plugin_download(plugin_name, url, args.gerrit_home)
        if status != 404:
            break
