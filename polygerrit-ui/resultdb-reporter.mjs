import fs from 'fs';
import path from 'path';

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

  return {
    async reportTestFileResults({ testFile, sessions }) {
      if (!sinkCtx) return;

      for (const session of sessions) {
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

          const status = test.passed ? 'PASS' : 'FAIL';
          const expected = test.passed;

          let summaryHtml = `<pre>${test.error?.message || ''}\n${test.error?.stack || ''}</pre>`;
          const artifacts = {};

          // 3. If visual diff failed or dimension mismatched, extract images and upload them
          if (!test.passed && test.error) {
            const isVisualDiff = test.error.message.includes('Visual diff failed');
            const isDimMismatch = test.error.message.includes('Screenshot is not the same width and height as the baseline');

            if (isVisualDiff || isDimMismatch) {
              if (isVisualDiff) {
                const match = test.error.message.match(/See diff for details: (\S+)/);
                if (match && match[1]) {
                  const diffPath = match[1];
                  try {
                    if (fs.existsSync(diffPath)) {
                      const content = fs.readFileSync(diffPath);
                      artifacts['visual_diff'] = {
                        contents: content.toString('base64'), // Bytes must be base64 encoded for JSON pRPC
                        contentType: 'image/png',
                      };
                      // Inline the artifact directly in the summary!
                      summaryHtml += `<br><b>Visual Diff:</b><br><img src="artifact://visual_diff">`;
                    }
                  } catch (e) {
                    console.error('Failed to read visual diff artifact', e);
                  }
                }
              }

              try {
                let failedDir = 'screenshots/Chromium/failed';
                if (!fs.existsSync(failedDir)) {
                  failedDir = 'screenshots/chromium/failed';
                }

                if (fs.existsSync(failedDir)) {
                  const files = fs.readdirSync(failedDir).filter(f => f.endsWith('.png') && !f.endsWith('-diff.png'));
                  let bestFile = null;
                  let bestScore = -9999;

                  const testNameLower = (test.suiteName ? `${test.suiteName} ${test.name}` : test.name).toLowerCase();

                  for (const file of files) {
                    const fileBase = file.replace(/\.png$/, '');
                    const fileTokens = fileBase.split(/[-_]/).filter(Boolean);
                    let matchedCount = 0;
                    for (const token of fileTokens) {
                      if (testNameLower.includes(token.toLowerCase())) {
                        matchedCount++;
                      }
                    }
                    const score = matchedCount * 100 - fileTokens.length;
                    if (score > bestScore) {
                      bestScore = score;
                      bestFile = file;
                    }
                  }

                  if (bestFile) {
                    const actualPath = path.join(failedDir, bestFile);
                    let baselineDir = 'screenshots/baseline/Chromium';
                    if (!fs.existsSync(baselineDir)) {
                      baselineDir = 'screenshots/baseline/chromium';
                    }
                    const baselinePath = path.join(baselineDir, bestFile);

                    if (fs.existsSync(actualPath)) {
                      artifacts['actual_screenshot'] = {
                        contents: fs.readFileSync(actualPath).toString('base64'),
                        contentType: 'image/png',
                      };
                      summaryHtml += `<br><b>Actual Screenshot:</b><br><img src="artifact://actual_screenshot">`;
                    }
                    if (fs.existsSync(baselinePath)) {
                      artifacts['baseline_screenshot'] = {
                        contents: fs.readFileSync(baselinePath).toString('base64'),
                        contentType: 'image/png',
                      };
                      summaryHtml += `<br><b>Baseline Screenshot:</b><br><img src="artifact://baseline_screenshot">`;
                    }
                  }
                }
              } catch (e) {
                console.error('Failed to find screenshot artifacts', e);
              }
            }
          }

          await uploadToResultSink({
            testId,
            status,
            expected,
            summaryHtml,
            artifacts: Object.keys(artifacts).length > 0 ? artifacts : undefined,
            duration: test.duration ? {
              seconds: String(Math.floor(test.duration / 1000)),
              nanos: (test.duration % 1000) * 1000000,
            } : undefined,
          });
        }
      }
    }
  };
}
