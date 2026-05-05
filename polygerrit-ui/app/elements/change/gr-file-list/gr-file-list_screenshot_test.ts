/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../shared/gr-date-formatter/gr-date-formatter';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {FileInfo, PARENT, RevisionPatchSetNum} from '../../../api/rest-api';
import {normalize} from '../../../models/change/files-model';
import {PatchRange} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../api/diff';
import {Category} from '../../../api/checks';
import {RunResult} from '../../../models/checks/checks-model';
import {GrFileList, NormalizedFileInfo} from './gr-file-list';
import './gr-file-list';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-file-list screenshot tests', () => {
  let element: GrFileList;

  function createFiles(
    count: number,
    fileInfo: FileInfo
  ): NormalizedFileInfo[] {
    return Array.from(Array(count).keys()).map(index =>
      normalize(fileInfo, `/file${index}`)
    );
  }

  setup(async () => {
    const patchRange: PatchRange = {
      basePatchNum: PARENT,
      patchNum: 2 as RevisionPatchSetNum,
    };
    const diffPrefs: DiffPreferencesInfo = {
      context: 10,
      tab_size: 8,
      font_size: 12,
      line_length: 100,
      ignore_whitespace: 'IGNORE_NONE',
    };
    element = await fixture(
      html`<gr-file-list
        .patchNum=${patchRange.patchNum}
        .basePatchNum=${patchRange.basePatchNum}
        .diffPrefs=${diffPrefs}
      ></gr-file-list>`
    );
  });

  test('screenshot', async () => {
    element.files = [
      ...createFiles(3, {lines_inserted: 9}),
      ...createFiles(2, {lines_deleted: 14}),
    ];
    await element.updateComplete;

    await visualDiff(element, 'gr-file-list');
    await visualDiffDarkTheme(element, 'gr-file-list');
  });

  test('screenshot with AI powered check results', async () => {
    element.files = [...createFiles(1, {lines_inserted: 1})];
    element.checkResults = [
      {
        internalResultId: 'id',
        checkName: 'ai-check',
        summary: 'summary',
        isLatestAttempt: true,
        isAiPowered: true,
        category: Category.WARNING,
        codePointers: [
          {
            path: '/file0',
            range: {
              start_line: 1,
              start_character: 0,
              end_line: 1,
              end_character: 0,
            },
          },
        ],
      } as RunResult,
    ];
    await element.updateComplete;

    await visualDiff(element, 'gr-file-list-with-ai-checks');
    await visualDiffDarkTheme(element, 'gr-file-list-with-ai-checks');
  });
});
