# Copyright (C) 2013 The Android Open Source Project
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

gem "buildr", "~>1.4.11"

require "buildr"

require File.join(File.dirname(__FILE__), 'buildr/repositories.rb')
require File.join(File.dirname(__FILE__), 'buildr/dependencies.rb')
require File.join(File.dirname(__FILE__), 'buildr/antlr.rb')

Buildr.settings.build['junit'] = '4.11'

VERSION_NUMBER = "2.7-SNAPSHOT"

desc "Gerrit Code Review"
define "gerrit" do
  project.version = VERSION_NUMBER
  project.group = "com.google.gerrit"
  package_with_sources

  compile.options.source = "1.6"
  compile.options.target = "1.6"
  manifest["Implementation-Vendor"] = "Gerrit Code Review"

  desc "Gerrit Code Review - ReviewDB"
  define "gerrit-reviewdb" do
    compile.with GWTORM
    package :jar
  end

  desc "Gerrit Code Review - Patch JGit"
  define "gerrit-patch-jgit" do
    project.version = "2.3.1.201302201838-r.175-g1b4320f"
    compile.with JGIT, GSON, GWT, GWTJSONRPC
    package :jar
  end

  desc "Gerrit Code Review - GWT expui"
  define "gerrit-gwtexpui" do
    compile.with JGIT, GSON, GWT
    package :jar
  end

  desc "Gerrit Code Review - Prettify"
  define "gerrit-prettify" do
    compile.with projects("gerrit-gwtexpui", "gerrit-patch-jgit", "gerrit-reviewdb"),
    JGIT, GSON, GWT
    package :jar
  end

  desc "Gerrit Code Review - ANTLR"
  define "gerrit-antlr" do
    antlr = antlr(_('src/main/antlr3/com/google/gerrit/server/query/Query.g'),
            :in_package=>'com.google.gerrit.server.query')
    compile.from(antlr)
    compile.with ANTLR
    package :jar
  end

  desc "Gerrit Code Review - Utility - CLI"
  define "gerrit-util-cli" do
    compile.with ARGS4J, GUAVA, GUICE.guice, GUICE.assistedinject
    package :jar
  end

  desc "Gerrit Code Review - Utility - SSL"
  define "gerrit-util-ssl" do
    compile
    package :jar
  end

  desc "Gerrit Code Review - Utility - SSL"
  define "gerrit-patch-commonsnet" do
    compile.with projects("gerrit-util-ssl"),
    COMMONS.codec, COMMONS.net, SLF4J.api
    package :jar
  end

  desc "Gerrit Code Review - Extension API"
  define "gerrit-extension-api" do
    compile.with TOMCAT, GUICE
    package :jar
  end

end
