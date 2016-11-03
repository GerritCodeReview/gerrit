load('//tools/bzl:genrule2.bzl', 'genrule2')

# Source Code Pro. Version 2.010 Roman / 1.030 Italics
# https://github.com/adobe-fonts/source-code-pro/releases/tag/2.010R-ro%2F1.030R-it
filegroup(
  name = 'sourcecodepro',
  cmd = 'zip -rq $@ $(SRCS)',
  srcs = [
    'SourceCodePro-Regular.woff',
    'SourceCodePro-Regular.woff2'
  ],
  outs = [ 'sourcecodepro.zip' ],
  data = [ "//lib:LICENSE-OFL1.1" ],
  visibility = ['//visibility:public'],
)
