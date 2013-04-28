guiceVersion = '3.0'
gwtormVersion = '1.6'
gwtjsonrpcVersion = '1.3'
slf4jVersion = '1.6.1'
bouncyCastleVersion = '140'
jgitVersion = '2.3.1.201302201838-r.175-g1b4320f'
jettyVersion = '8.1.7.v20120910'

GWTORM         = "gwtorm:gwtorm:jar:#{gwtormVersion}"

JGIT           = struct(
   :core       => "org.eclipse.jgit:org.eclipse.jgit:jar:#{jgitVersion}",
   :http       => "org.eclipse.jgit:org.eclipse.jgit.http.server:jar:#{jgitVersion}",
   :junit      => "org.eclipse.jgit:org.eclipse.jgit.junit:jar:#{jgitVersion}",
   :ewah       => "com.googlecode.javaewah:JavaEWAH:jar:0.5.6"
)

GWTJSONRPC     = "gwtjsonrpc:gwtjsonrpc:jar:#{gwtjsonrpcVersion}"

GWT            = [ group("gwt-user", "gwt-dev", "gwt-servlet",
                         :under=>"com.google.gwt", :version=>"2.5.0") ]
GSON           = "com.google.code.gson:gson:jar:2.1"

EASYMOCK       = struct(
   :core       => "org.easymock:easymock:jar:3.0",
   :cglibnodep => "cglib:cglib-nodep:jar:2.2",
   :objenesis  => "org.objenesis:objenesis:jar:1.2"
)
TOMCAT         = "org.apache.tomcat:servlet-api:jar:6.0.29"
TOMCAT3        = "org.apache.tomcat:tomcat-servlet-api:jar:7.0.32"

JSR305         = "com.google.code.findbugs:jsr305:jar:1.3.9"

JCRAFT         = "com.jcraft:jsch:jar:0.1.44-1"

ANTLR          = [ group("antlr", "antlr-runtime", "stringtemplate",
                         :under=>"org.antlr", :version=>"3.2") ]
ARGS4J         = "args4j:args4j:jar:2.0.16"
GUAVA          = "com.google.guava:guava:jar:14.0"
GUICE          = struct(
  :guice       =>"com.google.inject:guice:jar:#{guiceVersion}",
  :servlet     =>"com.google.inject.extensions:guice-servlet:jar:#{guiceVersion}",
  :assistedinject => "com.google.inject.extensions:guice-assistedinject:jar:#{guiceVersion}",
  :javaxinject => "javax.inject:javax.inject:jar:1",
  :sisuinject  => "org.sonatype.sisu.inject:cglib:jar:2.2.1-v20090111",
  :ow2asm      => "org.ow2.asm:asm:jar:4.0"
)

COMMONS         = struct(
  :codec        =>"commons-codec:commons-codec:jar:1.4",
  :net          =>"commons-net:commons-net:jar:2.2",
  :dbcp         =>"commons-dbcp:commons-dbcp:jar:1.4",
  :lang         =>"commons-lang:commons-lang:jar:2.5"
)

COMMONSPOOL     = "commons-pool:commons-pool:jar:1.5.5"
COMMONSCOLLECTION  = "commons-collections:commons-collections:jar:3.2.1"
COMMONSLOGGING  = ""

LOG4J           = "log4j:log4j:jar:1.2.16"

SLF4J           = struct(
  :log4j12      =>"org.slf4j:slf4j-log4j12:jar:#{slf4jVersion}",
  :api          =>"org.slf4j:slf4j-api:jar:#{slf4jVersion}",
)

H2              = "com.h2database:h2:jar:1.3.168"

VELOCITY        = "org.apache.velocity:velocity:jar:1.6.4"

BOUNCYCASTLE    = struct(
  :core         =>"bouncycastle:bcpg-jdk15:jar:#{bouncyCastleVersion}",
  :provider     =>"bouncycastle:bcprov-jdk15:jar:#{bouncyCastleVersion}"
)

MIMEUTIL        = "eu.medsea.mimeutil:mime-util:jar:2.1.3"

AOP             = "aopalliance:aopalliance:jar:1.0"
CHARDET         = "com.googlecode.juniversalchardet:juniversalchardet:jar:1.0.3"
BRICS           = "dk.brics.automaton:automaton:jar:1.11.8"

PROLOGCAFE      = "com.googlecode.prolog-cafe:PrologCafe:jar:1.3"

PEGDOWN         = "org.pegdown:pegdown:jar:1.1.0"

PARABOILED      = [ group("parboiled-core", "parboiled-java",
                         :under=>"org.parboiled", :version=>"1.1.3") ]

OPENID4JAVA     = struct(
   :core        => "org.openid4java:openid4java-nodeps:jar:0.9.6",
   :logging     => "commons-logging:commons-logging:jar:1.1.1",
   :client      => "org.apache.httpcomponents:httpclient:jar:4.0",
   :httpcore    => "org.apache.httpcomponents:httpcore:jar:4.0.1",
   :nekohtml    => "net.sourceforge.nekohtml:nekohtml:jar:1.9.10",
   :xerces      => "xerces:xercesImpl:jar:2.8.1"
)

APACHEMINA      = "org.apache.mina:mina-core:jar:2.0.5"
APACHESSHD      = "org.apache.sshd:sshd-core:jar:0.6.0"

JETTY           = [ group("jetty-servlet", "jetty-security", "jetty-server",
                          "jetty-continuation", "jetty-http", "jetty-io",
                          "jetty-util",
                         :under=>"org.eclipse.jetty", :version=>"#{jettyVersion}") ]
