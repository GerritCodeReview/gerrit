
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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







<script src="/node_modules/page/page.js"></script>






import '../../../test/common-test-setup.js';
import './gr-create-group-dialog.js';
import page from 'page/page.mjs';

const basicFixture = fixtureFromElement('gr-create-group-dialog');



suite('gr-create-group-dialog tests', () => {
  let element;
  let sandbox;
  const GROUP_NAME = 'test-group';

  setup(() => {
    sandbox = sinon.sandbox.create();
    stub('gr-rest-api-interface', {
      getLoggedIn() { return Promise.resolve(true); },
    });
    element = basicFixture.instantiate();
  });

  teardown(() => {
    sandbox.restore();
  });

  test('name is updated correctly', done => {
    assert.isFalse(element.hasNewGroupName);

    const inputEl = element.root.querySelector('iron-input');
    inputEl.bindValue = GROUP_NAME;

    setTimeout(() => {
      assert.isTrue(element.hasNewGroupName);
      assert.deepEqual(element._name, GROUP_NAME);
      done();
    });
  });

  test('test for redirecting to group on successful creation', done => {
    sandbox.stub(element.$.restAPI, 'createGroup')
        .returns(Promise.resolve({status: 201}));

    sandbox.stub(element.$.restAPI, 'getGroupConfig')
        .returns(Promise.resolve({group_id: 551}));

    const showStub = sandbox.stub(page, 'show');
    element.handleCreateGroup()
        .then(() => {
          assert.isTrue(showStub.calledWith('/admin/groups/551'));
          done();
        });
  });

  test('test for unsuccessful group creation', done => {
    sandbox.stub(element.$.restAPI, 'createGroup')
        .returns(Promise.resolve({status: 409}));

    sandbox.stub(element.$.restAPI, 'getGroupConfig')
        .returns(Promise.resolve({group_id: 551}));

    const showStub = sandbox.stub(page, 'show');
    element.handleCreateGroup()
        .then(() => {
          assert.isFalse(showStub.called);
          done();
        });
  });
});

