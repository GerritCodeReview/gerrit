# Prolog Unit Test Examples

## Run all examples

Build a local gerrit.war and then run the script:

    ./run.sh

Note that a local Gerrit server is not needed because
these unit test examples redefine wrappers of the `gerrit:change\*`
rules to provide mocked change data.

## Add a new unit test

Please follow the pattern in `t1.pl`, `t2.pl`, or `t3.pl`.

* Put code to be tested in a file, e.g. `rules.pl`.
  For easy unit testing, split long clauses into short ones
  and test every positive and negative path.

* Create a new unit test file, e.g. `t1.pl`,
  which should _load_ the test source file and `utils.pl`.

      % First load all source files and the utils.pl.
      :- load([aosp_rules,utils]).

      :- begin_tests(t1).  % give this test any name

      % Use test0/1 or test1/1 to verify failed/passed goals.

      :- end_tests(_,0).   % check total pass/fail counts

* Optionally replace calls to gerrit functions that depend on repository.
  For example, define the following wrappers and in source code, use
  `change_branch/1` instead of `gerrti:change_branch/1`.

      change_branch(X) :- gerrit:change_branch(X).
      commit_label(L,U) :- gerrit:commit_label(L,U).

* In unit test file, redefine the gerrit function wrappers and test.
  For example, in `t3.pl`, we have:

      :- redefine(uploader,1,uploader(user(42))).  % mocked uploader
      :- test1(uploader(user(42))).
      :- test0(is_exempt_uploader).

      % is_exempt_uploader/0 is expected to fail because it is
      % is_exempt_uploader :- uploader(user(Id)), memberchk(Id, [104, 106]).

      % Note that gerrit:remove_label does not depend on Gerrit repository,
      % so its caller remove_label/1 is tested without any redefinition.

      :- test1(remove_label('MyReview',[],[])).
      :- test1(remove_label('MyReview',submit(),submit())).
