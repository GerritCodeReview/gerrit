#!/usr/bin/python
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
#
# TODO(sop): Remove hack after Buck supports Eclipse

from os import path, symlink
from sys import argv

OUT = argv[1]
ROOT = path.abspath(__file__)
for _ in range(0, 3):
  ROOT = path.dirname(ROOT)

p = path.join(ROOT, '.project')
with open(p, 'w') as fd:
  print >>fd, """\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>gerrit</name>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.jdt.core.javabuilder</name>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>\
"""
symlink(p, OUT)
