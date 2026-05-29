#!/usr/bin/env python3
from argparse import ArgumentParser, RawTextHelpFormatter
from itertools import islice
import getpass
import logging
import os

from pygerrit2 import GerritRestAPI, HTTPBasicAuth, HTTPBasicAuthFromNetrc
from tqdm import tqdm

EPILOG = """\
To query the list of changes which have been created or modified since the
given timestamp and write them to a file "changes-to-reindex.list" run
$ ./reindex.py -u gerrit-url -s timestamp

To reindex the list of changes in file "changes-to-reindex.list" run
$ ./reindex.py -u gerrit-url
"""


def _parse_options():
    parser = ArgumentParser(
        formatter_class=RawTextHelpFormatter,
        epilog=EPILOG,
    )
    parser.add_argument(
        "-u",
        "--url",
        dest="url",
        help="gerrit url",
    )
    parser.add_argument(
        "-s",
        "--since",
        dest="time",
        help=(
            "changes modified after the given 'TIME', inclusive. Must be in the\n"
            "format '2006-01-02[ 15:04:05[.890][ -0700]]', omitting the time defaults\n"
            "to 00:00:00 and omitting the timezone defaults to UTC."
        ),
    )
    parser.add_argument(
        "-f",
        "--file",
        default="changes-to-reindex.list",
        dest="file",
        help=(
            "file path to store list of changes if --since is given,\n"
            "otherwise file path to read list of changes from"
        ),
    )
    parser.add_argument(
        "-c",
        "--chunk",
        default=100,
        dest="chunksize",
        help="chunk size defining how many changes are reindexed per request",
        type=int,
    )
    parser.add_argument(
        "--cert",
        dest="cert",
        type=str,
        help="path to file containing custom ca certificates to trust",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        dest="verbose",
        action="store_true",
        help="verbose debugging output",
    )
    parser.add_argument(
        "-n",
        "--netrc",
        default=True,
        dest="netrc",
        action="store_true",
        help=(
            "read credentials from .netrc, default to environment variables\n"
            "USERNAME and PASSWORD, otherwise prompt for credentials interactively"
        ),
    )
    return parser.parse_args()


def _chunker(iterable, chunksize):
    it = map(lambda s: s.strip(), iterable)
    while True:
        chunk = list(islice(it, chunksize))
        if not chunk:
            return
        yield chunk


class Reindexer:
    """Class for reindexing Gerrit changes"""

    def __init__(self):
        self.options = _parse_options()
        self._init_logger()
        credentials = self._authenticate()
        if self.options.cert:
            certs = os.path.expanduser(self.options.cert)
            self.api = GerritRestAPI(
                url=self.options.url, auth=credentials, verify=certs
            )
        else:
            self.api = GerritRestAPI(url=self.options.url, auth=credentials)

    def _init_logger(self):
        self.logger = logging.getLogger("Reindexer")
        self.logger.setLevel(logging.DEBUG)
        h = logging.StreamHandler()
        if self.options.verbose:
            h.setLevel(logging.DEBUG)
        else:
            h.setLevel(logging.INFO)
        formatter = logging.Formatter("%(message)s")
        h.setFormatter(formatter)
        self.logger.addHandler(h)

    def _authenticate(self):
        username = password = None
        if self.options.netrc:
            auth = HTTPBasicAuthFromNetrc(url=self.options.url)
            username = auth.username
            password = auth.password
        if not username:
            username = os.environ.get("USERNAME")
        if not password:
            password = os.environ.get("PASSWORD")
        while not username:
            username = input("user: ")
        while not password:
            password = getpass.getpass("password: ")
        auth = HTTPBasicAuth(username, password)
        return auth

    def _query(self):
        start = 0
        more_changes = True
        while more_changes:
            query = f"since:{self.options.time}&start={start}&skip-visibility"
            for change in self.api.get(f"changes/?q={query}"):
                more_changes = change.get("_more_changes") is not None
                start += 1
                yield change.get("_number")
            break

    def _query_to_file(self):
        self.logger.debug(
            f"writing changes since {self.options.time} to file {self.options.file}:"
        )
        with open(self.options.file, "w") as output:
            for id in self._query():
                self.logger.debug(id)
                output.write(f"{id}\n")

    def _reindex_chunk(self, chunk):
        self.logger.debug(f"indexing {chunk}")
        response = self.api.post(
            "/config/server/index.changes",
            chunk,
        )
        self.logger.debug(f"response: {response}")

    def _reindex(self):
        self.logger.debug(f"indexing changes from file {self.options.file}")
        with open(self.options.file, "r") as f:
            with tqdm(unit="changes", desc="Indexed") as pbar:
                for chunk in _chunker(f, self.options.chunksize):
                    self._reindex_chunk(chunk)
                    pbar.update(len(chunk))

    def execute(self):
        if self.options.time:
            self._query_to_file()
        else:
            self._reindex()


def main():
    reindexer = Reindexer()
    reindexer.execute()


if __name__ == "__main__":
    main()
