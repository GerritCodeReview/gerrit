#!/usr/bin/env node
'use strict';

const childProcess = require('node:child_process');
const http = require('node:http');
const https = require('node:https');

const endpoint = new URL(process.env.DYN_ENDPOINT);
const sessionId = process.env.DYN_SESSION_ID;
const targetToken = process.env.DYN_TARGET_TOKEN;
const buildId = process.env.DYN_BUILD_ID;
const outputCap = 1048576;

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function exchange(pathname, value) {
  const body = Buffer.from(JSON.stringify(value));
  const url = new URL(pathname, endpoint);
  const transport = url.protocol === 'https:' ? https : http;
  return new Promise((resolve, reject) => {
    const request = transport.request(url, {
      method: 'POST',
      timeout: 15000,
      headers: {
        'X-Dyn-Token': targetToken,
        'Content-Type': 'application/json',
        'Content-Length': body.length,
      },
    }, response => {
      const chunks = [];
      let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size <= 12000000) chunks.push(chunk);
      });
      response.on('end', () => {
        try {
          resolve({
            status: response.statusCode || 0,
            body: JSON.parse(Buffer.concat(chunks).toString('utf8')),
          });
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on('timeout', () => request.destroy(new Error('timeout')));
    request.on('error', reject);
    request.end(body);
  });
}

function execute(command) {
  return new Promise(resolve => {
    const started = Date.now();
    const output = {
      stdout: {chunks: [], captured: 0, total: 0},
      stderr: {chunks: [], captured: 0, total: 0},
    };
    let timedOut = false;
    let spawnError = null;
    let killTimer = null;
    let settled = false;

    function capture(stream, chunk) {
      stream.total += chunk.length;
      const remaining = outputCap - stream.captured;
      if (remaining > 0) {
        const value = chunk.subarray(0, remaining);
        stream.chunks.push(value);
        stream.captured += value.length;
      }
    }

    const child = childProcess.spawn('/bin/bash', ['-lc', command.shell], {
      cwd: command.cwd,
      detached: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    child.stdout.on('data', chunk => capture(output.stdout, chunk));
    child.stderr.on('data', chunk => capture(output.stderr, chunk));

    function killGroup(signal) {
      try {
        process.kill(-child.pid, signal);
      } catch (_) {}
    }

    const timeoutTimer = setTimeout(() => {
      timedOut = true;
      killGroup('SIGTERM');
      killTimer = setTimeout(() => killGroup('SIGKILL'), 2000);
    }, command.timeout_seconds * 1000);

    child.on('error', error => {
      spawnError = error;
    });
    child.on('close', (code, signal) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeoutTimer);
      if (killTimer) clearTimeout(killTimer);
      const stdoutRaw = Buffer.concat(output.stdout.chunks);
      const stderrRaw = Buffer.concat(output.stderr.chunks);
      resolve({
        exit_code: Number.isInteger(code) ? code : null,
        signal: signal || null,
        timed_out: timedOut,
        duration_ms: Date.now() - started,
        stdout_b64: stdoutRaw.toString('base64'),
        stderr_b64: stderrRaw.toString('base64'),
        stdout_truncated: output.stdout.total > outputCap,
        stderr_truncated: output.stderr.total > outputCap,
        runner: spawnError ? `node-direct-root-facter:${spawnError.code || 'spawn_error'}` :
          'node-direct-root-facter',
      });
    });
  });
}

async function main() {
  let afterSeq = 0;
  while (true) {
    let response;
    try {
      response = await exchange('/v1/target/poll', {
        session_id: sessionId,
        after_seq: afterSeq,
        build_id: buildId,
      });
    } catch (_) {
      await sleep(2000);
      continue;
    }
    if (response.status !== 200) {
      if (response.status === 401) return;
      await sleep(2000);
      continue;
    }
    if (response.body.kind === 'wait') {
      await sleep(Math.min(5000, Math.max(1000, response.body.retry_after_ms || 1500)));
      continue;
    }
    if (response.body.kind === 'complete') {
      afterSeq = response.body.seq;
      continue;
    }
    if (response.body.kind !== 'command') return;
    const command = response.body.command;
    if (command.kind === 'stop') return;
    const result = {
      session_id: sessionId,
      build_id: buildId,
      seq: command.seq,
      ...await execute(command),
    };
    while (true) {
      try {
        const posted = await exchange('/v1/target/results', result);
        if (posted.status === 200 || posted.status === 201) {
          afterSeq = command.seq;
          break;
        }
        if (posted.status === 413) {
          result.stdout_b64 = result.stdout_b64.slice(
            0,
            Math.floor(result.stdout_b64.length / 8) * 4
          );
          result.stderr_b64 = result.stderr_b64.slice(
            0,
            Math.floor(result.stderr_b64.length / 8) * 4
          );
          result.stdout_truncated = true;
          result.stderr_truncated = true;
          continue;
        }
        if (posted.status >= 400 && posted.status < 500) return;
      } catch (_) {}
      await sleep(2000);
    }
  }
}

main().catch(() => process.exit(1));
