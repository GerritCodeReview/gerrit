#!/usr/bin/env python
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
This script will populate an empty standard Gerrit instance with some
data for local testing.

TODO(hiesel): Make real git commits instead of empty changes
TODO(hiesel): Add comments
"""

from __future__ import print_function
import argparse
import atexit
import json
import os
import random
import shutil
import subprocess
import tempfile
import requests
import requests.auth

DEFAULT_TMP_PATH = "/tmp"
TMP_PATH = ""
BASE_URL = "http://localhost:%d/a/"

ADMIN_BASIC_AUTH = requests.auth.HTTPBasicAuth("admin", "secret")

# GROUP_ADMIN stores a GroupInfo for the admin group (see Gerrit rest docs)
# In addition, GROUP_ADMIN["name"] stores the admin group"s name.
GROUP_ADMIN = {}

HEADERS = {"Content-Type": "application/json", "charset": "UTF-8"}

# Random names from US Census Data
FIRST_NAMES = [
    "Casey", "Yesenia", "Shirley", "Tara", "Wanda", "Sheryl", "Jaime",
    "Elaine", "Charlotte", "Carly", "Bonnie", "Kirsten", "Kathryn", "Carla",
    "Katrina", "Melody", "Suzanne", "Sandy", "Joann", "Kristie", "Sally",
    "Emma", "Susan", "Amanda", "Alyssa", "Patty", "Angie", "Dominique",
    "Cynthia", "Jennifer", "Theresa", "Desiree", "Kaylee", "Maureen",
    "Jeanne", "Kellie", "Valerie", "Nina", "Judy", "Diamond", "Anita",
    "Rebekah", "Stefanie", "Kendra", "Erin", "Tammie", "Tracey", "Bridget",
    "Krystal", "Jasmin", "Sonia", "Meghan", "Rebecca", "Jeanette", "Meredith",
    "Beverly", "Natasha", "Chloe", "Selena", "Teresa", "Sheena", "Cassandra",
    "Rhonda", "Tami", "Jodi", "Shelly", "Angela", "Kimberly", "Terry",
    "Joanna", "Isabella", "Lindsey", "Loretta", "Dana", "Veronica", "Carolyn",
    "Laura", "Karen", "Dawn", "Alejandra", "Cassie", "Lorraine", "Yolanda",
    "Kerry", "Stephanie", "Caitlin", "Melanie", "Kerri", "Doris", "Sandra",
    "Beth", "Carol", "Vicki", "Shelia", "Bethany", "Rachael", "Donna",
    "Alexandra", "Barbara", "Ana", "Jillian", "Ann", "Rachel", "Lauren",
    "Hayley", "Misty", "Brianna", "Tanya", "Danielle", "Courtney",
    "Jacqueline", "Becky", "Christy", "Alisha", "Phyllis", "Faith", "Jocelyn",
    "Nancy", "Gloria", "Kristen", "Evelyn", "Julie", "Julia", "Kara",
    "Chelsey", "Cassidy", "Jean", "Chelsea", "Jenny", "Diana", "Haley",
    "Kristine", "Kristina", "Erika", "Jenna", "Alison", "Deanna", "Abigail",
    "Melissa", "Sierra", "Linda", "Monica", "Tasha", "Traci", "Yvonne",
    "Tracy", "Marie", "Maria", "Michaela", "Stacie", "April", "Morgan",
    "Cathy", "Darlene", "Cristina", "Emily" "Ian", "Russell", "Phillip", "Jay",
    "Barry", "Brad", "Frederick", "Fernando", "Timothy", "Ricardo", "Bernard",
    "Daniel", "Ruben", "Alexis", "Kyle", "Malik", "Norman", "Kent", "Melvin",
    "Stephen", "Daryl", "Kurt", "Greg", "Alex", "Mario", "Riley", "Marvin",
    "Dan", "Steven", "Roberto", "Lucas", "Leroy", "Preston", "Drew", "Fred",
    "Casey", "Wesley", "Elijah", "Reginald", "Joel", "Christopher", "Jacob",
    "Luis", "Philip", "Mark", "Rickey", "Todd", "Scott", "Terrence", "Jim",
    "Stanley", "Bobby", "Thomas", "Gabriel", "Tracy", "Marcus", "Peter",
    "Michael", "Calvin", "Herbert", "Darryl", "Billy", "Ross", "Dustin",
    "Jaime", "Adam", "Henry", "Xavier", "Dominic", "Lonnie", "Danny", "Victor",
    "Glen", "Perry", "Jackson", "Grant", "Gerald", "Garrett", "Alejandro",
    "Eddie", "Alan", "Ronnie", "Mathew", "Dave", "Wayne", "Joe", "Craig",
    "Terry", "Chris", "Randall", "Parker", "Francis", "Keith", "Neil", "Caleb",
    "Jon", "Earl", "Taylor", "Bryce", "Brady", "Max", "Sergio", "Leon", "Gene",
    "Darin", "Bill", "Edgar", "Antonio", "Dalton", "Arthur", "Austin",
    "Cristian", "Kevin", "Omar", "Kelly", "Aaron", "Ethan", "Tom", "Isaac",
    "Maurice", "Gilbert", "Hunter", "Willie", "Harry", "Dale", "Darius",
    "Jerome", "Jason", "Harold", "Kerry", "Clarence", "Gregg", "Shane",
    "Eduardo", "Micheal", "Howard", "Vernon", "Rodney", "Anthony", "Levi",
    "Larry", "Franklin", "Jimmy", "Jonathon", "Carl",
]

LAST_NAMES = [
    "Savage", "Hendrix", "Moon", "Larsen", "Rocha", "Burgess", "Bailey",
    "Farley", "Moses", "Schmidt", "Brown", "Hoover", "Klein", "Jennings",
    "Braun", "Rangel", "Casey", "Dougherty", "Hancock", "Wolf", "Henry",
    "Thomas", "Bentley", "Barnett", "Kline", "Pitts", "Rojas", "Sosa", "Paul",
    "Hess", "Chase", "Mckay", "Bender", "Colins", "Montoya", "Townsend",
    "Potts", "Ayala", "Avery", "Sherman", "Tapia", "Hamilton", "Ferguson",
    "Huang", "Hooper", "Zamora", "Logan", "Lloyd", "Quinn", "Monroe", "Brock",
    "Ibarra", "Fowler", "Weiss", "Montgomery", "Diaz", "Dixon", "Olson",
    "Robertson", "Arias", "Benjamin", "Abbott", "Stein", "Schroeder", "Beck",
    "Velasquez", "Barber", "Nichols", "Ortiz", "Burns", "Moody", "Stokes",
    "Wilcox", "Rush", "Michael", "Kidd", "Rowland", "Mclean", "Saunders",
    "Chung", "Newton", "Potter", "Hickman", "Ray", "Larson", "Figueroa",
    "Duncan", "Sparks", "Rose", "Hodge", "Huynh", "Joseph", "Morales",
    "Beasley", "Mora", "Fry", "Ross", "Novak", "Hahn", "Wise", "Knight",
    "Frederick", "Heath", "Pollard", "Vega", "Mcclain", "Buckley", "Conrad",
    "Cantrell", "Bond", "Mejia", "Wang", "Lewis", "Johns", "Mcknight",
    "Callahan", "Reynolds", "Norris", "Burnett", "Carey", "Jacobson", "Oneill",
    "Oconnor", "Leonard", "Mckenzie", "Hale", "Delgado", "Spence", "Brandt",
    "Obrien", "Bowman", "James", "Avila", "Roberts", "Barker", "Cohen",
    "Bradley", "Prince", "Warren", "Summers", "Little", "Caldwell", "Garrett",
    "Hughes", "Norton", "Burke", "Holden", "Merritt", "Lee", "Frank", "Wiley",
    "Ho", "Weber", "Keith", "Winters", "Gray", "Watts", "Brady", "Aguilar",
    "Nicholson", "David", "Pace", "Cervantes", "Davis", "Baxter", "Sanchez",
    "Singleton", "Taylor", "Strickland", "Glenn", "Valentine", "Roy",
    "Cameron", "Beard", "Norman", "Fritz", "Anthony", "Koch", "Parrish",
    "Herman", "Hines", "Sutton", "Gallegos", "Stephenson", "Lozano",
    "Franklin", "Howe", "Bauer", "Love", "Ali", "Ellison", "Lester", "Guzman",
    "Jarvis", "Espinoza", "Fletcher", "Burton", "Woodard", "Peterson",
    "Barajas", "Richard", "Bryan", "Goodman", "Cline", "Rowe", "Faulkner",
    "Crawford", "Mueller", "Patterson", "Hull", "Walton", "Wu", "Flores",
    "York", "Dickson", "Barnes", "Fisher", "Strong", "Juarez", "Fitzgerald",
    "Schmitt", "Blevins", "Villa", "Sullivan", "Velazquez", "Horton",
    "Meadows", "Riley", "Barrera", "Neal", "Mendez", "Mcdonald", "Floyd",
    "Lynch", "Mcdowell", "Benson", "Hebert", "Livingston", "Davies",
    "Richardson", "Vincent", "Davenport", "Osborn", "Mckee", "Marshall",
    "Ferrell", "Martinez", "Melton", "Mercer", "Yoder", "Jacobs", "Mcdaniel",
    "Mcmillan", "Peters", "Atkinson", "Wood", "Briggs", "Valencia", "Chandler",
    "Rios", "Hunter", "Bean", "Hicks", "Hays", "Lucero", "Malone", "Waller",
    "Banks", "Myers", "Mitchell", "Grimes", "Houston", "Hampton", "Trujillo",
    "Perkins", "Moran", "Welch", "Contreras", "Montes", "Ayers", "Hayden",
    "Daniel", "Weeks", "Porter", "Gill", "Mullen", "Nolan", "Dorsey", "Crane",
    "Estes", "Lam", "Wells", "Cisneros", "Giles", "Watson", "Vang", "Scott",
    "Knox", "Hanna", "Fields",
]


def clean(json_string):
    # Strip JSON XSS Tag
    json_string = json_string.strip()
    if json_string.startswith(")]}'"):
        return json_string[5:]
    return json_string


def basic_auth(user):
    return requests.auth.HTTPBasicAuth(user["username"], user["http_password"])


def fetch_admin_group():
    global GROUP_ADMIN
    # Get admin group
    r = json.loads(clean(requests.get(
        BASE_URL + "groups/?suggest=ad&p=All-Projects",
        headers=HEADERS,
        auth=ADMIN_BASIC_AUTH).text))
    admin_group_name = r.keys()[0]
    GROUP_ADMIN = r[admin_group_name]
    GROUP_ADMIN["name"] = admin_group_name


def generate_random_text():
    return " ".join([random.choice("lorem ipsum "
                                   "doleret delendam "
                                   "\n esse".split(" ")) for _ in range(1,
                                                                        100)])


def set_up():
    global TMP_PATH
    TMP_PATH = tempfile.mkdtemp()
    atexit.register(clean_up)
    os.makedirs(TMP_PATH + "/ssh")
    os.makedirs(TMP_PATH + "/repos")
    fetch_admin_group()


def get_random_users(num_users):
    users = random.sample([(f, l) for f in FIRST_NAMES for l in LAST_NAMES],
                          num_users)
    names = []
    for u in users:
        names.append({"firstname": u[0],
                      "lastname": u[1],
                      "name": u[0] + " " + u[1],
                      "username": u[0] + u[1],
                      "email": u[0] + "." + u[1] + "@gerritcodereview.com",
                      "http_password": "secret",
                      "groups": []})
    return names


def generate_ssh_keys(gerrit_users):
    for user in gerrit_users:
        key_file = TMP_PATH + "/ssh/" + user["username"] + ".key"
        subprocess.check_output(["ssh-keygen", "-f", key_file, "-N", ""])
        with open(key_file + ".pub", "r") as f:
            user["ssh_key"] = f.read()


def create_gerrit_groups():
    groups = [
        {"name": "iOS-Maintainers", "description": "iOS Maintainers",
         "visible_to_all": True, "owner": GROUP_ADMIN["name"],
         "owner_id": GROUP_ADMIN["id"]},
        {"name": "Android-Maintainers", "description": "Android Maintainers",
         "visible_to_all": True, "owner": GROUP_ADMIN["name"],
         "owner_id": GROUP_ADMIN["id"]},
        {"name": "Backend-Maintainers", "description": "Backend Maintainers",
         "visible_to_all": True, "owner": GROUP_ADMIN["name"],
         "owner_id": GROUP_ADMIN["id"]},
        {"name": "Script-Maintainers", "description": "Script Maintainers",
         "visible_to_all": True, "owner": GROUP_ADMIN["name"],
         "owner_id": GROUP_ADMIN["id"]},
        {"name": "Security-Team", "description": "Sec Team",
         "visible_to_all": False, "owner": GROUP_ADMIN["name"],
         "owner_id": GROUP_ADMIN["id"]}]
    for g in groups:
        requests.put(BASE_URL + "groups/" + g["name"],
                     json.dumps(g),
                     headers=HEADERS,
                     auth=ADMIN_BASIC_AUTH)
    return [g["name"] for g in groups]


def create_gerrit_projects(owner_groups):
    projects = [
        {"id": "android", "name": "Android", "parent": "All-Projects",
         "branches": ["master"], "description": "Our android app.",
         "owners": [owner_groups[0]], "create_empty_commit": True},
        {"id": "ios", "name": "iOS", "parent": "All-Projects",
         "branches": ["master"], "description": "Our ios app.",
         "owners": [owner_groups[1]], "create_empty_commit": True},
        {"id": "backend", "name": "Backend", "parent": "All-Projects",
         "branches": ["master"], "description": "Our awesome backend.",
         "owners": [owner_groups[2]], "create_empty_commit": True},
        {"id": "scripts", "name": "Scripts", "parent": "All-Projects",
         "branches": ["master"], "description": "some small scripts.",
         "owners": [owner_groups[3]], "create_empty_commit": True}]
    for p in projects:
        requests.put(BASE_URL + "projects/" + p["name"],
                     json.dumps(p),
                     headers=HEADERS,
                     auth=ADMIN_BASIC_AUTH)
    return [p["name"] for p in projects]


def create_gerrit_users(gerrit_users):
    for user in gerrit_users:
        requests.put(BASE_URL + "accounts/" + user["username"],
                     json.dumps(user),
                     headers=HEADERS,
                     auth=ADMIN_BASIC_AUTH)


def create_change(user, project_name):
    random_commit_message = generate_random_text()
    change = {
        "project": project_name,
        "subject": random_commit_message.split("\n")[0],
        "branch": "master",
        "status": "NEW",
    }
    requests.post(BASE_URL + "changes/",
                  json.dumps(change),
                  headers=HEADERS,
                  auth=basic_auth(user))


def clean_up():
    shutil.rmtree(TMP_PATH)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("-u", "--user_count", action="store",
                 default=100,
                 type='int',
                 help="number of users to generate")
    p.add_argument("-p", "--port", action="store",
                 default=8080,
                 type='int',
                 help="port of server")
    options = vars(p.parse_args())
    global BASE_URL
    BASE_URL = BASE_URL % options['port']
    print(BASE_URL)

    set_up()
    gerrit_users = get_random_users(options['user_count'])

    group_names = create_gerrit_groups()
    for idx, u in enumerate(gerrit_users):
        u["groups"].append(group_names[idx % len(group_names)])
        if idx % 5 == 0:
            # Also add to security group
            u["groups"].append(group_names[4])

    generate_ssh_keys(gerrit_users)
    create_gerrit_users(gerrit_users)

    project_names = create_gerrit_projects(group_names)

    for idx, u in enumerate(gerrit_users):
        for _ in xrange(random.randint(1, 5)):
            create_change(u, project_names[4 * idx / len(gerrit_users)])

main()
