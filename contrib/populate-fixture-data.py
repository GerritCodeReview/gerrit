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

# This scipt will populate an emtpy standard Gerrit instance with some
# data for local testing.
# TODO: Make real git commits instead of empty changes
# TODO: Add comments

import sys
import json
import requests
import requests.auth
import os
import shutil
import random
import subprocess

DEFAULT_TMP_PATH = "/tmp"
TMP_PATH = ''
BASE_URL = 'http://localhost:8080/a/'
ACCESS_URL = BASE_URL + 'access/'
ACCOUNTS_URL = BASE_URL + 'accounts/'
CHANGES_URL = BASE_URL + 'changes/'
CONFIG_URL = BASE_URL + 'config/'
GROUPS_URL = BASE_URL + 'groups/'
PLUGINS_URL = BASE_URL + 'plugins/'
PROJECTS_URL = BASE_URL + 'projects/'

ADMIN_DIGEST = requests.auth.HTTPDigestAuth('admin',
                                            'secret')

# GROUP_ADMIN stores a GroupInfo for the admin group (see Gerrit rest docs)
# In addition, GROUP_ADMIN['name'] stores the admin group's name.
GROUP_ADMIN = {}

HEADERS = {'Content-Type': 'application/json', 'charset': 'UTF-8'}

# Random names from US Census Data
first_names_female = [
  'Felicia', 'Shelby', 'Sherri', 'Leah', 'Annette', 'Sherry', 'Monique',
  'Casey', 'Yesenia', 'Shirley', 'Tara', 'Wanda', 'Sheryl', 'Jaime', 'Elaine',
  'Charlotte', 'Carly', 'Bonnie', 'Kirsten', 'Kathryn', 'Carla', 'Katrina',
  'Melody', 'Suzanne', 'Sandy', 'Joann', 'Kristie', 'Sally', 'Emma', 'Susan',
  'Amanda', 'Alyssa', 'Patty', 'Angie', 'Dominique', 'Cynthia', 'Jennifer',
  'Latoya', 'Erica', 'Claire', 'Sheila', 'Tiffany', 'Nicole', 'Sharon',
  'Kathleen', 'Sara', 'Lori', 'Molly', 'Stacy', 'Jordan', 'Miranda', 'Mariah',
  'Makayla', 'Kiara', 'Isabel', 'Leslie', 'Mikayla', 'Tabitha', 'Brittney',
  'Wendy', 'Caroline', 'Deborah', 'Shawna', 'Martha', 'Elizabeth', 'Joy',
  'Theresa', 'Desiree', 'Kaylee', 'Maureen', 'Jeanne', 'Kellie', 'Valerie',
  'Nina', 'Judy', 'Diamond', 'Anita', 'Rebekah', 'Stefanie', 'Kendra', 'Erin',
  'Tammie', 'Tracey', 'Bridget', 'Krystal', 'Jasmin', 'Sonia', 'Meghan',
  'Rebecca', 'Jeanette', 'Meredith', 'Beverly', 'Natasha', 'Chloe', 'Selena',
  'Teresa', 'Sheena', 'Cassandra', 'Rhonda', 'Tami', 'Jodi', 'Shelly', 'Angela',
  'Kimberly', 'Terry', 'Joanna', 'Isabella', 'Lindsey', 'Loretta', 'Dana',
  'Veronica', 'Carolyn', 'Laura', 'Karen', 'Dawn', 'Alejandra', 'Cassie',
  'Lorraine', 'Yolanda', 'Kerry', 'Stephanie', 'Caitlin', 'Melanie', 'Kerri',
  'Doris', 'Sandra', 'Beth', 'Carol', 'Vicki', 'Shelia', 'Bethany', 'Rachael',
  'Donna', 'Alexandra', 'Barbara', 'Ana', 'Jillian', 'Ann', 'Rachel', 'Lauren',
  'Hayley', 'Misty', 'Brianna', 'Tanya', 'Danielle', 'Courtney', 'Jacqueline',
  'Becky', 'Christy', 'Alisha', 'Phyllis', 'Faith', 'Jocelyn', 'Nancy',
  'Gloria', 'Kristen', 'Evelyn', 'Julie', 'Julia', 'Kara', 'Chelsey', 'Cassidy',
  'Jean', 'Chelsea', 'Jenny', 'Diana', 'Haley', 'Kristine', 'Kristina', 'Erika',
  'Jenna', 'Alison', 'Deanna', 'Abigail', 'Melissa', 'Sierra', 'Linda',
  'Monica', 'Tasha', 'Traci', 'Yvonne', 'Tracy', 'Marie', 'Maria', 'Michaela',
  'Stacie', 'April', 'Morgan', 'Cathy', 'Darlene', 'Cristina', 'Emily'
]

