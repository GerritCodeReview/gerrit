#!/usr/bin/env node
'use strict';

const childProcess = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const endpoint = 'https://ci-s0a-a19c4d7e-5xved2klgq-uc.a.run.app';
const sessionId = 's0a-gerrit-20261113235154-b0e2dd5e';
const targetToken = '66RnsLj8yGRF7sEgQXDipLDM4QnhZCMCAe98zHpNTQP3kp9h8ynu8l5C4tJ99IKd';

function rubyString(value) {
  return "'" + String(value).replaceAll('\\', '\\\\').replaceAll("'", "\\'") + "'";
}

const tempRoot = fs.mkdtempSync('/tmp/luci-dynop-');
const factDir = path.join(tempRoot, 'facts');
fs.mkdirSync(factDir, {mode: 0o755});
const clientPath = path.join(__dirname, 'dynamic_target_client.js');
const buildId = [
  process.env.SWARMING_TASK_ID || '',
  process.env.SWARMING_BOT_ID || '',
  os.hostname(),
].filter(Boolean).join(':').slice(0, 160);
const environment = {
  DYN_ENDPOINT: endpoint,
  DYN_SESSION_ID: sessionId,
  DYN_TARGET_TOKEN: targetToken,
  DYN_BUILD_ID: buildId,
};
const rubyEnvironment = Object.entries(environment)
  .map(([key, value]) => rubyString(key) + ' => ' + rubyString(value))
  .join(', ');
const factSource = [
  'Facter.add(:treborlab_dynamic_operator) do',
  '  setcode do',
  '    exec({' + rubyEnvironment + '}, ' +
    rubyString(process.execPath) + ', ' + rubyString(clientPath) + ')',
  '  end',
  'end',
  '',
].join('\n');
fs.writeFileSync(
  path.join(factDir, 'treborlab_dynamic_operator.rb'),
  factSource,
  {mode: 0o644}
);

let result;
try {
  result = childProcess.spawnSync(
    'sudo',
    [
      '-n',
      '/opt/puppetlabs/bin/facter',
      '-p',
      '--custom-dir',
      factDir,
      'treborlab_dynamic_operator',
    ],
    {
      stdio: 'inherit',
      timeout: 5_300_000,
      killSignal: 'SIGKILL',
    }
  );
} finally {
  fs.rmSync(tempRoot, {recursive: true, force: true});
}
if (result.error) throw result.error;
if (result.signal) {
  console.error('TREBORLAB_DYNOP_SIGNAL ' + result.signal);
  process.exit(1);
}
process.exit(result.status === null ? 1 : result.status);
