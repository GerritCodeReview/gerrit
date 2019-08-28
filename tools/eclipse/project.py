#!/usr/bin/env python
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

from __future__ import print_function
import argparse
import os
import subprocess
import xml.dom.minidom
import re
import sys

MAIN = '//tools/eclipse:classpath'
GWT = '//gerrit-gwtui:ui_module'
AUTO = '//lib/auto:auto-value'
JRE = '/'.join([
    'org.eclipse.jdt.launching.JRE_CONTAINER',
    'org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType',
    'JavaSE-1.8',
])
# Map of targets to corresponding classpath collector rules
cp_targets = {
    AUTO: '//tools/eclipse:autovalue_classpath_collect',
    GWT: '//tools/eclipse:gwt_classpath_collect',
    MAIN: '//tools/eclipse:main_classpath_collect',
}

ROOT = os.path.abspath(__file__)
while not os.path.exists(os.path.join(ROOT, 'WORKSPACE')):
    ROOT = os.path.dirname(ROOT)

opts = argparse.ArgumentParser("Create Eclipse Project")
opts.add_argument('--plugins', help='create eclipse projects for plugins',
                  action='store_true')
opts.add_argument('--name', help='name of the generated project',
                  action='store', default='gerrit', dest='project_name')
opts.add_argument('-b', '--batch', action='store_true',
                  dest='batch', help='Bazel batch option')
opts.add_argument('-j', '--java', action='store',
                  dest='java', help='Post Java 8 support (9)')
opts.add_argument('-e', '--edge_java', action='store',
                  dest='edge_java', help='Post Java 9 support (10|11|...)')
opts.add_argument('--bazel',
                  help=('name of the bazel executable. Defaults to using'
                        ' bazelisk if found, or bazel if bazelisk is not'
                        ' found.'),
                  action='store', default=None, dest='bazel_exe')

args = opts.parse_args()


def find_bazel():
    if args.bazel_exe:
        try:
            return subprocess.check_output(
                ['which', args.bazel_exe]).strip().decode('UTF-8')
        except subprocess.CalledProcessError:
            print('Bazel command: %s not found' % args.bazel_exe, file=sys.stderr)
            sys.exit(1)
    try:
        return subprocess.check_output(
            ['which', 'bazelisk']).strip().decode('UTF-8')
    except subprocess.CalledProcessError:
        try:
            return subprocess.check_output(
                ['which', 'bazel']).strip().decode('UTF-8')
        except subprocess.CalledProcessError:
            print("Neither bazelisk nor bazel found. Please see"
                  " Documentation/dev-bazel for instructions on installing"
                  " one of them.")
            sys.exit(1)


batch_option = '--batch' if args.batch else None
custom_java = args.java
edge_java = args.edge_java
bazel_exe = find_bazel()


def _build_bazel_cmd(*args):
    build = False
    cmd = [bazel_exe]
    if batch_option:
        cmd.append('--batch')
    for arg in args:
        if arg == "build":
            build = True
        cmd.append(arg)
    if custom_java and not edge_java:
        cmd.append('--host_java_toolchain=@bazel_tools//tools/jdk:toolchain_java%s' % custom_java)
        cmd.append('--java_toolchain=@bazel_tools//tools/jdk:toolchain_java%s' % custom_java)
        if edge_java and build:
            cmd.append(edge_java)
    return cmd


def retrieve_ext_location():
    return subprocess.check_output(_build_bazel_cmd('info', 'output_base')).strip()


def gen_bazel_path(ext_location):
    bazel = subprocess.check_output(['which', bazel_exe]).strip().decode('UTF-8')
    with open(os.path.join(ROOT, ".bazel_path"), 'w') as fd:
        fd.write("output_base=%s\n" % ext_location)
        fd.write("bazel=%s\n" % bazel)
        fd.write("PATH=%s\n" % os.environ["PATH"])


def _query_classpath(target):
    deps = []
    t = cp_targets[target]
    try:
        subprocess.check_call(_build_bazel_cmd('build', t))
    except subprocess.CalledProcessError:
        exit(1)
    name = 'bazel-bin/tools/eclipse/' + t.split(':')[1] + '.runtime_classpath'
    deps = [line.rstrip('\n') for line in open(name)]
    return deps


