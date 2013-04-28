gem "buildr", "~>1.4.11"

require "buildr"

require File.join(File.dirname(__FILE__), 'repositories.rb')
require File.join(File.dirname(__FILE__), 'dependencies.rb')

VERSION_NUMBER = "2.7-SNAPSHOT"

desc "Gerrit Code Review"
define "gerrit" do
  project.version = VERSION_NUMBER
  project.group = "com.google.gerrit"

  compile.options.source = "1.6"
  compile.options.target = "1.6"
  manifest["Implementation-Vendor"] = "Gerrit Code Review"

  desc "Gerrit Code Review - ReviewDB"
  define "gerrit-reviewdb" do
    compile.with GWTORM
    package(:jar)
  end

  desc "Gerrit Code Review - Patch JGit"
  define "gerrit-patch-jgit" do
    project.version = "2.3.1.201302201838-r.175-g1b4320f"
    compile.with JGIT, GWTJSONRPC, GWTUSR, GSON
    package(:jar)
  end

  desc "Gerrit Code Review - GWT expui"
  define "gerrit-gwtexpui" do
    compile.with JGIT, GWTJSONRPC, GWTUSR, GSON, GWTDEV
    package(:jar)
  end

  desc "Gerrit Code Review - Prettify"
  define "gerrit-prettify" do
    compile.with projects("gerrit-gwtexpui", "gerrit-patch-jgit", "gerrit-reviewdb"),
      JGIT, GWTJSONRPC, GWTUSR, GSON, GWTDEV
    package(:jar)
  end

end