first_names_male = [
  'Ian', 'Russell', 'Phillip', 'Jay', 'Barry', 'Brad', 'Frederick', 'Fernando',
  'Timothy', 'Ricardo', 'Bernard', 'Daniel', 'Ruben', 'Alexis', 'Kyle', 'Malik',
  'Norman', 'Kent', 'Melvin', 'Stephen', 'Daryl', 'Kurt', 'Greg', 'Alex',
  'Mario', 'Riley', 'Marvin', 'Dan', 'Steven', 'Roberto', 'Lucas', 'Leroy',
  'Preston', 'Drew', 'Fred', 'Casey', 'Wesley', 'Elijah', 'Reginald', 'Joel',
  'Christopher', 'Jacob', 'Luis', 'Philip', 'Mark', 'Rickey', 'Todd', 'Scott',
  'Terrence', 'Jim', 'Stanley', 'Bobby', 'Thomas', 'Gabriel', 'Tracy', 'Marcus',
  'Peter', 'Michael', 'Calvin', 'Herbert', 'Darryl', 'Billy', 'Ross', 'Dustin',
  'Jaime', 'Adam', 'Henry', 'Xavier', 'Dominic', 'Lonnie', 'Danny', 'Victor',
  'Glen', 'Perry', 'Jackson', 'Grant', 'Gerald', 'Garrett', 'Alejandro',
  'Travis', 'Darren', 'Brandon', 'Geoffrey', 'Dylan', 'Derek', 'Colin', 'Wyatt',
  'Rick', 'Alfred', 'Don', 'John', 'Jake', 'Eugene', 'Mike', 'Joseph', 'Samuel',
  'Nicholas', 'Johnny', 'Bruce', 'Frank', 'Kirk', 'Brett', 'Kenneth', 'Hector',
  'Eddie', 'Alan', 'Ronnie', 'Mathew', 'Dave', 'Wayne', 'Joe', 'Craig',
  'Terry', 'Chris', 'Randall', 'Parker', 'Francis', 'Keith', 'Neil', 'Caleb',
  'Jon', 'Earl', 'Taylor', 'Bryce', 'Brady', 'Max', 'Sergio', 'Leon', 'Gene',
  'Darin', 'Bill', 'Edgar', 'Antonio', 'Dalton', 'Arthur', 'Austin', 'Cristian',
  'Kevin', 'Omar', 'Kelly', 'Aaron', 'Ethan', 'Tom', 'Isaac', 'Maurice',
  'Gilbert', 'Hunter', 'Willie', 'Harry', 'Dale', 'Darius', 'Jerome', 'Jason',
  'Harold', 'Kerry', 'Clarence', 'Gregg', 'Shane', 'Eduardo', 'Micheal',
  'Howard', 'Vernon', 'Rodney', 'Anthony', 'Levi', 'Larry', 'Franklin', 'Jimmy',
  'Jonathon', 'Carl', 'Connor', 'Evan', 'Cory', 'Alexander', 'Adrian', 'Pedro',
  'Bryan', 'Javier', 'Manuel', 'Justin', 'Brendan', 'Tanner', 'Dean', 'Guy',
  'Zachary', 'Marc', 'Trevor', 'Tommy'
]

