is_no_code_style_branch('refs/heads/stable-2.10').
is_no_code_style_branch('refs/heads/stable-2.11').
is_no_code_style_branch('refs/heads/stable-2.12').
is_no_code_style_branch('refs/heads/stable-2.13').

submit_rule(submit(CR, V, L)) :-
  gerrit:change_branch(B),
  is_no_code_style_branch(B),
  needs_library_compliance,
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L),
  gerrit:max_with_block(-1, 1, 'Verified', V).

submit_rule(submit(CR, V)) :-
  gerrit:change_branch(B),
  is_no_code_style_branch(B),
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Verified', V).

submit_rule(submit(CR)) :-
  gerrit:change_branch('refs/meta/config'),
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR).

submit_rule(submit(CR, CS, V, L)) :-
  needs_library_compliance,
  !,
  base(CR, CS, V),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, CS, V)) :-
  is_no_polygerrit_change,
  !,
  base(CR, CS, V).

submit_rule(submit(P, CS, V)) :-
  gerrit:max_with_block(-2, 2, 'PolyGerrit-Review', P),
  gerrit:max_with_block(-1, 1, 'Code-Style', CS),
  gerrit:max_with_block(-1, 1, 'Verified', V).

base(CR, CS, V) :-
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Code-Style', CS),
  gerrit:max_with_block(-1, 1, 'Verified', V).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^[.]buckversion$'), !.
needs_library_compliance :- gerrit:commit_delta('^WORKSPACE$'), !.

is_no_polygerrit_change :-
  gerrit:commit_delta('^(?!polygerrit-ui/|gerrit-server/src/main/resources/com/google/gerrit/server/mail/).*$').
