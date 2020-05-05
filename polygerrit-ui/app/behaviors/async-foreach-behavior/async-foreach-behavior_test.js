/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {AsyncForeachBehavior} from './async-foreach-behavior.js';

console.log('Test!');

if(window.__karma__.config.args.indexOf('--grep=$#%#') >= 0) {
  window.__karma__.config.args = window.__karma__.config.args.filter((v) => v !== '--grep=$#%#');  

  function markAllPendingRecursive(suites) {
    for(const suite of suites) {
      for(const test of suite.tests) {
        test.pending = true;
      }
      markAllPendingRecursive(suite.suites);
    }
  }
  suiteSetup(function() {
     markAllPendingRecursive(this.test.parent.suites);
  });
  
}
let ln = 57;
const originalKarmaResult = window.__karma__.result;
console.log(window.__karma__.files);
window.__karma__.result = function(result) {
  console.log(result);
  if (!result.id) {
    result.id = `${result.suite.join('$')}$${result.description}`;
    result.fullName = result.id;
    result.filePath = "/Users/dmfilippov/gerrit/gerrit/polygerrit-ui/app/behaviors/async-foreach-behavior/async-foreach-behavior_test.js";
    result.line = ln;
    ln++;
  }
  console.log(result);
  originalKarmaResult.apply(this, arguments);
};
console.log(window.__karma__);

suite('async-foreach-behavior tests', () => {
  test('loops over each item', () => {
    const fn = sinon.stub().returns(Promise.resolve());
    return AsyncForeachBehavior.asyncForeach([1, 2, 3], fn)
        .then(() => {
          assert.isTrue(fn.calledThrice);
          assert.equal(fn.getCall(0).args[0], 1);
          assert.equal(fn.getCall(1).args[0], 2);
          assert.equal(fn.getCall(2).args[0], 3);
        });
  });

  test('halts on stop condition', () => {
    console.log('x');
    const stub = sinon.stub();
    const fn = (e, stop) => {
      stub(e);
      stop();
      return Promise.resolve();
    };
    return AsyncForeachBehavior.asyncForeach([1, 2, 3], fn)
        .then(() => {
          assert.isTrue(stub.calledOnce);
          assert.equal(stub.lastCall.args[0], 1);
        });
  });
});
