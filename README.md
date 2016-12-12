# AngryProgrammers
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)

Julius and Richard's take on the [AI Birds](http://aibirds.org/) competition as part of the INF-PM-ANW module for their studies.

## Get it running for development
1. `gradle clean build uberjar`
2. Create your own config.properties file with the following content:

 ```
 db_user=aibirds
 db_pass=secret
 db_path=jdbc:sqlite:database.db
 ```
 
3. Execute it as usual

## Get it running for the competition or on a development server
1. `gradle clean build uberjar`
2. `~/start.sh 9042`

The last shell script starts a new tmux session, named after the port number the, and attaches the chrome window to a xpra session, using the same port number.

## License
Unless explicitly noted otherwise, the content of this package is released under the [GNU Affero General Public License version 3 (AGPLv3)](http://www.gnu.org/licenses/agpl.html)

[Why the GNU Affero GPL](http://www.gnu.org/licenses/why-affero-gpl.html)

Copyright © 2013,2014 for the Basic Game Playing Software by XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz, Sahan Abeyasinghe, Jim Keys, Andrew Wang, Peng Zhang. All rights reserved.

Copyright © 2016 for the rest by Richard Kwasnicki and [Julius Gonsior](https://gaenseri.ch/) 
