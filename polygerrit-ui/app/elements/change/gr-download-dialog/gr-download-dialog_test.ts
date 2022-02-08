/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {
  createChange,
  createCommit,
  createDownloadInfo,
  createRevision,
  createRevisions,
} from '../../../test/test-data-generators';
import {
  CommitId,
  NumericChangeId,
  PatchSetNum,
  RepoName,
} from '../../../types/common';
import {GrDownloadDialog} from './gr-download-dialog';
import {mockPromise} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-download-dialog');

function getChangeObject() {
  return {
    ...createChange(),
    current_revision: '34685798fe548b6d17d1e8e5edc43a26d055cc72' as CommitId,
    revisions: {
      '34685798fe548b6d17d1e8e5edc43a26d055cc72': {
        ...createRevision(),
        commit: createCommit(),
        fetch: {
          repo: {
            url: 'my.url',
            ref: 'refs/changes/5/6/1',
            commands: {
              repo: 'repo download test-project 5/1',
            },
          },
          ssh: {
            url: 'my.url',
            ref: 'refs/changes/5/6/1',
            commands: {
              Checkout:
                'git fetch ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1 && git checkout FETCH_HEAD',
              'Cherry Pick':
                'git fetch ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1 && git cherry-pick FETCH_HEAD',
              'Format Patch':
                'git fetch ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1 ' +
                '&& git format-patch -1 --stdout FETCH_HEAD',
              Pull:
                'git pull ' +
                'ssh://andybons@localhost:29418/test-project ' +
                'refs/changes/05/5/1',
            },
          },
          http: {
            url: 'my.url',
            ref: 'refs/changes/5/6/1',
            commands: {
              Checkout:
                'git fetch ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1 && git checkout FETCH_HEAD',
              'Cherry Pick':
                'git fetch ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1 && git cherry-pick FETCH_HEAD',
              'Format Patch':
                'git fetch ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1 && ' +
                'git format-patch -1 --stdout FETCH_HEAD',
              Pull:
                'git pull ' +
                'http://andybons@localhost:8080/a/test-project ' +
                'refs/changes/05/5/1',
            },
          },
        },
      },
    },
  };
}

function getChangeObjectNoFetch() {
  return {
    ...createChange(),
    current_revision: '34685798fe548b6d17d1e8e5edc43a26d055cc72' as CommitId,
    revisions: createRevisions(1),
  };
}

suite('gr-download-dialog', () => {
  let element: GrDownloadDialog;

  setup(() => {
    element = basicFixture.instantiate();
    element.patchNum = 1 as PatchSetNum;
    element.config = createDownloadInfo();
    flush();
  });

  test('anchors use download attribute', () => {
    const anchors = Array.from(element.root!.querySelectorAll('a'));
    assert.isTrue(!anchors.some(a => !a.hasAttribute('download')));
  });

  suite('gr-download-dialog tests with no fetch options', () => {
    setup(() => {
      element.change = getChangeObjectNoFetch();
      flush();
    });

    test('focuses on first download link if no copy links', () => {
      const focusStub = sinon.stub(element.$.download, 'focus');
      element.focus();
      assert.isTrue(focusStub.called);
      focusStub.restore();
    });
  });

  suite('gr-download-dialog with fetch options', () => {
    setup(() => {
      element.change = getChangeObject();
      flush();
    });

    test('focuses on first copy link', () => {
      const focusStub = sinon.stub(element.$.downloadCommands, 'focusOnCopy');
      element.focus();
      flush();
      assert.isTrue(focusStub.called);
      focusStub.restore();
    });

    test('computed fields', () => {
      assert.equal(
        element._computeArchiveDownloadLink(
          {
            ...createChange(),
            project: 'test/project' as RepoName,
            _number: 123 as NumericChangeId,
          },
          2 as PatchSetNum,
          'tgz'
        ),
        '/changes/test%2Fproject~123/revisions/2/archive?format=tgz'
      );
    });

    test('close event', async () => {
      const closeCalled = mockPromise();
      element.addEventListener('close', () => {
        closeCalled.resolve();
      });
      const closeButton = element.shadowRoot!.querySelector(
        '.closeButtonContainer gr-button'
      );
      tap(closeButton!);
      await closeCalled;
    });
  });

  test('_computeShowDownloadCommands', () => {
    assert.equal(element._computeShowDownloadCommands([]), 'hidden');
    assert.equal(element._computeShowDownloadCommands(['test']), '');
  });

  test('_computeHidePatchFile', () => {
    const patchNum = 1 as PatchSetNum;

    const changeWithNoParent = {
      ...createChange(),
      revisions: {
        r1: {...createRevision(), commit: createCommit()},
      },
    };
    assert.isTrue(element._computeHidePatchFile(changeWithNoParent, patchNum));

    const changeWithOneParent = {
      ...createChange(),
      revisions: {
        r1: {
          ...createRevision(),
          commit: {
            ...createCommit(),
            parents: [{commit: 'p1' as CommitId, subject: 'subject1'}],
          },
        },
      },
    };
    assert.isFalse(
      element._computeHidePatchFile(changeWithOneParent, patchNum)
    );

    const changeWithMultipleParents = {
      ...createChange(),
      revisions: {
        r1: {
          ...createRevision(),
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p1' as CommitId, subject: 'subject1'},
              {commit: 'p2' as CommitId, subject: 'subject2'},
            ],
          },
        },
      },
    };
    assert.isTrue(
      element._computeHidePatchFile(changeWithMultipleParents, patchNum)
    );
  });
});