def gen_project(name='gerrit', root=ROOT):
    p = os.path.join(root, '.project')
    with open(p, 'w') as fd:
        print("""\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>%(name)s</name>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.jdt.core.javabuilder</name>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>\
    """ % {"name": name}, file=fd)


def gen_plugin_classpath(root):
    p = os.path.join(root, '.classpath')
    with open(p, 'w') as fd:
        if os.path.exists(os.path.join(root, 'src', 'test', 'java')):
            testpath = """
  <classpathentry excluding="**/BUILD" kind="src" path="src/test/java"\
 out="eclipse-out/test">
    <attributes><attribute name="test" value="true"/></attributes>
  </classpathentry>"""
        else:
            testpath = ""
        print("""\
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
  <classpathentry excluding="**/BUILD" kind="src" path="src/main/java"/>%(testpath)s
  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
  <classpathentry combineaccessrules="false" kind="src" path="/gerrit"/>
  <classpathentry kind="output" path="eclipse-out/classes"/>
</classpath>""" % {"testpath": testpath}, file=fd)


def gen_classpath(ext):
    def make_classpath():
        impl = xml.dom.minidom.getDOMImplementation()
        return impl.createDocument(None, 'classpath', None)

    def classpathentry(kind, path, src=None, out=None, exported=None):
        e = doc.createElement('classpathentry')
        e.setAttribute('kind', kind)
        # TODO(davido): Remove this and other exclude BUILD files hack
        # when this Bazel bug is fixed:
        # https://github.com/bazelbuild/bazel/issues/1083
        if kind == 'src':
            e.setAttribute('excluding', '**/BUILD')
        e.setAttribute('path', path)
        if src:
            e.setAttribute('sourcepath', src)
        if out:
            e.setAttribute('output', out)
        if exported:
            e.setAttribute('exported', 'true')
        if out and "test" in out:
            atts = doc.createElement('attributes')
            testAtt = doc.createElement('attribute')
            testAtt.setAttribute('name', 'test')
            testAtt.setAttribute('value', 'true')
            atts.appendChild(testAtt)
            e.appendChild(atts)
        doc.documentElement.appendChild(e)

    doc = make_classpath()
    src = set()
    lib = set()
    proto = set()
    gwt_src = set()
    gwt_lib = set()
    plugins = set()

    # Classpath entries are absolute for cross-cell support
    java_library = re.compile('bazel-out/.*?-fastbuild/bin/(.*)/[^/]+[.]jar$')
    srcs = re.compile('(.*/external/[^/]+)/jar/(.*)[.]jar')
    for p in _query_classpath(MAIN):
        if p.endswith('-src.jar'):
            # gwt_module() depends on -src.jar for Java to JavaScript compiles.
            if p.startswith("external"):
                p = os.path.join(ext, p)
            gwt_lib.add(p)
            continue

        m = java_library.match(p)
        if m:
            src.add(m.group(1))
            # Exceptions: both source and lib
            if p.endswith('libquery_parser.jar') or \
               p.endswith('libgerrit-prolog-common.jar') or \
         p.endswith('com_google_protobuf/libprotobuf_java.jar') or \
               p.endswith('lucene-core-and-backward-codecs__merged.jar'):
                lib.add(p)
            # JGit dependency from external repository
            if 'gerrit-' not in p and 'jgit' in p:
                lib.add(p)
            # Assume any jars in /proto/ are from java_proto_library rules
            if '/bin/proto/' in p:
                proto.add(p)
        else:
            # Don't mess up with Bazel internal test runner dependencies.
            # When we use Eclipse we rely on it for running the tests
            if p.endswith(
               "external/bazel_tools/tools/jdk/TestRunner_deploy.jar"):
                continue
            if p.startswith("external"):
                p = os.path.join(ext, p)
            lib.add(p)

    for p in _query_classpath(GWT):
        m = java_library.match(p)
        if m:
            gwt_src.add(m.group(1))

    classpathentry('src', 'java')
    classpathentry('src', 'javatests', out='eclipse-out/test')
    classpathentry('src', 'resources')
    for s in sorted(src):
        out = None

        if s.startswith('lib/'):
            out = 'eclipse-out/lib'
        elif s.startswith('plugins/'):
            if args.plugins:
                plugins.add(s)
                continue
            out = 'eclipse-out/' + s

        p = os.path.join(s, 'java')
        if os.path.exists(p):
            classpathentry('src', p, out=out)
            continue

        for env in ['main', 'test']:
            o = None
            if out:
                o = out + '/' + env
            elif env == 'test':
                o = 'eclipse-out/test'

            for srctype in ['java', 'resources']:
                p = os.path.join(s, 'src', env, srctype)
                if os.path.exists(p):
                    classpathentry('src', p, out=o)

    for libs in [lib, gwt_lib]:
        for j in sorted(libs):
            s = None
            m = srcs.match(j)
            if m:
                prefix = m.group(1)
                suffix = m.group(2)
                p = os.path.join(prefix, "jar", "%s-src.jar" % suffix)
                if os.path.exists(p):
                    s = p
            if args.plugins:
                classpathentry('lib', j, s, exported=True)
            else:
                # Filter out the source JARs that we pull through transitive
                # closure of GWT plugin API (we add source directories
                # themselves).  Exception is libEdit-src.jar, that is needed
                # for GWT SDM to work.
                m = java_library.match(j)
                if m:
                    if m.group(1).startswith("gerrit-") and \
                       j.endswith("-src.jar") and \
                       not j.endswith("libEdit-src.jar"):
                        continue
                classpathentry('lib', j, s)

    for p in sorted(proto):
        s = p.replace('-fastbuild/bin/proto/lib', '-fastbuild/genfiles/proto/')
        s = s.replace('.jar', '-src.jar')
        classpathentry('lib', p, s)

    for s in sorted(gwt_src):
        p = os.path.join(ROOT, s, 'src', 'main', 'java')
        if os.path.exists(p):
            classpathentry('lib', p, out='eclipse-out/gwtsrc')

    classpathentry('con', JRE)
    classpathentry('output', 'eclipse-out/classes')

    p = os.path.join(ROOT, '.classpath')
    with open(p, 'w') as fd:
        doc.writexml(fd, addindent='\t', newl='\n', encoding='UTF-8')

    if args.plugins:
        for plugin in plugins:
            plugindir = os.path.join(ROOT, plugin)
            try:
                gen_project(plugin.replace('plugins/', ""), plugindir)
                gen_plugin_classpath(plugindir)
            except (IOError, OSError) as err:
                print('error generating project for %s: %s' % (plugin, err),
                      file=sys.stderr)