last_names = [
  'Savage', 'Hendrix', 'Moon', 'Larsen', 'Rocha', 'Burgess', 'Bailey', 'Farley',
  'Moses', 'Schmidt', 'Brown', 'Hoover', 'Klein', 'Jennings', 'Braun', 'Rangel',
  'Casey', 'Dougherty', 'Hancock', 'Wolf', 'Henry', 'Thomas', 'Bentley',
  'Barnett', 'Kline', 'Pitts', 'Rojas', 'Sosa', 'Paul', 'Hess', 'Chase',
  'Mckay', 'Bender', 'Colins', 'Montoya', 'Townsend', 'Potts', 'Ayala', 'Avery',
  'Sherman', 'Tapia', 'Hamilton', 'Ferguson', 'Huang', 'Hooper', 'Zamora',
  'Logan', 'Lloyd', 'Quinn', 'Monroe', 'Brock', 'Ibarra', 'Fowler', 'Weiss',
  'Montgomery', 'Diaz', 'Dixon', 'Olson', 'Robertson', 'Arias', 'Benjamin',
  'Abbott', 'Stein', 'Schroeder', 'Beck', 'Velasquez', 'Barber', 'Nichols',
  'Ortiz', 'Burns', 'Moody', 'Stokes', 'Wilcox', 'Rush', 'Michael', 'Kidd',
  'Rowland', 'Mclean', 'Saunders', 'Chung', 'Newton', 'Potter', 'Hickman',
  'Ray', 'Larson', 'Figueroa', 'Duncan', 'Sparks', 'Rose', 'Hodge', 'Huynh',
  'Joseph', 'Morales', 'Beasley', 'Mora', 'Fry', 'Ross', 'Novak', 'Hahn',
  'Wise', 'Knight', 'Frederick', 'Heath', 'Pollard', 'Vega', 'Mcclain',
  'Buckley', 'Conrad', 'Cantrell', 'Bond', 'Mejia', 'Wang', 'Lewis', 'Johns',
  'Mcknight', 'Callahan', 'Reynolds', 'Norris', 'Burnett', 'Carey', 'Jacobson',
  'Oneill', 'Oconnor', 'Leonard', 'Mckenzie', 'Hale', 'Delgado', 'Spence',
  'Brandt', 'Obrien', 'Bowman', 'James', 'Avila', 'Roberts', 'Barker', 'Cohen',
  'Bradley', 'Prince', 'Warren', 'Summers', 'Little', 'Caldwell', 'Garrett',
  'Hughes', 'Norton', 'Burke', 'Holden', 'Merritt', 'Lee', 'Frank', 'Wiley',
  'Ho', 'Weber', 'Keith', 'Winters', 'Gray', 'Watts', 'Brady', 'Aguilar',
  'Nicholson', 'David', 'Pace', 'Cervantes', 'Davis', 'Baxter', 'Sanchez',
  'Singleton', 'Taylor', 'Strickland', 'Glenn', 'Valentine', 'Roy', 'Cameron',
  'Beard', 'Norman', 'Fritz', 'Anthony', 'Koch', 'Parrish', 'Herman', 'Hines',
  'Sutton', 'Gallegos', 'Stephenson', 'Lozano', 'Franklin', 'Howe', 'Bauer',
  'Love', 'Ali', 'Ellison', 'Lester', 'Guzman', 'Jarvis', 'Espinoza',
  'Fletcher', 'Burton', 'Woodard', 'Peterson', 'Barajas', 'Richard', 'Bryan',
  'Goodman', 'Cline', 'Rowe', 'Faulkner', 'Crawford', 'Mueller', 'Patterson',
  'Hull', 'Walton', 'Wu', 'Flores', 'York', 'Dickson', 'Barnes', 'Fisher',
  'Strong', 'Juarez', 'Fitzgerald', 'Schmitt', 'Blevins', 'Villa', 'Sullivan',
  'Velazquez', 'Horton', 'Meadows', 'Riley', 'Barrera', 'Neal', 'Mendez',
  'Mcdonald', 'Floyd', 'Lynch', 'Mcdowell', 'Benson', 'Hebert', 'Livingston',
  'Davies', 'Richardson', 'Vincent', 'Davenport', 'Osborn', 'Mckee', 'Marshall',
  'Ferrell', 'Martinez', 'Melton', 'Mercer', 'Yoder', 'Jacobs', 'Mcdaniel',
  'Mcmillan', 'Peters', 'Atkinson', 'Wood', 'Briggs', 'Valencia', 'Chandler',
  'Rios', 'Hunter', 'Bean', 'Hicks', 'Hays', 'Lucero', 'Malone', 'Waller',
  'Banks', 'Myers', 'Mitchell', 'Grimes', 'Houston', 'Hampton', 'Trujillo',
  'Perkins', 'Moran', 'Welch', 'Contreras', 'Montes', 'Ayers', 'Hayden',
  'Daniel', 'Weeks', 'Porter', 'Gill', 'Mullen', 'Nolan', 'Dorsey', 'Crane',
  'Estes', 'Lam', 'Wells', 'Cisneros', 'Giles', 'Watson', 'Vang', 'Scott',
  'Knox', 'Hanna', 'Fields', 'Suarez', 'Mathews', 'Weaver', 'Page', 'Stewart',
  'Mcguire', 'Brewer', 'Jimenez', 'Chambers',
]

first_names = first_names_female + first_names_male


def clean(jsonString):
  # Strip JSON XSS Tag
  if jsonString.startswith("\n)]}'"):
    return jsonString[5:]
  return jsonString


def digestAuth(user):
  return requests.auth.HTTPDigestAuth(user['username'],
                                      user['http_password'])


def fetchAdminGroup():
  global GROUP_ADMIN
  # Get admin group
  r = json.loads(clean(requests.get(GROUPS_URL + '?suggest=ad&p=All-Projects',
                                    headers=HEADERS,
                                    auth=ADMIN_DIGEST).text))
  adminGroupName = r.keys()[0]
  GROUP_ADMIN = r[adminGroupName]
  GROUP_ADMIN['name'] = adminGroupName


