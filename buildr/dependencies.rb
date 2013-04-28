guiceVersion = '3.0'
gwtormVersion = '1.6'
gwtjsonrpcVersion = '1.3'
slf4jVersion = '1.6.1'
bouncyCastleVersion = '140'
jgitVersion = '2.3.1.201302201838-r.175-g1b4320f'

GWTORM         = "gwtorm:gwtorm:jar:#{gwtormVersion}"
JGIT           = "org.eclipse.jgit:org.eclipse.jgit:jar:#{jgitVersion}"
GWTJSONRPC     = "gwtjsonrpc:gwtjsonrpc:jar:#{gwtjsonrpcVersion}"

GWT            = [ group("gwt-user", "gwt-dev", "gwt-servlet",
                         :under=>"com.google.gwt", :version=>"2.5.0") ]
GSON           = "com.google.code.gson:gson:jar:2.1"
EASYMOCK       = "org.easymock:easymock:jar:3.0"
TOMCAT         = "org.apache.tomcat:servlet-api:jar:6.0.29"
JSR305         = "com.google.code.findbugs:jsr305:jar:1.3.9"

ANTLR          = [ group("antlr", "antlr-runtime", "stringtemplate",
                         :under=>"org.antlr", :version=>"3.2") ]
ARGS4J         = "args4j:args4j:jar:2.0.16"
GUAVA          = "com.google.guava:guava:jar:14.0"
GUICE          = struct(
  :guice       =>"com.google.inject:guice:jar:#{guiceVersion}",
  :servlet     =>"com.google.inject.extensions:guice-servlet:jar:#{guiceVersion}",
  :assistedinject => "com.google.inject.extensions:guice-assistedinject:jar:#{guiceVersion}"
)

COMMONS         = struct(
  :codec        =>"commons-codec:commons-codec:jar:1.4",
  :net          =>"commons-net:commons-net:jar:2.2"
)

SLF4J           = struct(
  :log4j12      =>"org.slf4j:slf4j-log4j12:jar:#{slf4jVersion}",
  :api          =>"org.slf4j:slf4j-api:jar:#{slf4jVersion}",
)
