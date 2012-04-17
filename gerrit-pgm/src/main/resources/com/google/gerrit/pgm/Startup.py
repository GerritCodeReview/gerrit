# Copyright (C) 2012, Marcin Cieslak <saper@saper.info>
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
import com.google
import com.google.gerrit.server.schema.DataSourceProvider
import com.google.gerrit.sshd.SshLog
import com.google.gerrit.sshd.SshDaemon
import com.google.gerrit.server.schema.SchemaVersionCheck
import com.google.inject.Guice as Guice

# LifecycleManager has following instances registered:
#
# <type 'com.google.gerrit.pgm.util.ErrorLogFile$1'>
# <type 'com.google.gerrit.server.schema.DataSourceProvider'>
# <type 'com.google.gerrit.server.git.LocalDiskRepositoryManager$Lifecycle'>
# <type 'com.google.gerrit.server.schema.SchemaVersionCheck'>
# <type 'com.google.gerrit.pgm.util.LogFileCompressor$Lifecycle'>
# <type 'com.google.gerrit.server.git.WorkQueue$Lifecycle'>
# <type 'com.google.gerrit.ehcache.EhcachePoolImpl$Lifecycle'>
# <type 'com.google.gerrit.server.config.MasterNodeStartup$OnStart'>
# <type 'com.google.gerrit.sshd.commands.ShowCaches$StartupListener'>
# <type 'com.google.gerrit.sshd.SshLog'>
# <type 'com.google.gerrit.sshd.SshDaemon'>
# <type 'com.google.gerrit.pgm.http.jetty.JettyServer$Lifecycle'>
for x in m.listeners:
  if type(x) == com.google.gerrit.server.schema.DataSourceProvider:
    ds = x.ds
  if type(x) == com.google.gerrit.sshd.SshLog:
    sshlog = x
  if type(x) == com.google.gerrit.sshd.SshDaemon:
    sshd = x
  if type(x) == com.google.gerrit.server.schema.SchemaVersionCheck:
    schk = x
    orm = schk.schema
    try:
      db.close()
    except NameError:
      pass
    db = orm.open()
  if str(type(x)) == "<type 'com.google.gerrit.pgm.http.jetty.JettyServer$Lifecycle'>":
    jettyserver = x.server
    httpd = jettyserver.httpd
    site = jettyserver.site
    baseResource = jettyserver.baseResource
  if str(type(x)) == "<type 'com.google.gerrit.ehcache.EhcachePoolImpl$Lifecycle'>":
    ehcache = x.cachePool
  if str(type(x)) == "<type 'com.google.gerrit.server.git.WorkQueue$Lifecycle'>":
    wq = x.workQueue
  if str(type(x)) == "<type 'com.google.gerrit.server.git.LocalDiskRepositoryManager$Lifecycle'>":
    gitconfig = x.cfg
del(x)
def help():
  for (n, v) in vars(sys.modules['__main__']).items():
    if not n.startswith("__") and not n in ['help', 'reload'] \
       and str(type(v)) != "<type 'javapackage'>"             \
       and not str(v).startswith("<module"):
       print "\"%s\" is \"%s\"" % (n, v)
  print
  print "Welcome to the Gerrit Inspector"
  print "Enter help() to see the above again, EOF to quit and stop Gerrit"

help()
