/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-markdown-viewer';
import {GrDiffMarkdownViewer} from './gr-diff-markdown-viewer';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-expect-error
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {createDiff, createPatchRange} from '../../../test/test-data-generators';
import {DiffInfo, DiffViewMode} from '../../../api/diff';

suite('gr-diff-markdown-viewer screenshot tests', () => {
  let element: GrDiffMarkdownViewer;

  setup(async () => {
    const diff: DiffInfo = {
      ...createDiff(),
      meta_a: {
        name: 'SKILL.md',
        content_type: 'text/markdown',
        lines: 25,
      },
      meta_b: {
        name: 'SKILL.md',
        content_type: 'text/markdown',
        lines: 25,
      },
      content: [
        {
          ab: ['## Running the Experiment', ''],
        },
        {
          a: [
            'The generalized runner script is located at [run_experiment.py](file:///path/to/workspace/tools/build_experimentation/scripts/run_experiment.py).',
          ],
          b: [
            'The generalized runner script is located at [`scripts/run_experiment.py`](scripts/run_experiment.py) (relative to this `SKILL.md` directory, `<SKILL_DIR>`). It automatically resolves the workspace root from `--workspace-root`, `workspace_root` in the JSON config, `$ANDROID_BUILD_TOP`, or the nearest ancestor directory containing `.repo/`.',
          ],
        },
        {
          ab: [
            '',
            '### Execution',
            '',
            'Run the script from the build top directory, passing the path to your config file:',
            '',
            '```bash',
          ],
        },
        {
          a: [
            'python3 tools/build_experimentation/scripts/run_experiment.py --config path/to/config.json',
          ],
          b: [
            'python3 <SKILL_DIR>/scripts/run_experiment.py --config path/to/config.json',
          ],
        },
        {
          ab: [
            '```',
            '',
            'By default, running this command will clear any existing results in `out/experiment_results.json`.',
            '',
            '#### Appending Results (Opt-in)',
            'To append results to a prior run (e.g., to add more iterations to an existing experiment), use the `--append` flag:',
            '',
            '```bash',
          ],
        },
        {
          a: [
            'python3 tools/build_experimentation/scripts/run_experiment.py --config path/to/config.json --append',
          ],
          b: [
            'python3 <SKILL_DIR>/scripts/run_experiment.py --config path/to/config.json --append',
          ],
        },
        {
          ab: [
            '```',
            '',
            'This will preserve existing results, start iteration numbering from where the last run left off, and avoid clearing preserved artifacts.',
          ],
        },
      ],
    };

    element = await fixture<GrDiffMarkdownViewer>(
      html`<gr-diff-markdown-viewer
        style="display: block; width: 1200px;"
        .diff=${diff}
        .path=${'SKILL.md'}
        .patchRange=${createPatchRange()}
      ></gr-diff-markdown-viewer>`
    );
    await element.updateComplete;
    await document.fonts?.ready;
  });

  test('side-by-side rich diff with inline code spans', async () => {
    await visualDiff(element, 'gr-diff-markdown-viewer-inline-code');
    await visualDiffDarkTheme(element, 'gr-diff-markdown-viewer-inline-code');
  });

  test('unified rich diff with inline code spans', async () => {
    element.viewMode = DiffViewMode.UNIFIED;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-markdown-viewer-inline-code-unified');
    await visualDiffDarkTheme(
      element,
      'gr-diff-markdown-viewer-inline-code-unified'
    );
  });
});
