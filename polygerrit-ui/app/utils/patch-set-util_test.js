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

import '../test/common-test-setup-karma.js';
import {
  _testOnly_computeWipForPatchSets, computeAllPatchSets,
  fetchChangeUpdates, findEditParentPatchNum, findEditParentRevision,
  getParentIndex, getRevisionByPatchNum,
  isMergeParent,
  patchNumEquals, sortRevisions,
} from './patch-set-util.js';

suite('gr-patch-set-util tests', () => {
  test('getRevisionByPatchNum', () => {
    const revisions = [
      {_number: 0},
      {_number: 1},
      {_number: 2},
    ];
    assert.deepEqual(getRevisionByPatchNum(revisions, '1'), revisions[1]);
    assert.deepEqual(getRevisionByPatchNum(revisions, 2), revisions[2]);
    assert.equal(getRevisionByPatchNum(revisions, '3'), undefined);
  });

  test('fetchChangeUpdates on latest', done => {
    const knownChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
      },
      status: 'NEW',
      messages: [],
    };
    const mockRestApi = {
      getChangeDetail() {
        return Promise.resolve(knownChange);
      },
    };
    fetchChangeUpdates(knownChange, mockRestApi)
        .then(result => {
          assert.isTrue(result.isLatest);
          assert.isNotOk(result.newStatus);
          assert.isFalse(result.newMessages);
          done();
        });
  });

  test('fetchChangeUpdates not on latest', done => {
    const knownChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
      },
      status: 'NEW',
      messages: [],
    };
    const actualChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
        sha3: {description: 'patch 3', _number: 3},
      },
      status: 'NEW',
      messages: [],
    };
    const mockRestApi = {
      getChangeDetail() {
        return Promise.resolve(actualChange);
      },
    };
    fetchChangeUpdates(knownChange, mockRestApi)
        .then(result => {
          assert.isFalse(result.isLatest);
          assert.isNotOk(result.newStatus);
          assert.isFalse(result.newMessages);
          done();
        });
  });

  test('fetchChangeUpdates new status', done => {
    const knownChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
      },
      status: 'NEW',
      messages: [],
    };
    const actualChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
      },
      status: 'MERGED',
      messages: [],
    };
    const mockRestApi = {
      getChangeDetail() {
        return Promise.resolve(actualChange);
      },
    };
    fetchChangeUpdates(knownChange, mockRestApi)
        .then(result => {
          assert.isTrue(result.isLatest);
          assert.equal(result.newStatus, 'MERGED');
          assert.isFalse(result.newMessages);
          done();
        });
  });

  test('fetchChangeUpdates new messages', done => {
    const knownChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
      },
      status: 'NEW',
      messages: [],
    };
    const actualChange = {
      revisions: {
        sha1: {description: 'patch 1', _number: 1},
        sha2: {description: 'patch 2', _number: 2},
      },
      status: 'NEW',
      messages: [{message: 'blah blah'}],
    };
    const mockRestApi = {
      getChangeDetail() {
        return Promise.resolve(actualChange);
      },
    };
    fetchChangeUpdates(knownChange, mockRestApi)
        .then(result => {
          assert.isTrue(result.isLatest);
          assert.isNotOk(result.newStatus);
          assert.isTrue(result.newMessages);
          done();
        });
  });

  test('_computeWipForPatchSets', () => {
    // Compute patch sets for a given timeline on a change. The initial WIP
    // property of the change can be true or false. The map of tags by
    // revision is keyed by patch set number. Each value is a list of change
    // message tags in the order that they occurred in the timeline. These
    // indicate actions that modify the WIP property of the change and/or
    // create new patch sets.
    //
    // Returns the actual results with an assertWip method that can be used
    // to compare against an expected value for a particular patch set.
    const compute = (initialWip, tagsByRevision) => {
      const change = {
        messages: [],
        work_in_progress: initialWip,
      };
      const revs = Object.keys(tagsByRevision).sort((a, b) => { return a - b; });
      for (const rev of revs) {
        for (const tag of tagsByRevision[rev]) {
          change.messages.push({
            tag,
            _revision_number: rev,
          });
        }
      }
      let patchNums = revs.map(rev => { return {num: rev}; });
      patchNums = _testOnly_computeWipForPatchSets(
          change, patchNums);
      const actualWipsByRevision = {};
      for (const patchNum of patchNums) {
        actualWipsByRevision[patchNum.num] = patchNum.wip;
      }
      const verifier = {
        assertWip(revision, expectedWip) {
          const patchNum = patchNums.find(patchNum => { return patchNum.num == revision; });
          if (!patchNum) {
            assert.fail('revision ' + revision + ' not found');
          }
          assert.equal(patchNum.wip, expectedWip,
              'wip state for ' + revision + ' is ' +
            patchNum.wip + '; expected ' + expectedWip);
          return verifier;
        },
      };
      return verifier;
    };

    compute(false, {1: ['upload']}).assertWip(1, false);
    compute(true, {1: ['upload']}).assertWip(1, true);

    const setWip = 'autogenerated:gerrit:setWorkInProgress';
    const uploadInWip = 'autogenerated:gerrit:newWipPatchSet';
    const clearWip = 'autogenerated:gerrit:setReadyForReview';

    compute(false, {
      1: ['upload', setWip],
      2: ['upload'],
      3: ['upload', clearWip],
      4: ['upload', setWip],
    }).assertWip(1, false) // Change was created with PS1 ready for review
        .assertWip(2, true) // PS2 was uploaded during WIP
        .assertWip(3, false) // PS3 was marked ready for review after upload
        .assertWip(4, false); // PS4 was uploaded ready for review

    compute(false, {
      1: [uploadInWip, null, 'addReviewer'],
      2: ['upload'],
      3: ['upload', clearWip, setWip],
      4: ['upload'],
      5: ['upload', clearWip],
      6: [uploadInWip],
    }).assertWip(1, true) // Change was created in WIP
        .assertWip(2, true) // PS2 was uploaded during WIP
        .assertWip(3, false) // PS3 was marked ready for review
        .assertWip(4, true) // PS4 was uploaded during WIP
        .assertWip(5, false) // PS5 was marked ready for review
        .assertWip(6, true); // PS6 was uploaded with WIP option
  });

  test('patchNumEquals', () => {
    assert.isFalse(patchNumEquals('edit', 'PARENT'));
    assert.isFalse(patchNumEquals('edit', NaN));
    assert.isFalse(patchNumEquals(1, '2'));

    assert.isTrue(patchNumEquals(1, '1'));
    assert.isTrue(patchNumEquals(1, 1));
    assert.isTrue(patchNumEquals('edit', 'edit'));
    assert.isTrue(patchNumEquals('PARENT', 'PARENT'));
  });

  test('isMergeParent', () => {
    assert.isFalse(isMergeParent(1));
    assert.isFalse(isMergeParent(4321));
    assert.isFalse(isMergeParent('52'));
    assert.isFalse(isMergeParent('edit'));
    assert.isFalse(isMergeParent('PARENT'));
    assert.isFalse(isMergeParent(0));

    assert.isTrue(isMergeParent(-23));
    assert.isTrue(isMergeParent(-1));
    assert.isTrue(isMergeParent('-42'));
  });

  test('findEditParentRevision', () => {
    let revisions = [
      {_number: 0},
      {_number: 1},
      {_number: 2},
    ];
    assert.strictEqual(findEditParentRevision(revisions), null);

    revisions = [...revisions, {_number: 'edit', basePatchNum: 3}];
    assert.strictEqual(findEditParentRevision(revisions), null);

    revisions = [...revisions, {_number: 3}];
    assert.deepEqual(findEditParentRevision(revisions), {_number: 3});
  });

  test('findEditParentPatchNum', () => {
    let revisions = [
      {_number: 0},
      {_number: 1},
      {_number: 2},
    ];
    assert.equal(findEditParentPatchNum(revisions), -1);

    revisions =
        [...revisions, {_number: 'edit', basePatchNum: 3}, {_number: 3}];
    assert.deepEqual(findEditParentPatchNum(revisions), 3);
  });

  test('sortRevisions', () => {
    const revisions = [
      {_number: 0},
      {_number: 2},
      {_number: 1},
    ];
    const sorted = [
      {_number: 2},
      {_number: 1},
      {_number: 0},
    ];

    assert.deepEqual(sortRevisions(revisions), sorted);

    // Edit patchset should follow directly after its basePatchNum.
    revisions.push({_number: 'edit', basePatchNum: 2});
    sorted.unshift({_number: 'edit', basePatchNum: 2});
    assert.deepEqual(sortRevisions(revisions), sorted);

    revisions[0].basePatchNum = 0;
    const edit = sorted.shift();
    edit.basePatchNum = 0;
    // Edit patchset should be at index 2.
    sorted.splice(2, 0, edit);
    assert.deepEqual(sortRevisions(revisions), sorted);
  });

  test('getParentIndex', () => {
    assert.equal(getParentIndex('-13'), 13);
    assert.equal(getParentIndex(-4), 4);
  });

  test('computeAllPatchSets', () => {
    const expected = [
      {num: 4, desc: 'test', sha: 'rev4'},
      {num: 3, desc: 'test', sha: 'rev3'},
      {num: 2, desc: 'test', sha: 'rev2'},
      {num: 1, desc: 'test', sha: 'rev1'},
    ];
    const patchNums = computeAllPatchSets({
      revisions: {
        rev3: {_number: 3, description: 'test', date: 3},
        rev1: {_number: 1, description: 'test', date: 1},
        rev4: {_number: 4, description: 'test', date: 4},
        rev2: {_number: 2, description: 'test', date: 2},
      },
    });
    assert.equal(patchNums.length, expected.length);
    for (let i = 0; i < expected.length; i++) {
      assert.deepEqual(patchNums[i], expected[i]);
    }
  });
});

