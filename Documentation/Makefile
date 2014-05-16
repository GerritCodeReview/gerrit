# Copyright (C) 2009 The Android Open Source Project
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

ASCIIDOC       ?= asciidoc
ASCIIDOC_EXTRA ?=
ASCIIDOC_VER   ?= 8.6.3
SVN            ?= svn
PUB_ROOT       ?= https://gerrit-documentation.googlecode.com/svn/Documentation

all: html

clean:
	rm -f *.html
	rm -rf $(LOCAL_ROOT)

ASCIIDOC_EXE := $(shell which $(ASCIIDOC))
ifeq ($(wildcard $(ASCIIDOC_EXE)),)
  $(error $(ASCIIDOC) must be available)
else
  ASCIIDOC_OK := $(shell expr `asciidoc --version | cut -f2 -d' '` \>= $(ASCIIDOC_VER))
  ifeq ($(ASCIIDOC_OK),0)
    $(error $(ASCIIDOC) version $(ASCIIDOC_VER) or higher is required)
  endif
endif

ifeq ($(origin VERSION), undefined)
  VERSION := $(shell ./GEN-DOC-VERSION 2>/dev/null)
endif

DOC_HTML      := $(patsubst %.txt,%.html,$(wildcard *.txt))
LOCAL_ROOT    := .published
COMMIT        := $(shell git describe HEAD | sed s/^v//)
PUB_DIR       := $(PUB_ROOT)/$(VERSION)
PRIOR          = PRIOR

ifeq ($(VERSION),)
  REVISION = $(COMMIT)
else
  ifeq ($(VERSION),$(COMMIT))
    REVISION := $(VERSION)
  else
    REVISION := $(VERSION) (from v$(COMMIT))
  endif
endif

html: $(DOC_HTML)

update: html
ifeq ($(VERSION),)
	./GEN-DOC-VERSION
endif
	@-rm -rf $(LOCAL_ROOT)
	@echo "Checking out current $(VERSION)"
	@if ! $(SVN) checkout $(PUB_DIR) $(LOCAL_ROOT) 2>/dev/null ; then \
		echo "Copying $(PRIOR) to $(VERSION) ..." && \
		$(SVN) cp -m "Create $(VERSION) documentation" $(PUB_ROOT)/$(PRIOR) $(PUB_DIR) && \
		$(SVN) checkout $(PUB_DIR) $(LOCAL_ROOT) ; \
	fi
	@rm -f $(LOCAL_ROOT)/*.html
	@cp *.html $(LOCAL_ROOT)
	@cd $(LOCAL_ROOT) && \
	  r=`$(SVN) status | perl -ne 'print if s/^!  *//' ` && \
	  if [ -n "$$r" ]; then $(SVN) rm $$r; fi && \
	  a=`$(SVN) status | perl -ne 'print if s/^\?  *//' ` && \
	  if [ -n "$$a" ]; then \
	    $(SVN) add $$a && \
	    $(SVN) propset svn:mime-type text/html $$a ; \
	    fi && \
	  echo "Committing $(VERSION) at v$(COMMIT)" && \
	  $(SVN) commit -m "Updated $(VERSION) documentation to v$(COMMIT)"
	@-rm -rf $(LOCAL_ROOT)

$(DOC_HTML): %.html : %.txt
	@echo "FORMAT $@"
	@rm -f $@+ $@
	@$(ASCIIDOC) -a toc \
		-a data-uri \
		-a 'revision=$(REVISION)' \
		-a 'newline=\n' \
		-b xhtml11 \
		-f asciidoc.conf \
		$(ASCIIDOC_EXTRA) \
		-o $@+ $<
	@mv $@+ $@
