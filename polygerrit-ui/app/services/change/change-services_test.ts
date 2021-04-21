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

import {ChangeStatus} from '../../constants/constants';
import '../../test/common-test-setup-karma';
import {
  createChange,
  createChangeMessageInfo,
  createRevision,
} from '../../test/test-data-generators';
import {stubRestApi} from '../../test/test-utils';
import {CommitId, PatchSetNum} from '../../types/common';
import {ParsedChangeInfo} from '../../types/types';
import {ChangeService} from './change-service';

suite('change service tests', () => {
  let changeService: ChangeService;
  let knownChange: ParsedChangeInfo;
  setup(() => {
    changeService = new ChangeService();
    knownChange = {
      ...createChange(),
      revisions: {
        sha1: {
          ...createRevision(1),
          description: 'patch 1',
          _number: 1 as PatchSetNum,
        },
        sha2: {
          ...createRevision(2),
          description: 'patch 2',
          _number: 2 as PatchSetNum,
        },
      },
      status: ChangeStatus.NEW,
      current_revision: 'abc' as CommitId,
      messages: [],
    };
  });

  test('changeService.fetchChangeUpdates on latest', async () => {
    stubRestApi('getChangeDetail').returns(Promise.resolve(knownChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeService.fetchChangeUpdates not on latest', async () => {
    const actualChange = {
      ...knownChange,
      revisions: {
        ...knownChange.revisions,
        sha3: {
          ...createRevision(3),
          description: 'patch 3',
          _number: 3 as PatchSetNum,
        },
      },
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isFalse(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeService.fetchChangeUpdates new status', async () => {
    const actualChange = {
      ...knownChange,
      status: ChangeStatus.MERGED,
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.equal(result.newStatus, ChangeStatus.MERGED);
    assert.isNotOk(result.newMessages);
  });

  test('changeService.fetchChangeUpdates new messages', async () => {
    const actualChange = {
      ...knownChange,
      messages: [{...createChangeMessageInfo(), message: 'blah blah'}],
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.deepEqual(result.newMessages, {
      ...createChangeMessageInfo(),
      message: 'blah blah',
    });
  });
});