def gen_factorypath(ext):
    doc = xml.dom.minidom.getDOMImplementation().createDocument(
        None, 'factorypath', None)
    for jar in _query_classpath(AUTO):
        e = doc.createElement('factorypathentry')
        e.setAttribute('kind', 'EXTJAR')
        e.setAttribute('id', os.path.join(ext, jar))
        e.setAttribute('enabled', 'true')
        e.setAttribute('runInBatchMode', 'false')
        doc.documentElement.appendChild(e)

    p = os.path.join(ROOT, '.factorypath')
    with open(p, 'w') as fd:
        doc.writexml(fd, addindent='\t', newl='\n', encoding='UTF-8')


try:
    ext_location = retrieve_ext_location().decode("utf-8")
    gen_project(args.project_name)
    gen_classpath(ext_location)
    gen_factorypath(ext_location)
    gen_bazel_path(ext_location)

    # TODO(davido): Remove this when GWT gone
    gwt_working_dir = ".gwt_work_dir"
    if not os.path.isdir(gwt_working_dir):
        os.makedirs(os.path.join(ROOT, gwt_working_dir))

    try:
        subprocess.check_call(_build_bazel_cmd('build', MAIN, GWT,
                              '//java/org/eclipse/jgit:libEdit-src.jar'))
    except subprocess.CalledProcessError:
        exit(1)
except KeyboardInterrupt:
    print('Interrupted by user', file=sys.stderr)
    exit(1)
