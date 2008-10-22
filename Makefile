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
#
# Define DATASTORE to the location where 'make serve' should store its
# runtime data files; by default this is /tmp/dev_appserver.datastore.
#
# Define REMOTE=1 to enable remote hosts to connect to the development
# web server started by 'make serve'.  This may be a security risk.
#
# Define EMAIL=1 to enable sending email messages during 'make serve'.
# This may spam invalid addresses, so it is off by default.
#
# Define APPID to the unique Google App Engine application instance
# 'make update' will upload the application files to.
#
# Define SDK to the location of the Google App Engine SDK download.
#
# Define PYSDK to the location of a 'python2.5' interpreter.
#
# Define ADMIN_ONLY to lock the release application build to only
# its Google App Engine administrative accounts.
#
# Define ADMIN to the email address of an admin account that can
# run appcfg.py to update the code.  This parameter is only used
# by the administrative targets.
#

ifeq ($(shell uname),Darwin)
	SDK = /usr/local/bin
else
	SDK = $(HOME)/google_appengine
endif

APPID         = gerrit-code-review-tool-demo
APPNAME_HTML  = <i>Gerrit</i> Code Review Tool

ADMIN         = set.ADMIN@read.the.docs
PYSDK         = python2.5
JAVAC         = javac -target 1.5
CPIO          = cpio -pd
DEV_APPSERVER = $(PYSDK) $(SDK)/dev_appserver.py
APPCFG        = $(PYSDK) $(SDK)/appcfg.py $(APPCFG_OPTS) -e '$(ADMIN)'

-include config.mak

ifeq ($(APPID),android-codereview)
APPNAME_HTML  = Android Code Review
endif

protobuf := ../protobuf
PROTOC   := $(abspath $(protobuf)/src/protoc)
jgit     := ../jgit
MGR_APP  := mgrapp
WEB_APP  := webapp
WEB_ARG  :=

ifdef DATASTORE
	WEB_ARG += --datastore_path=$(DATASTORE)
endif
ifeq (1,$(REMOTE))
	WEB_ARG += --address 0.0.0.0
endif
ifeq (1,$(EMAIL))
	WEB_ARG += --enable_sendmail
endif

R_WEB      := release/web
R_MGR      := release/mgr
R_PYCLIENT := release/pyclient

PYZIP_IGNORE := $(strip \
	.gitignore \
	.gitattributes \
	\*.pyc \
	\*.pyo \
)
django_IGNORE := $(strip \
	.svn \
	gis \
	admin \
	localflavor \
	mysql \
	mysql_old \
	oracle \
	postgresql \
	postgresql_psycopg2 \
	sqlite3 \
	test \
	\*.po \
	\*.mo \
)

