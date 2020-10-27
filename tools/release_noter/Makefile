COMMITS := 10

.PHONY: all clean

all: deploy black flake test

clean:
	rm -f release_noter.md

setup:
	pipenv install --dev

deploy:
	pipenv install --dev --deploy

black:
	pipenv run black release_noter.py

flake:
	pipenv run flake8 release_noter.py

help:
	pipenv run python release_noter.py -h

test:
	pipenv run python release_noter.py HEAD~$(COMMITS)..HEAD -l
