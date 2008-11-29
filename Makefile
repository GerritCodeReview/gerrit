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

WEBAPP   = webapp
WEB_MAIN = com.google.gerrit.Gerrit
GWT_CP   = \
	$(GWT_SDK)/gwt-user.jar \
	$(GWT_SDK)/gwt-dev-$(GWT_OS).jar \
	lib/gwtjsonrpc.jar \
	lib/gwtorm.jar \
#end GWT_CP

WEB_LIB_GEN = \
	$(WEBAPP)/lib/antlr.jar \
	$(WEBAPP)/lib/asm.jar \
	$(WEBAPP)/lib/commons-codec.jar \
	$(WEBAPP)/lib/gson.jar \
	$(WEBAPP)/lib/gwtjsonrpc.jar \
	$(WEBAPP)/lib/gwtorm.jar \
	$(WEBAPP)/lib/jdbc-h2.jar \
#end WEB_LIB_GEN

ALL_LIB = \
	$(GWT_SDK)/gwt-servlet.jar \
	$(WEBAPP)/lib/dyuproject-openid.jar \
	$(WEBAPP)/lib/dyuproject-util.jar \
	$(WEBAPP)/lib/jetty-util.jar \
	$(wildcard $(WEBAPP)/lib/jdbc-*.jar) \
	$(WEB_LIB_GEN)
#end ALL_LIB

ifdef GEN_DEBUG
	GWT_FLAGS += -gen gensrc
endif

MY_JAVA := $(shell find webapp/src -name \*.java)
MY_JAR  := gerrit-server.jar
MY_WAR  := gerrit.war
MY_WXML := $(WEBAPP)/src/com/google/gerrit/web.xml
MY_NCJS := $(WEBAPP)/www/$(WEB_MAIN)/$(WEB_MAIN).nocache.js

all: $(MY_WAR)

clean:
	rm -rf $(MY_JAR) $(MY_WAR) .bin
	rm -f $(WEB_LIB_GEN)
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
	$(foreach p,$(ALL_LIB) $(GWT_SDK)/gwt-user.jar,CLASSPATH="$$CLASSPATH:$(abspath $p)" &&) \
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
	$(foreach p,$(ALL_LIB) $(MY_JAR),cp $p .bin/WEB-INF/lib &&) :
	cd $(WEBAPP)/www/$(WEB_MAIN) && find . | $(CPIO) $(abspath .bin)
	cp $(MY_WXML) .bin/WEB-INF
	mkdir -p .bin/WEB-INF/classes/com/google/gerrit/public
	mv .bin/Gerrit.html .bin/WEB-INF/classes/com/google/gerrit/public
	cd .bin && $(JAR) cf ../$(MY_WAR) .
	rm -rf .bin

$(MY_NCJS): \
		$(MY_JAVA) \
		$(WEBAPP)/src/com/google/gerrit/Gerrit.gwt.xml \
		$(WEB_LIB_GEN)
	CLASSPATH=src:classes && \
	$(foreach p,$(GWT_CP),CLASSPATH=$$CLASSPATH:$p &&) \
	export CLASSPATH && \
	cd $(WEBAPP) && $(JAVA) $(JAVA_ARGS) \
		com.google.gwt.dev.GWTCompiler \
		-out www \
		$(GWT_FLAGS) \
		$(WEB_MAIN)

web-shell: $(WEB_LIB_GEN)
	CLASSPATH=src:classes && \
	$(foreach p,$(GWT_CP),CLASSPATH=$$CLASSPATH:$p &&) \
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

.PHONY: all
.PHONY: clean
.PHONY: web web-shell web-lib
