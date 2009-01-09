DOC_HTML       = $(patsubst %.txt,%.html,$(wildcard *.txt))
ASCIIDOC       = asciidoc
ASCIIDOC_EXTRA =
RM             = rm -f

all: html

html: $(DOC_HTML)

clean:
	$(RM) *.html

$(DOC_HTML): %.html : %.txt
	$(RM) $@+ $@
	$(ASCIIDOC) \
		-b xhtml11 -f asciidoc.conf \
		$(ASCIIDOC_EXTRA) -o $@+ $<
	mv $@+ $@
