submit_rule(submit(CR)) :-
  gerrit:change_branch('refs/meta/config'),
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR).

submit_rule(submit(CR, VB, VZ, L)) :-
  needs_library_compliance,
  !,
  base(CR, VB, VZ),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, VB, VZ)) :-
  is_no_polygerrit_change,
  !,
  base(CR, VB, VZ).

submit_rule(submit(P, VB, VZ)) :-
  gerrit:max_with_block(-2, 2, 'PolyGerrit-Review', P),
  gerrit:max_with_block(-1, 1, 'Verified-Buck', VB),
  gerrit:max_with_block(-1, 1, 'Verified-Bazel', VZ).

base(CR, VB, VZ) :-
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Verified-Buck', VB),
  gerrit:max_with_block(-1, 1, 'Verified-Bazel', VZ).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^[.]buckversion$'), !.
needs_library_compliance :- gerrit:commit_delta('^WORKSPACE$'), !.

is_no_polygerrit_change :-
  gerrit:commit_delta('^(?!polygerrit-ui/).*$').
