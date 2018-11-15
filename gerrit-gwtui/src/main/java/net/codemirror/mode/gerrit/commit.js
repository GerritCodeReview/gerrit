CodeMirror.defineMode('gerrit_commit', function() {
  var header = /^(Parent|Author|AuthorDate|Commit|CommitDate):/;
  var id = /^Change-Id: I[0-9a-f]{40}/;
  var footer = /^[A-Z][A-Za-z0-9-]+:/;
  var sha1 = /\b[0-9a-f]{6,40}/;

  return {
    token: function(stream) {
      if (stream.sol()) {
        if (stream.match(header))
          return 'keyword';
        if (stream.match(id) || stream.match(footer))
          return 'builtin';
      }

      stream.eatSpace();
      if (stream.match(sha1))
        return 'variable-2';
      if (stream.match(/".*"/))
        return 'string';
      stream.next();
      return null;
    }
  };
});
CodeMirror.defineMIME('text/x-gerrit-commit-message', 'gerrit_commit');
