# Copyright (C) 2010 The Android Open Source Project
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
SVN            ?= svn
PUB_ROOT       ?= https://gerrit-documentation.googlecode.com/svn/ReleaseNotes

DOC_HTML      := $(patsubst %.txt,%.html,$(wildcard ReleaseNotes*.txt))
COMMIT        := $(shell git describe HEAD | sed s/^v//)
LOCAL_ROOT    := .published
PUB_DIR       := $(PUB_ROOT)

all: html

html: index.html $(DOC_HTML)

update: html
	@-rm -rf $(LOCAL_ROOT)
	@echo "Checking out current release notes"
	@$(SVN) checkout $(PUB_DIR) $(LOCAL_ROOT)
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
	  echo "Committing release notes at v$(COMMIT)" && \
	  $(SVN) commit -m "Updated release notes to v$(COMMIT)"
	@-rm -rf $(LOCAL_ROOT)

clean:
	rm -f *.html
	rm -rf $(LOCAL_ROOT)

index.html: index.txt
	@echo FORMAT $@
	@rm -f $@+ $@
	@$(ASCIIDOC) --unsafe \
		-a toc \
		-b xhtml11 -f asciidoc.conf \
		$(ASCIIDOC_EXTRA) -o $@+ $<
	@mv $@+ $@

$(DOC_HTML): %.html : %.txt
	@echo FORMAT $@
	@rm -f $@+ $@
	@v=$$(echo $< | sed 's/^ReleaseNotes-//;s/.txt$$//;') && \
	 c=$$(git rev-list -1 HEAD -- $<) && \
	 n=$$(git describe $$c) && \
	 if [ "X$$n" != "Xv$$v" ]; then v="$$v (from $$n)"; fi && \
	 $(ASCIIDOC) --unsafe \
		-a toc \
		-a "revision=$$v" \
		-b xhtml11 -f asciidoc.conf \
		$(ASCIIDOC_EXTRA) -o $@+ $<
	@mv $@+ $@
