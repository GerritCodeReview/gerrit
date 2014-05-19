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

all: html

clean:
	rm -f *.html

ASCIIDOC_EXE := $(shell which $(ASCIIDOC))
ifeq ($(wildcard $(ASCIIDOC_EXE)),)
  $(error $(ASCIIDOC) must be available)
else
  ASCIIDOC_OK := $(shell expr `asciidoc --version | cut -f2 -d' '` \>= $(ASCIIDOC_VER))
  ifeq ($(ASCIIDOC_OK),0)
    $(error $(ASCIIDOC) version $(ASCIIDOC_VER) or higher is required)
  endif
endif

DOC_HTML := $(patsubst %.txt,%.html,$(wildcard *.txt))
REVISION := $(shell git describe HEAD | sed s/^v//)

html: $(DOC_HTML)

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
