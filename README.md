# AngryProgrammers
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)

Julius and Richard's take on the [AI Birds](http://aibirds.org/) competition as part of the INF-PM-ANW module for their studies.

## Get it running for development with Intellij Idea
1. Install the IvyIDEA plugin
2. Let this plugin download all the Dependencies specified in ivy.xml
3. Create your own config.properties file:
 ```
 db_user=aibirds
 db_pass=secret
 db_path=jdbc:sqlite:database.db
 db_name=ai_test
 ```
4. Execute it as usual

## Get it running for the competition or on a development server (untested yet)
1. Install gradle
2. Run `gradle clean build uberjar` to build the jar file, then move it into main folder (automatization will follow) 
4. Execute `~/start.sh`

<!--
@todo: merge ant resolve and ant jar into ~/start.sh
-->

## License
Unless explicitly noted otherwise, the content of this package is released under the [GNU Affero General Public License version 3 (AGPLv3)](http://www.gnu.org/licenses/agpl.html)

[Why the GNU Affero GPL](http://www.gnu.org/licenses/why-affero-gpl.html)

Copyright © 2013,2014 for the Basic Game Playing Software by XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz, Sahan Abeyasinghe, Jim Keys, Andrew Wang, Peng Zhang. All rights reserved.

Copyright © 2016 for the rest by Richard Kwasnicki and [Julius Gonsior](https://gaenseri.ch/) 
