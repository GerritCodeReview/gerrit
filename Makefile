# Gerrit
#
# Copyright 2008 Google Inc.
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
# Define GWT_SDK to the location of the Google Web Toolkit SDK.
#
# Define GEN_DEBUG to save the generated Java code from GWT.
#

uname_S := $(shell sh -c 'uname -s 2>/dev/null || echo not')

JAVA       = java
JAVAC      = javac
JAR        = jar
JAVA_ARGS  = -Xmx265m
CPIO       = cpio -pd
GWT_OS     = unknown
GWT_FLAGS  =

gwtjsonrpc = ../gwtjsonrpc
gwtorm     = ../gwtorm
jgit       = ../jgit

ifeq ($(uname_S),Darwin)
	GWT_OS = mac
endif
ifeq ($(uname_S),Linux)
	GWT_OS = linux
endif
ifeq ($(uname_S),Cygwin)
	GWT_OS = win
endif

-include config.mak

WEBAPP   := $(abspath webapp)
GWT_SDK  := $(abspath $(GWT_SDK))
WEB_MAIN := com.google.gerrit.Gerrit
GWT_CP   = \
	$(GWT_SDK)/gwt-user.jar \
	$(GWT_SDK)/gwt-dev-$(GWT_OS).jar \
#end GWT_CP

WEB_LIB_GEN = \
	$(WEBAPP)/lib/antlr.jar \
	$(WEBAPP)/lib/asm.jar \
	$(WEBAPP)/lib/commons-codec.jar \
	$(WEBAPP)/lib/gson.jar \
	$(WEBAPP)/lib/gwtjsonrpc.jar \
	$(WEBAPP)/lib/gwtorm.jar \
	$(WEBAPP)/lib/jdbc-h2.jar \
    $(WEBAPP)/lib/jgit.jar \
    $(WEBAPP)/lib/jsch.jar \
#end WEB_LIB_GEN

ALL_LIB = $(filter-out $(WEBAPP)/lib/jdbc-h2.jar, \
	$(GWT_SDK)/gwt-servlet.jar \
	$(WEBAPP)/lib/dyuproject-openid.jar \
	$(WEBAPP)/lib/dyuproject-util.jar \
	$(WEBAPP)/lib/jetty-util.jar \
	$(WEB_LIB_GEN) \
	)
#end ALL_LIB

ALL_JDBC = $(wildcard $(WEBAPP)/lib/jdbc-*.jar)

ifdef GEN_DEBUG
	GWT_FLAGS += -gen gensrc
endif

MY_JAVA := $(shell find $(WEBAPP)/src -name \*.java)
MY_RSRC := $(shell find $(WEBAPP)/src \
     -name \*.css \
  -o -name \*.gif \
  -o -name \*.html \
  -o -name \*.png \
  -o -name \*.properties \
  )
MY_JAR  := gerrit-server.jar
MY_WAR  := gerrit.war
MY_WXML := $(WEBAPP)/src/com/google/gerrit/web.xml
MY_NCJS := $(WEBAPP)/www/$(WEB_MAIN)/$(WEB_MAIN).nocache.js

all: $(MY_WAR)

clean:
	rm -rf $(MY_JAR) $(MY_WAR) .bin
	rm -f $(WEB_LIB_GEN)
	rm -f .jgit_version
	rm -rf $(WEBAPP)/gensrc
	rm -rf $(WEBAPP)/classes
	rm -rf $(WEBAPP)/www
	rm -rf $(WEBAPP)/tomcat

clean-h2db:
	rm -f $(WEBAPP)/ReviewDb.*.db
.PHONY: clean-h2db

$(MY_JAR): $(MY_JAVA) $(ALL_LIB)
	rm -rf .bin
	mkdir .bin
	CLASSPATH= && \
	$(foreach p,$(ALL_LIB) $(GWT_SDK)/gwt-user.jar,CLASSPATH="$$CLASSPATH:$p" &&) \
	export CLASSPATH && \
	cd $(WEBAPP)/src && $(JAVAC) \
		-encoding utf-8 \
		-source 1.5 \
		-target 1.5 \
		-g \
		-d "$(abspath .bin)" \
		$(patsubst $(WEBAPP)/src/%,%,$(MY_JAVA))
	cd .bin && $(JAR) cf ../$(MY_JAR) .
	rm -rf .bin

