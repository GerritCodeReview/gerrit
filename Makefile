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
JAVA_ARGS  = -Xmx265m
GWT_OS     = unknown
GWT_FLAGS  =
gwtjsonrpc = ../gwtjsonrpc

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
	lib/gwtjsonrpc.jar
#end GWT_CP

ifdef GEN_DEBUG
	GWT_FLAGS += -gen gensrc
endif

all: web

clean:
	rm -f $(WEBAPP)/lib/gwtjsonrpc.jar
	rm -f $(WEBAPP)/lib/gson.jar
	rm -f $(WEBAPP)/lib/commons-codec.jar
	rm -rf $(WEBAPP)/gensrc
	rm -rf $(WEBAPP)/classes
	rm -rf $(WEBAPP)/www
	rm -rf $(WEBAPP)/tomcat

web: web-lib
	CLASSPATH=src:classes && \
	$(foreach p,$(GWT_CP),CLASSPATH=$$CLASSPATH:$p &&) \
	export CLASSPATH && \
	cd $(WEBAPP) && $(JAVA) $(JAVA_ARGS) \
		com.google.gwt.dev.GWTCompiler \
		-out www \
		$(GWT_FLAGS) \
		$(WEB_MAIN)

web-shell: web-lib
	CLASSPATH=src:classes && \
	$(foreach p,$(GWT_CP),CLASSPATH=$$CLASSPATH:$p &&) \
	export CLASSPATH && \
	cd $(WEBAPP) && $(JAVA) $(JAVA_ARGS) \
		com.google.gwt.dev.GWTShell \
		-out www \
		$(WEB_MAIN)/Gerrit.html

web-lib: \
	$(WEBAPP)/lib/gson.jar \
	$(WEBAPP)/lib/commons-codec.jar \
	$(WEBAPP)/lib/gwtjsonrpc.jar

$(WEBAPP)/lib/gwtjsonrpc.jar: $(gwtjsonrpc)/lib/gwtjsonrpc.jar
	cp $< $@
$(WEBAPP)/lib/gson.jar: $(gwtjsonrpc)/lib/gson.jar
	cp $< $@
$(WEBAPP)/lib/commons-codec.jar: $(gwtjsonrpc)/lib/commons-codec.jar
	cp $< $@
$(gwtjsonrpc)/lib/gwtjsonrpc.jar: make-gwtjsonrpc
	$(MAKE) -C $(gwtjsonrpc) GWT_SDK=$(GWT_SDK)
.PHONY: make-gwtjsonrpc

.PHONY: all
.PHONY: clean
.PHONY: web web-shell web-lib
