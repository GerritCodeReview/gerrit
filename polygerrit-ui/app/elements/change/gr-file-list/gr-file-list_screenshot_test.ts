/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import '../../shared/gr-date-formatter/gr-date-formatter';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {DiffPreferencesInfo} from '../../../api/diff';
import {FileInfo, PARENT, RevisionPatchSetNum} from '../../../api/rest-api';
import {
  filesModelToken,
  normalize
} from '../../../models/change/files-model';
import {testResolver} from '../../../test/common-test-setup';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {PatchRange} from '../../../types/common';
import './gr-file-list';
import {GrFileList, NormalizedFileInfo} from './gr-file-list';

suite('gr-file-list screenshot tests', () => {
  let element: GrFileList;

  function createFiles(
    count: number,
    fileInfo: FileInfo,
  ): NormalizedFileInfo[] {
    return Array.from({length: count}).map((_, index) =>
      normalize(fileInfo, `/file${index}`),
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
      ></gr-file-list>`,
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

  test('screenshot with safety limit warning', async () => {
    const filesModel = testResolver(filesModelToken);
    filesModel.updateState({
      rawFiles: [
        ...createFiles(3, {lines_inserted: 9}),
        ...createFiles(1000, {lines_deleted: 14}),
      ],
    });
    await element.updateComplete;

    await visualDiff(element, 'gr-file-list-warning');
    await visualDiffDarkTheme(element, 'gr-file-list-warning');
  });
});
