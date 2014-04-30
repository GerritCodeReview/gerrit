CodeMirror.defineMode('gerrit_commit', function() {
  var TOKEN_NAMES = {
    '+': 'positive',
    '-': 'negative',
    '@': 'meta'
  };

  return {
    token: function(stream) {
      if (stream.sol() && stream.match(/^(Parent|Author|AuthorDate|Commit|CommitDate):/))
        return 'keyword';
      if (stream.sol() && stream.match(/^[a-zA-Z0-9-]+:/))
        return 'builtin';
      stream.skipToEnd();
      return null;
    }
  };
});
CodeMirror.defineMIME('text/x-gerrit-commit-message', 'gerrit_commit');