$(MY_WAR): $(MY_NCJS) $(ALL_LIB) $(MY_JAR) $(MY_WXML)
	rm -rf .bin
	mkdir -p .bin/WEB-INF/lib
	cd $(WEBAPP)/www/$(WEB_MAIN) && find . | $(CPIO) $(abspath .bin)
	for p in $$(find .bin -type f); do\
	  case $$p in\
	  *.png) : skip ;;\
	  *) gzip -9c $$p > $$p.gz || exit ;;\
	  esac;\
	done
	$(foreach p,$(ALL_LIB) $(MY_JAR),cp $p .bin/WEB-INF/lib &&) :
	cp $(MY_WXML) .bin/WEB-INF
	mkdir -p .bin/WEB-INF/classes/com/google/gerrit/public
	$(foreach p,Gerrit.html SetCookie.html,\
	  rm .bin/$p.gz && \
	  mv .bin/$p .bin/WEB-INF/classes/com/google/gerrit/public &&) :
	cd .bin && $(JAR) cf ../$(MY_WAR) .
	rm -rf .bin

$(MY_NCJS): \
		$(MY_JAR) \
		$(MY_JAVA) \
		$(MY_RSRC) \
		$(WEBAPP)/src/com/google/gerrit/Gerrit.gwt.xml
	CLASSPATH=src:$(abspath $(MY_JAR)) && \
	$(foreach p,$(GWT_CP) $(ALL_LIB),CLASSPATH=$$CLASSPATH:$p &&) \
	export CLASSPATH && \
	cd $(WEBAPP) && $(JAVA) $(JAVA_ARGS) \
		com.google.gwt.dev.GWTCompiler \
		-out www \
		$(GWT_FLAGS) \
		$(WEB_MAIN)

web-shell: $(MY_JAR) web-lib
	CLASSPATH=src:$(abspath $(MY_JAR)) && \
	$(foreach p,$(GWT_CP) $(ALL_LIB) $(ALL_JDBC),CLASSPATH=$$CLASSPATH:$p &&) \
	export CLASSPATH && \
	cd $(WEBAPP) && $(JAVA) $(JAVA_ARGS) \
		com.google.gwt.dev.GWTShell \
		-out www \
		$(WEB_MAIN)/Gerrit.html

web-lib: $(WEB_LIB_GEN)

$(WEBAPP)/lib/gwtjsonrpc.jar: $(gwtjsonrpc)/lib/gwtjsonrpc.jar
	cp $< $@
$(WEBAPP)/lib/gson.jar: $(gwtjsonrpc)/lib/gson.jar
	cp $< $@
$(WEBAPP)/lib/commons-codec.jar: $(gwtjsonrpc)/lib/commons-codec.jar
	cp $< $@
$(gwtjsonrpc)/lib/gwtjsonrpc.jar: make-gwtjsonrpc
	$(MAKE) -C $(gwtjsonrpc) GWT_SDK=$(GWT_SDK)
.PHONY: make-gwtjsonrpc

$(WEBAPP)/lib/gwtorm.jar: $(gwtorm)/lib/gwtorm.jar
	cp $< $@
$(WEBAPP)/lib/antlr.jar: $(gwtorm)/lib/antlr.jar
	cp $< $@
$(WEBAPP)/lib/asm.jar: $(gwtorm)/lib/asm.jar
	cp $< $@
$(WEBAPP)/lib/jdbc-h2.jar: $(gwtorm)/lib/jdbc-h2.jar
	cp $< $@
$(gwtorm)/lib/gwtorm.jar: make-gwtorm
	$(MAKE) -C $(gwtorm) GWT_SDK=$(GWT_SDK)
.PHONY: make-gwtorm

$(WEBAPP)/lib/jsch.jar: $(jgit)/org.spearce.jgit/lib/jsch-0.1.37.jar
	cp $< $@
$(WEBAPP)/lib/jgit.jar: .jgit_version
	rm -f $@ $(basename $@)_src.zip
	cd $(jgit) && $(SHELL) ./make_jgit.sh
	cp $(jgit)/jgit.jar $@
	chmod 644 $@
	cp $(jgit)/jgit_src.zip $(basename $@)_src.zip

.jgit_version: jgit_phony
	@a=`git --git-dir=$(jgit)/.git rev-parse HEAD 2>/dev/null`; \
	 b=`cat .jgit_version 2>/dev/null`; \
	 if test z$$a = z$$b; then : up to date; \
	 else echo $$a >$@; fi
.PHONY: jgit_phony

.PHONY: all
.PHONY: clean
.PHONY: web web-shell web-lib
