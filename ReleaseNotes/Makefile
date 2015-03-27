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

DOC_HTML      := $(patsubst %.txt,%.html,$(wildcard ReleaseNotes*.txt))

all: html

html: index.html $(DOC_HTML)

clean:
	rm -f *.html

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
	 n=$$(git describe HEAD) && \
	 if ! git diff-index --quiet v$$v -- $< 2>/dev/null; then v="$$v (from $$n)"; fi && \
	 $(ASCIIDOC) --unsafe \
		-a toc \
		-a "revision=$$v" \
		-b xhtml11 -f asciidoc.conf \
		$(ASCIIDOC_EXTRA) -o $@+ $<
	@mv $@+ $@
