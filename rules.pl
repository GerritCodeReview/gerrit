submit_rule(submit(CR)) :-
  gerrit:change_branch('refs/meta/config'),
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR).

submit_rule(submit(CR, V, L)) :-
  needs_library_compliance,
  !,
  base(CR, V),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, V)) :-
  is_no_polygerrit_change,
  !,
  base(CR, V).

submit_rule(submit(P, V)) :-
  gerrit:max_with_block(-2, 2, 'PolyGerrit-Review', P),
  gerrit:max_with_block(-1, 1, 'Verified', V).

base(CR, V) :-
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Verified', V),
  gerrit:max_with_block(-1, 1, 'Code-Style', CS).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^[.]buckversion$'), !.
needs_library_compliance :- gerrit:commit_delta('^WORKSPACE$'), !.

is_no_polygerrit_change :-
  gerrit:commit_delta('^(?!polygerrit-ui/|gerrit-server/src/main/resources/com/google/gerrit/server/mail/).*$').
