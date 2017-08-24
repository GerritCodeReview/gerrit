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

# -----------------------------------------------------------------------
# Startup script for Gerrit Inspector - a Jython introspector
# -----------------------------------------------------------------------

import sys

def print_help():
  for (n, v) in vars(sys.modules['__main__']).items():
    if not n.startswith("__") and not n in ['help', 'reload'] \
       and str(type(v)) != "<type 'javapackage'>"             \
       and not str(v).startswith("<module"):
       print "\"%s\" is \"%s\"" % (n, v)
  print
  print "Welcome to the Gerrit Inspector"
  print "Enter help() to see the above again, EOF to quit and stop Gerrit"

print_help()
