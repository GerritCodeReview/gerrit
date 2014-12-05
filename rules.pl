submit_rule(submit(CR, V, L)) :-
  gerrit:commit_delta('^lib/'),
  !,
  base(CR, V),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, V)) :-
  base(CR, V).

base(CR, V) :-
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_no_block(-1, 1, 'Verified', V).
