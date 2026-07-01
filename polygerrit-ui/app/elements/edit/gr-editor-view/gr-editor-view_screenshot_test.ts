/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-editor-view';
import {GrEditorView} from './gr-editor-view';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {
  query,
  stubRestApi,
  visualDiffDarkTheme,
} from '../../../test/test-utils';
import {createEditViewState} from '../../../test/test-data-generators';
import {NumericChangeId, RevisionPatchSetNum} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-editor-view screenshot tests', () => {
  let element: GrEditorView;

  setup(async () => {
    stubRestApi('getFileContent').resolves({
      ok: true,
      type: 'text/javascript',
      content: 'original content',
    });
    element = await fixture<GrEditorView>(
      html`<gr-editor-view></gr-editor-view>`
    );
    element.viewState = {
      ...createEditViewState(),
      changeNum: 42 as NumericChangeId,
      patchNum: 1 as RevisionPatchSetNum,
      editView: {path: 'foo/bar.baz'},
    };
    element.latestPatchsetNumber = 1 as RevisionPatchSetNum;
    element.content = 'original content';
    element.newContent = 'original content';
    await element.updateComplete;
  });

  test('editor view', async () => {
    await visualDiff(element, 'gr-editor-view-normal');
    await visualDiffDarkTheme(element, 'gr-editor-view-normal');
  });

  test('cancel modal open', async () => {
    element.newContent = 'modified content';
    await element.updateComplete;

    query<GrButton>(element, '#close')!.click();
    await element.updateComplete;

    await visualDiff(element, 'gr-editor-view-cancel-modal');
    await visualDiffDarkTheme(element, 'gr-editor-view-cancel-modal');
  });
});
