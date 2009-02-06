DOC_HTML       = $(patsubst %.txt,%.html,$(wildcard *.txt))
ASCIIDOC       = asciidoc
ASCIIDOC_EXTRA =
SVN            = svn
PUB_ROOT       = https://gerrit.googlecode.com/svn/documentation
LOCAL_ROOT     = .published

DOC_VERS      := $(shell git describe HEAD)
DOC_VMM       := $(shell sh -c "echo $(DOC_VERS) | perl -pe 's,^v(\d+\.\d+)[\.-].*\$$,\$$1,'")
PUB_DIR        = $(PUB_ROOT)/$(DOC_VMM)

all: html

html: $(DOC_HTML)

update: html
	-rm -rf $(LOCAL_ROOT)
	$(SVN) checkout $(PUB_DIR) $(LOCAL_ROOT)
	rm -f $(LOCAL_ROOT)/*.html
	cp *.html $(LOCAL_ROOT)
	cd $(LOCAL_ROOT) && \
	  r=`$(SVN) status | perl -ne 'print if s/^!  *//' ` && \
	  if [ -n "$$r" ]; then $(SVN) rm $$r; fi && \
	  a=`$(SVN) status | perl -ne 'print if s/^\?  *//' ` && \
	  if [ -n "$$a" ]; then \
	    $(SVN) add $$a && \
	    $(SVN) propset svn:mime-type text/html $$a ; \
	    fi && \
	  $(SVN) commit -m "Updated documentation $(DOC_VMM) to $(DOC_VERS)"
	-rm -rf $(LOCAL_ROOT)

new-docs:
	$(SVN) mkdir -m "Create documentation $(DOC_VMM)" $(PUB_DIR)

clean:
	rm -f *.html
	rm -rf $(LOCAL_ROOT)

$(DOC_HTML): %.html : %.txt
	rm -f $@+ $@
	$(ASCIIDOC) \
		-b xhtml11 -f asciidoc.conf \
		$(ASCIIDOC_EXTRA) -o $@+ $<
	mv $@+ $@
