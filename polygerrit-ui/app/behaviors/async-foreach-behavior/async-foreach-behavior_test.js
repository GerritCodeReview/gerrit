// import '../../test/common-test-setup.js';
// import './async-foreach-behavior.js';
import 'chai/chai.js';
const assert = chai.assert;
import '../../test/common-test-setup.js';

import './async-foreach-behavior.js';
suite('async-foreach-behavior tests', () => {
  test('loops over each item', () => {
    const fn = sinon.stub().returns(Promise.resolve());
    return Gerrit.AsyncForeachBehavior.asyncForeach([1, 2, 3], fn)
        .then(() => {
          assert.isTrue(fn.calledThrice);
          assert.equal(fn.getCall(0).args[0], 1);
          assert.equal(fn.getCall(1).args[0], 2);
          assert.equal(fn.getCall(2).args[0], 3);
        });
  });

  test('halts on stop condition', () => {
    const stub = sinon.stub();
    const fn = (e, stop) => {
      stub(e);
      stop();
      return Promise.resolve();
    };
    return Gerrit.AsyncForeachBehavior.asyncForeach([1, 2, 3], fn)
        .then(() => {
          assert.isTrue(stub.calledOnce);
          assert.equal(stub.lastCall.args[0], 1);
        });
  });
});
