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
        assert.isNotOk(result.newMessages);
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
        assert.isNotOk(result.newMessages);
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
        assert.isNotOk(result.newMessages);
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
        assert.deepEqual(result.newMessages, {message: 'blah blah'});
        done();
      });
});
