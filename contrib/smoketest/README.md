# Gerrit smoke test

TBD

## Python environment

Prerequisites:

- python 3.9
- pipenv

Install virtual python environment and run the script

``` bash
pipenv sync --dev
pipenv shell
./smoke-test
```

Use flake8 for style checking:

``` bash
flake8 smoke-test.py
```

Use black for auto-formatting:

``` bash
black smoke-test.py
```
