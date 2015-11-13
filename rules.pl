submit_rule(submit(CR, V, L, N)) :-
  needs_library_compliance,
  !,
  base(CR, V, N),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, V, N)) :-
  base(CR, V, N).

base(CR, V, N) :-
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  max_no_block(1, 'Verified', V).
  max_no_block(1, 'Verified-Notedb', N).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^[.]buckversion$'), !.

max_no_block(Max, Label, label(Label, S)) :-
  number(Max), atom(Label),
  !,
  gerrit:max_no_block(Label, Max, S).