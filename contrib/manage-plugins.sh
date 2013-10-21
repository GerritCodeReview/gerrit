#!/bin/bash
# The MIT License
#
# Copyright (C) 2013 Sony Mobile Communications. All rights reserved.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#
# #########################################################################

# Get all core plugins.  We make the naive assumption that any entry under
# git version control in the ./plugins subfolder, beginning with a lower case
# case letter, is a plugin
#
CORE_PLUGINS=$(cd plugins && git ls-files | grep ^[a-z])

# Get all currently linked plugins.  Assume that any symbolic link in the
# ./plugins subfolder is a linked plugin.
#
LINKED_PLUGINS=$(cd plugins && find . -type l)

# Get all available plugins.  Assume that anything in the ../plugins
# subdirectory is an available plugin.
#
AVAILABLE_PLUGINS=$(cd ../plugins && ls -1)

#
# Print help.
#
function _help() {
  echo "Options / commands:"
  echo "-h/--help/help  Show this help"
  echo "ls              List all plugins"
  echo "add plugin      Add symbolic link to plugin"
  echo "rm              Remove symbolic link to plugin"
}

#
# List all plugins.
#
function _ls() {
  function do_list() {
    while test $# -gt 0; do
      echo $1
      shift
    done
  }

  echo "*** CORE PLUGINS ***"
  do_list $CORE_PLUGINS

  echo
  echo "*** LINKED PLUGINS ***"
  do_list $LINKED_PLUGINS

  echo
  echo "*** AVAILABLE PLUGINS ***"
  do_list $AVAILABLE_PLUGINS
}

#
# Add a plugin.
#
# Make a symbolic link to ../plugins/plugin-name in ./plugins/plugin-name
#
function _add() {
  plugin="$1"
  echo "add $plugin"
  ln -s ../../plugins/$plugin ./plugins/$plugin
}

#
# Remove a plugin.
#
# Remove the symbolic link in ./plugins/plugin-name
#
function _rm() {
  plugin="$1"
  echo "rm $plugin"
  rm ./plugins/$plugin
}

case "$1" in
  -h|--help|help)
    _help
    exit 0
    ;;
  ls)
    _ls
    exit 0
    ;;
  add)
    shift
    if test $# -gt 0; then
      _add "$1"
      exit 0
    else
      echo "ERROR: add: missing plugin name"
      exit 1
    fi
    ;;
  rm)
    shift
    if test $# -gt 0; then
      _rm "$1"
      exit 0
    else
      echo "ERROR: rm: missing plugin name"
      exit 1
    fi
    ;;
  *)
    echo "ERROR: Unknown or missing command"
    echo
    _help
    exit 1
esac

