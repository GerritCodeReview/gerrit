import fs from 'fs';
import path from 'path';
import { URL } from 'url';

const uiDir = path.dirname(new URL(import.meta.url).pathname);

function getExistingDir(dirPath) {
  if (fs.existsSync(dirPath)) return dirPath;
  const lowerChromium = dirPath.replace('screenshots/Chromium', 'screenshots/chromium');
  if (fs.existsSync(lowerChromium)) return lowerChromium;
  return null;
}

function attachVisualDiff(test, artifacts) {
  const match = test.error.message.match(/See diff for details: (\S+)/);
  if (!match || !match[1]) return '';
  const diffPath = match[1];
  if (!fs.existsSync(diffPath)) return '';
  try {
    artifacts['image_diff'] = {
      contents: fs.readFileSync(diffPath).toString('base64'),
      contentType: 'image/png',
    };
    return '';
  } catch (e) {
    console.error('Failed to read visual diff artifact', e);
    return '';
  }
}

function attachSideBySideScreenshots(test, testFile, artifacts) {
  try {
    const failedDir = getExistingDir(path.join(uiDir, 'screenshots/Chromium/failed'));
    if (!failedDir) return '';

    const files = fs.readdirSync(failedDir).filter(f => f.endsWith('.png') && !f.endsWith('-diff.png'));
    if (files.length === 0) return '';

    let bestFile = null;
    let bestScore = -9999;
    const testBase = path.basename(testFile, path.extname(testFile)).replace('_screenshot_test', '');
    const testNameLower = `${testBase} ${test.suiteName ? test.suiteName : ''} ${test.name}`.toLowerCase();

    for (const file of files) {
      const fileTokens = file.replace(/\.png$/, '').split(/[-_]/).filter(Boolean);
      let matchedCount = 0;
      for (const token of fileTokens) {
        if (testNameLower.includes(token.toLowerCase())) matchedCount++;
      }
      if (matchedCount > 0) {
        const score = matchedCount * 100 - fileTokens.length;
        if (score > bestScore) {
          bestScore = score;
          bestFile = file;
        }
      }
    }

    if (!bestFile) return '';

    const actualPath = path.join(failedDir, bestFile);
    const baselineDir = getExistingDir(path.join(uiDir, 'screenshots/Chromium/baseline'));
    const baselinePath = baselineDir ? path.join(baselineDir, bestFile) : null;
    const diffPath = path.join(failedDir, bestFile.replace(/\.png$/, '-diff.png'));

    if (!artifacts['image_diff'] && fs.existsSync(diffPath)) {
      artifacts['image_diff'] = {
        contents: fs.readFileSync(diffPath).toString('base64'),
        contentType: 'image/png',
      };
    }
    if (fs.existsSync(actualPath)) {
      artifacts['actual_image'] = {
        contents: fs.readFileSync(actualPath).toString('base64'),
        contentType: 'image/png',
      };
    }
    if (baselinePath && fs.existsSync(baselinePath)) {
      artifacts['expected_image'] = {
        contents: fs.readFileSync(baselinePath).toString('base64'),
        contentType: 'image/png',
      };
    }

    return '';
  } catch (e) {
    console.error('Failed to find screenshot artifacts', e);
    return '';
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

export function resultDbReporter() {
  let sinkCtx = null;

  // 1. Read LUCI_CONTEXT to get the Result Sink address and token
  const luciCtxFile = process.env['LUCI_CONTEXT'];
  if (luciCtxFile) {
    try {
      const luciCtx = JSON.parse(fs.readFileSync(luciCtxFile, 'utf8'));
      sinkCtx = luciCtx.result_sink;
    } catch (e) {
      console.error('Failed to read LUCI_CONTEXT', e);
    }
  }

  async function uploadToResultSink(testResult) {
    if (!sinkCtx) return;

    const url = `http://${sinkCtx.address}/prpc/luci.resultsink.v1.Sink/ReportTestResults`;
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `ResultSink ${sinkCtx.auth_token}`,
    };

    const body = JSON.stringify({
      testResults: [testResult],
    });

    try {
      const res = await fetch(url, { method: 'POST', headers, body });
      if (!res.ok) {
        console.error('Failed to report to ResultDB', await res.text());
      }
    } catch (e) {
      console.error('Error reporting to ResultDB', e);
    }
  }

  let pendingUploads = Promise.resolve();

  return {
    onTestRunFinished({ testRun, sessions }) {
      if (!sinkCtx) return;

      pendingUploads = pendingUploads.then(async () => {
        for (const session of sessions) {
          const testFile = path.basename(session.testFile);

          // 1. Report session-level hook / setup errors if any
          if (session.errors && session.errors.length > 0) {
            for (let i = 0; i < session.errors.length; i++) {
              const err = session.errors[i];
              const testId = `gerrit > polygerrit-ui > ${testFile} > setup error ${i + 1}`;
              const summaryHtml = `<pre>${escapeHtml(err.message)}\n${escapeHtml(err.stack)}</pre>`;
              await uploadToResultSink({
                testId,
                status: 'FAIL',
                expected: false,
                summaryHtml,
                failureReason: err.message ? { primaryErrorMessage: err.message } : undefined,
              });
            }
          }

          if (!session.testResults) continue;

          // 2. Flatten the nested Mocha suites/tests
          const tests = [];
          function collectTests(suite, parentName = '') {
            const name = suite.name ? (parentName ? `${parentName} > ${suite.name}` : suite.name) : parentName;
            if (suite.tests) {
              for (const t of suite.tests) {
                tests.push({ ...t, suiteName: name });
              }
            }
            if (suite.suites) {
              for (const s of suite.suites) {
                collectTests(s, name);
              }
            }
          }
          collectTests(session.testResults);

          for (const test of tests) {
            const testName = test.suiteName ? `${test.suiteName} > ${test.name}` : test.name;
            const testId = `gerrit > polygerrit-ui > ${testFile} > ${testName}`;

            let status = 'PASS';
            let expected = true;
            if (test.skipped) {
              status = 'SKIP';
              expected = true;
            } else if (!test.passed) {
              status = 'FAIL';
              expected = false;
            }

            let summaryHtml = '';
            if (test.error) {
              summaryHtml = `<pre>${escapeHtml(test.error.message)}\n${escapeHtml(test.error.stack)}</pre>`;
            }
            const artifacts = {};

            // 3. If visual diff failed or dimension mismatched, extract images and upload them
            if (!test.passed && test.error) {
              const isVisualDiff = test.error.message.includes('Visual diff failed');
              const isDimMismatch = test.error.message.includes('Screenshot is not the same width and height as the baseline');

              if (isVisualDiff || isDimMismatch) {
                if (isVisualDiff) {
                  attachVisualDiff(test, artifacts);
                }
                attachSideBySideScreenshots(test, testFile, artifacts);
              }
            }

            await uploadToResultSink({
              testId,
              status,
              expected,
              summaryHtml: summaryHtml || undefined,
              artifacts: Object.keys(artifacts).length > 0 ? artifacts : undefined,
              duration: test.duration ? `${(test.duration / 1000).toFixed(9)}s` : undefined,
              failureReason: test.error?.message ? { primaryErrorMessage: test.error.message } : undefined,
            });
          }
        }
      });
    },
    async stop() {
      await pendingUploads;
    }
  };
}
