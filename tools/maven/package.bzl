# Copyright (C) 2016 The Android Open Source Project
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

_PREAMBLE = [
    "echo '#!/usr/bin/env bash' > $@",
    "echo \"# this script should run from the root of your workspace.\" >> $@",
    "echo \"set -e\" >> $@",
    "echo \"\" >> $@",
    "echo 'function bazel_cmd() {' >> $@",
    "echo '  if [[ `which bazelisk` ]]; then' >> $@",
    "echo '    bazelisk \"$$@\"' >> $@",
    "echo '  else' >> $@",
    "echo '    bazel \"$$@\"' >> $@",
    "echo '  fi' >> $@",
    "echo '}' >> $@",
    "echo \"\" >> $@",
    "echo 'if [[ \"$$VERBOSE\" ]]; then set -x ; fi' >> $@",
    "echo \"\" >> $@",
]

def _script_cmd(build_line, mvn_line):
    lines = _PREAMBLE[:]
    lines.append("echo " + build_line + " >> $@")
    lines.append("echo \"\" >> $@")
    lines.append("echo " + mvn_line + " >> $@")
    return " && ".join(lines)

def maven_package(
        version,
        jar = {},
        src = {},
        doc = {},
        war = {}):
    build_cmd = ["bazel_cmd", "build", "'$$@'"]
    mvn_cmd = ["python3", "tools/maven/mvn.py", "-v", version]
    api_cmd = mvn_cmd[:]
    api_targets = []
    for type, d in [("jar", jar), ("java-source", src), ("javadoc", doc)]:
        for a, t in sorted(d.items()):
            api_cmd.append("-s %s:%s:$(location %s)" % (a, type, t))
            api_targets.append(t)

    # The API artifacts publish via rules_jvm_external (api.sh drives their
    # `.publish` targets directly), so only emit the legacy API mvn.py scripts when
    # a fat-jar API artifact is still listed. After Phase 3 the API maps are empty,
    # so no gen_api_* scripts are generated (mvn.py would fail on an empty -s list).
    if api_targets:
        api_build = " ".join(build_cmd + api_targets)
        native.genrule(
            name = "gen_api_install",
            cmd = _script_cmd(api_build, " ".join(api_cmd + ["-a", "install"])),
            srcs = api_targets,
            outs = ["api_install.sh"],
            executable = True,
            testonly = True,
        )
        native.genrule(
            name = "gen_api_deploy",
            cmd = _script_cmd(api_build, " ".join(api_cmd + ["-a", "deploy"])),
            srcs = api_targets,
            outs = ["api_deploy.sh"],
            executable = True,
            testonly = True,
        )

    war_cmd = mvn_cmd[:]
    war_targets = []
    for a, t in sorted(war.items()):
        war_cmd.append("-s %s:war:$(location %s)" % (a, t))
        war_targets.append(t)

    war_build = " ".join(build_cmd + war_targets)

    native.genrule(
        name = "gen_war_install",
        cmd = _script_cmd(war_build, " ".join(war_cmd + ["-a", "install"])),
        srcs = war_targets,
        outs = ["war_install.sh"],
        executable = True,
    )

    native.genrule(
        name = "gen_war_deploy",
        # WAR is a separate Portal deployment; mvn.py clears staging by default.
        cmd = _script_cmd(war_build, " ".join(war_cmd + ["-a", "deploy"])),
        srcs = war_targets,
        outs = ["war_deploy.sh"],
        executable = True,
    )
