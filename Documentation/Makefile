ifeq ($(shell uname),Darwin)
	SDK = /usr/local/bin
else
	SDK = $(HOME)/google_appengine
endif

DOC_HTML       = $(patsubst %.txt,%.html,$(wildcard *.txt))
ASCIIDOC       = asciidoc
ASCIIDOC_EXTRA =
PYSDK          = python2.5
APPCFG         = $(PYSDK) $(SDK)/appcfg.py $(APPCFG_OPTS) -e '$(ADMIN)'
R_WEB          = release-gae
APPID          = gerrit-documentation

-include config.mak

all: html

html: $(DOC_HTML)

update: html
	@rm -rf $(R_WEB)
	@mkdir -p $(R_WEB)/Documentation
	cp app.yaml redirect.py $(R_WEB)
	cp ../appjar/src/main/java/com/google/gerrit/public/favicon.ico $(R_WEB)
	cp *.html $(R_WEB)/Documentation
	git describe HEAD >$(R_WEB)/application_version
	perl -pi -e 's/(application:).*/$$1 $(APPID)/' $(R_WEB)/app.yaml
	$(APPCFG) update $(R_WEB)

clean:
	rm -f *.html $(R_WEB)

$(DOC_HTML): %.html : %.txt
	rm -f $@+ $@
	$(ASCIIDOC) \
		-b xhtml11 -f asciidoc.conf \
		$(ASCIIDOC_EXTRA) -o $@+ $<
	mv $@+ $@