WEB_INCLUDE := $(strip \
	__init__.py \
	app.yaml \
	index.yaml \
	main.py \
	settings.py \
	static \
	templates \
)
WEB_HASH := $(strip $(patsubst $(WEB_APP)/%,%,\
	$(wildcard $(WEB_APP)/static/*.js) \
	$(wildcard $(WEB_APP)/static/*.css) \
))
WEB_PYZIP := $(strip \
	codereview \
	django \
	froofle \
)
PYCLIENT_INCLUDE := $(strip \
	froofle \
	codereview/__init__.py \
	codereview/proto_client.py \
	codereview/*_pb2.py \
)

PROTO_SRC            := proto
PUBLIC_PROTO         := $(wildcard $(PROTO_SRC)/*.proto)
INTERNAL_PROTO       := $(wildcard $(PROTO_SRC)/internal/*.proto)

jgit_jar               := $(MGR_APP)/lib/jgit.jar
jsch_jar               := $(MGR_APP)/lib/jsch.jar
protobuf_jar           := $(MGR_APP)/lib/protobuf.jar
protobuf_jar_src       := $(protobuf)/java/src/main/java
codereview_proto_jar   := $(MGR_APP)/lib/codereview_proto.jar
codereview_manager_jar := $(MGR_APP)/lib/codereview_manager.jar

ALL_PROTO := $(strip \
	$(PUBLIC_PROTO) \
	$(INTERNAL_PROTO) \
)

MGR_LIB := $(strip \
	$(jsch_jar) \
	$(jgit_jar) \
	$(wildcard $(MGR_APP)/lib/*.jar) \
	$(protobuf_jar) \
	$(codereview_proto_jar) \
)
MGR_LIB := $(sort $(MGR_LIB))
MGR_LIB := $(filter-out $(codereview_manager_jar),$(MGR_LIB))

GEN_JAR := $(strip \
	$(jsch_jar) \
	$(jgit_jar) \
	$(protobuf_jar) \
	$(codereview_proto_jar) \
	$(codereview_manager_jar) \
	\
	$(basename $(jsch_jar))_src.zip \
	$(basename $(jgit_jar))_src.zip \
)

PROTO_PY := $(patsubst $(PROTO_SRC)/%,$(WEB_APP)/codereview/%,\
            $(patsubst %.proto,%_pb2.py,$(ALL_PROTO))) \
            $(WEB_APP)/froofle/__init__.py


## Top level targets
##

all: web mgr
release: release-web release-mgr release-pyclient

clean:
	@rm -rf release .java_src .java_bin .proto_out
	@rm -f .jgit_version
	@rm -f $(protobuf_jar_src)/com/google/protobuf/DescriptorProtos.java
	@rm -f $(GEN_JAR)
	@rm -rf $(WEB_APP)/froofle $(WEB_APP)/froofle+
	@find $(WEB_APP)/codereview -name '*.pyc' | xargs rm -f
	@find $(WEB_APP)/codereview -name '*_pb2.py' | xargs rm -f


## Web application
##

web: $(PROTO_PY)

serve: web
	$(DEV_APPSERVER) $(WEB_ARG) $(WEB_APP)

release-web: web
	@echo Building Gerrit `./GIT-VERSION-GEN` for $(APPID):
	@rm -rf $(R_WEB)
	@mkdir -p $(R_WEB)
	@cd $(WEB_APP) && \
	 $(foreach a,$(WEB_PYZIP),\
	   echo "  Packing $a.zip" && \
	   find $a \
	    $(foreach i,$(PYZIP_IGNORE), -name $i -prune -o) \
	    $(foreach i,$(value $(a)_IGNORE), -name $i -prune -o) \
	    -type f -print | \
	   zip -q9 $(abspath $(R_WEB)/$a.zip) -@ &&) \
	 echo "  Copying loose files" && \
	 find $(WEB_INCLUDE) \
	  $(foreach i,$(WEB_HASH), -path $i -prune -o) \
	  -type f -print | $(CPIO) $(abspath $(R_WEB)) && \
	 echo "  Hashing loose files" && \
	 $(foreach i,$(WEB_HASH),\
	   h=$$(openssl sha1 <$i) && \
	   d=$(basename $i)-$$h$(suffix $i) && \
	   echo "    $$d" && \
	   cp $i $(abspath $(R_WEB))/$$d && \
	   find $(abspath $(R_WEB)/templates) -type f \
	   | xargs perl -pi -e "s,$i,$$d,g" && ) \
	 true
	@./GIT-VERSION-GEN >$(R_WEB)/static/application_version
	@echo "This is Gerrit `./GIT-VERSION-GEN`" >$(R_WEB)/templates/live_revision.html
	@perl -pi -e 's{PUBLIC APPLICATION NAME}{$(APPNAME_HTML)}' $(R_WEB)/templates/base.html
	@perl -pi -e 's/(application:).*/$$1 $(APPID)/' $(R_WEB)/app.yaml
ifdef ADMIN_ONLY
	@echo '*** SETTING ADMIN_ONLY ***'
	@perl -pi -e 's/^(.*)(script:.*main.py)/$$1login: admin\n$$1$$2/' $(R_WEB)/app.yaml
endif
	@echo $(R_WEB) built for $(APPID).

update: release-web
	$(APPCFG) update $(R_WEB)

version:
	@printf '%s = ' '$(APPID)'
	@curl http://$(APPID).appspot.com/application_version

## Python RPC client
##

release-pyclient: $(PROTO_PY)
	@echo Building Python RPC client $(R_PYCLIENT)
	@rm -rf $(R_PYCLIENT)
	@mkdir -p $(R_PYCLIENT)
	@cd $(WEB_APP) && \
	 find $(PYCLIENT_INCLUDE) \
	   $(foreach i,$(PYZIP_IGNORE), -name $i -prune -o) \
	   -type f -print \
	 | $(CPIO) $(abspath $(R_PYCLIENT))
	@echo "__version__ = '`./GIT-VERSION-GEN`'" >>$(R_PYCLIENT)/codereview/__init__.py


## Manager backend
##

mgr: $(codereview_manager_jar)

release-mgr: mgr
	@rm -rf $(R_MGR)
	@mkdir -p $(R_MGR)
	@cd $(MGR_APP) && \
	 find bin \
	  $(foreach i,.gitignore .gitattributes, -name $i -prune -o) \
	  -type f -print \
	 | $(CPIO) $(abspath $(R_MGR)) && \
	 find lib -name \*.jar -type f -print \
	 | $(CPIO) $(abspath $(R_MGR))
	@echo $(R_MGR) built for `./GIT-VERSION-GEN`


## Basic productions
##

$(PROTOC): .protobuf_version $(protobuf)/Makefile
	$(MAKE) -C $(protobuf)
	@touch $(PROTOC)
$(protobuf)/configure: $(protobuf)/configure.ac .protobuf_version
	cd $(protobuf) && ./autogen.sh
$(protobuf)/Makefile: $(protobuf)/configure .protobuf_version
	cd $(protobuf) && ./configure

$(WEB_APP)/froofle/__init__.py: .protobuf_version $(PROTOC)
	@echo Updating $(WEB_APP)/froofle ...
	@rm -rf $(WEB_APP)/froofle $(WEB_APP)/froofle+
	@mkdir $(WEB_APP)/froofle+
	@cd $(protobuf)/python/google && \
	 find . \
	 $(foreach i,test_util.py \*_test.py, -name $i -prune -o) \
	 -type f -name \*.py -print \
	 | $(CPIO) $(abspath $(WEB_APP)/froofle+)
	@cd $(protobuf)/python && \
	 t=$(abspath .proto_out) && \
	 rm -rf $$t && \
	 mkdir -p $$t/google/protobuf && \
	 $(PROTOC) -I../src -I. \
	           --python_out=$$t \
	           ../src/google/protobuf/descriptor.proto
	@cp .proto_out/google/protobuf/descriptor_pb2.py \
	    $(WEB_APP)/froofle+/protobuf
	@rm -rf .proto_out
	@find $(WEB_APP)/froofle+ -name __init__.py | xargs perl -ni -e ''
	@find $(WEB_APP)/froofle+ -name '*.py' | xargs chmod a-x
	@find $(WEB_APP)/froofle+ -name '*.py' \
	 | xargs perl -pi -e 's/google(?!\.com)/froofle/g'
	@mv $(WEB_APP)/froofle+ $(WEB_APP)/froofle

$(WEB_APP)/codereview/%_pb2.py: $(PROTO_SRC)/%.proto $(PROTOC)
	@echo protoc $< && \
	 $(PROTOC) --python_out=$(dir $@) --proto_path=$(dir $<) $< && \
	 perl -pi -e 's/google(?!\.com)/froofle/g' $@

$(codereview_manager_jar): \
		$(MGR_LIB) \
		$(shell find $(MGR_APP)/src -type f)
	@echo build $@
	@rm -rf .java_bin && \
	 b=$(abspath .java_bin) && \
	 cd $(MGR_APP)/src && \
	 mkdir $$b && \
	 mkdir $$b/META-INF && \
	 find META-INF/MANIFEST.MF | $(CPIO) $$b && \
	 CLASSPATH=$$b && \
	 $(foreach c,$(MGR_LIB),CLASSPATH=$$CLASSPATH:$(abspath $c) &&) \
	 export CLASSPATH && \
	 find . -name \*.java | xargs $(JAVAC) \
		-encoding UTF-8 \
		-g \
		-d $$b
	@cd .java_bin && jar cf $(abspath $@) .
	@rm -rf .java_bin

$(codereview_proto_jar): $(ALL_PROTO) $(protobuf_jar) $(PROTOC)
	@echo build $@
	@rm -rf .java_src .java_bin && \
	 mkdir .java_src .java_bin && \
	 $(foreach p,$(ALL_PROTO),\
	  $(PROTOC) --java_out=.java_src --proto_path=$(dir $p) $p &&) \
	 unset CLASSPATH && \
	 cd .java_src && \
	 find . -name '*.java' | xargs $(JAVAC) \
		-encoding UTF-8 \
		-g:none \
		-nowarn \
		-classpath $(abspath $(protobuf_jar)) \
		-d ../.java_bin && \
	 cd ../.java_bin && jar cf $(abspath $@) .
	@rm -rf .java_src .java_bin

$(protobuf_jar): \
		$(protobuf)/src/google/protobuf/descriptor.proto \
		$(shell find $(protobuf_jar_src) -type f)
	@echo build $@
	@rm -rf .java_bin && \
	 b=$(abspath .java_bin) && \
	 cd $(protobuf_jar_src) && \
	 mkdir $$b && \
	 CLASSPATH=$$b && \
	 export CLASSPATH && \
	 $(PROTOC) \
		--java_out=. \
		-I$(abspath $(protobuf)/src) \
		$(abspath $(protobuf)/src/google/protobuf/descriptor.proto) && \
	 find . -name \*.java | xargs $(JAVAC) \
		-encoding UTF-8 \
		-g \
		-d $$b
	@cd .java_bin && jar cf $(abspath $@) .
	@rm -f $(protobuf_jar_src)/com/google/protobuf/DescriptorProtos.java
	@rm -rf .java_bin

$(jsch_jar): $(jgit)/org.spearce.jgit/lib/jsch-0.1.37.jar
	rm -f $@ $(basename $@)_src.zip
	cp $< $@
	cp $(basename $<).zip $(basename $@)_src.zip

$(jgit_jar): .jgit_version
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

.protobuf_version: protobuf_phony
	@a=`git --git-dir=$(protobuf)/.git rev-parse HEAD 2>/dev/null`; \
	 b=`cat .protobuf_version 2>/dev/null`; \
	 if test z$$a = z$$b; then : up to date; \
	 else echo $$a >$@; fi

.PHONY: all clean release
.PHONY: web release-web
.PHONY: mgr release-mgr
.PHONY: protobuf_phony
.PHONY: jgit_phony