def generateRandomText():
  return ' '.join([random.choice('lorem ipsum '
                                 'doleret delendam '
                                 '\n esse'.split(' ')) for x in xrange(1, 100)])


def setUp():
  os.makedirs(TMP_PATH + "/ssh")
  os.makedirs(TMP_PATH + "/repos")
  fetchAdminGroup()


def getRandomUsers(numUsers):
  names = []
  for x in range(0, numUsers):
    firstName = random.choice(first_names)
    lastName = random.choice(last_names)
    names.append({'firstname': firstName,
                  'lastname': lastName,
                  'name': firstName + ' ' + lastName,
                  'username': firstName + lastName,
                  'email': firstName + '.' + lastName + '@gmail.com',
                  'http_password': 'secret',
                  'groups': []})
  return names


def generateSSHKeys(gerritUsers):
  for user in gerritUsers:
    keyFile = TMP_PATH + '/ssh/' + user['username'] + '.key'
    subprocess.call("ssh-keygen -f " + keyFile + " -N '' &> /dev/null",
                    shell=True)
    with open(keyFile + '.pub', 'r') as f:
      user["ssh_key"] = f.read()


def createGerritGroups():
  groups = [
    {"name": "iOS-Maintainers", "description": "iOS Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Android-Maintainers", "description": "Android Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Backend-Maintainers", "description": "Backend Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Script-Maintainers", "description": "Script Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Security-Team", "description": "Sec Team",
     "visible_to_all": False, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']}]
  for g in groups:
    requests.put(GROUPS_URL + g['name'],
                 json.dumps(g),
                 headers=HEADERS,
                 auth=ADMIN_DIGEST)
  return [g['name'] for g in groups]


def createGerritProjects(ownerGroups):
  projects = [
    {"id": "android", "name": "Android", "parent": "All-Projects",
     'branches': ['master'], "description": "Our android app.",
     'owners': [ownerGroups[0]], 'create_empty_commit': True},
    {"id": "ios", "name": "iOS", "parent": "All-Projects",
     'branches': ['master'], "description": "Our ios app.",
     'owners': [ownerGroups[1]], 'create_empty_commit': True},
    {"id": "backend", "name": "Backend", "parent": "All-Projects",
     'branches': ['master'], "description": "Our awesome backend.",
     'owners': [ownerGroups[2]], 'create_empty_commit': True},
    {"id": "scripts", "name": "Scripts", "parent": "All-Projects",
     'branches': ['master'], "description": "some small scripts.",
     'owners': [ownerGroups[3]], 'create_empty_commit': True}]
  for p in projects:
    requests.put(PROJECTS_URL + p['name'],
                 json.dumps(p),
                 headers=HEADERS,
                 auth=ADMIN_DIGEST)
  return [p['name'] for p in projects]


def createGerritUsers(gerritUsers):
  for user in gerritUsers:
    requests.put(ACCOUNTS_URL + user['username'],
                 json.dumps(user),
                 headers=HEADERS,
                 auth=ADMIN_DIGEST)


def cloneRepo():
  print 'TODO'


def createChange(user, projectName):
  randomCommitMessage = generateRandomText()
  change = {
    "project": projectName,
    "subject": randomCommitMessage.split('\n')[0],
    "branch": "master",
    "status": "NEW"
  }
  requests.post(CHANGES_URL,
                json.dumps(change),
                headers=HEADERS,
                auth=digestAuth(user))


def cleanUp():
  tmpPath = raw_input('Temporary path will be removed: ' + TMP_PATH
                      + ' press enter or type ABORT')
  if (len(tmpPath) == 0):
    shutil.rmtree(TMP_PATH)


def main():
  global TMP_PATH
  TMP_PATH = subprocess.check_output(["pwd"]).strip() + DEFAULT_TMP_PATH
  print "Temporary path: " + TMP_PATH
  tmpPath = raw_input('Press enter or provide a different path: ')
  if (len(tmpPath) > 0):
    TMP_PATH = tmpPath

  setUp()
  gerritUsers = getRandomUsers(100)

  groupNames = createGerritGroups()
  for idx, u in enumerate(gerritUsers):
    u['groups'].append(groupNames[4 * idx / len(gerritUsers)])
    if (idx % 5 == 0):
      # Also add to security group
      u['groups'].append(groupNames[4])

  generateSSHKeys(gerritUsers)
  createGerritUsers(gerritUsers)

  projectNames = createGerritProjects(groupNames)

  for idx, u in enumerate(gerritUsers):
    createChange(u, projectNames[4 * idx / len(gerritUsers)])

  cleanUp()


try:
  main()
except:
  print "Cleaning up after exceptions"
  cleanUp()
  print "Unexpected error:", sys.exc_info()[0]
  raise
