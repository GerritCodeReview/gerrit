load('//tools/bzl:genrule2.bzl', 'genrule2')

CORE = [
  'commit-message-length-validator',
  'download-commands',
  'hooks',
  'replication',
  'reviewnotes',
  'singleusergroup'
]

genrule2(
  name = 'core',
  cmd = 'mkdir -p $$TMP/WEB-INF/plugins;' +
    'for s in ' +
    ' '.join(['$(location //plugins/%s:%s_deploy.jar)' % (n, n) for n in CORE]) +
    ';do ln -s $$ROOT/$$s $$TMP/WEB-INF/plugins;done;' +
    'cd $$TMP;' +
    'zip -qr $$ROOT/$@ .',
  tools = ['//plugins/%s:%s_deploy.jar' % (n, n) for n in CORE],
  out = 'core.zip',
  visibility = ['//visibility:public'],
)
