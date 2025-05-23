---
title: Getting started
date: 2022-09-20
slug: /en/introduction/getting-started
---


In this quick tutorial, you will learn how to:
- Write the basic configuration.
- Write a scenario.
- Run Wakamiti.
- Learn the basic workflow.

Please be aware that this tutorial assumes that you have:
- Some experience using a terminal.
- Some experience using a text editor.
- A basic understanding of `gherkin` syntax.

Before starting, you will need the following:
- Install and run [Docker](https://www.docker.com/get-started/).
- The source code of [this tutorial](javascript:downloadTutorial()).

Optionally:
- Install an IDE, such as [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [VS Code](https://code.visualstudio.com/). 
  It's not essential, but it will make scenario development much easier.

### 0. Start the sample application
Unzip the downloaded zip file containing the tutorial source code, open a terminal in that directory and launch the 
application with the following command:
```shell copy=true
docker compose up -d
```

### 1. Wakamiti configuration
Wakamiti configuration is created by using a `yaml` file that will be placed in the same directory where the tests are 
located (e.g., where the source code of the tutorial is located):
```diff
  tutorial
  ├── application-wakamiti.properties
  ├── docker-compose.yml
+ └── wakamiti.yaml
```

This is the basic configuration to be able to run tests:
```yml copy=true
wakamiti:
  resourceTypes:
    - gherkin
  launcher:
    modules:
      - mysql:mysql-connector-java:8.0.28
      - es.iti.wakamiti:rest-wakamiti-plugin
      - es.iti.wakamiti:db-wakamiti-plugin
      - es.iti.wakamiti:html-report-wakamiti-plugin
  htmlReport:
    title: Test
  rest:
    baseURL: http://host.docker.internal:9966/petclinic/api
  database:
    connection:
      url: jdbc:mysql://host.docker.internal:3309/petclinic?useUnicode=true
      username: root
      password: petclinic
      driver: com.mysql.cj.jdbc.Driver
```
> **NOTE** <br />
> Note that each plugin has its own configuration, which can be checked in [their respective sections](en/plugins).
> You can also check other options in [global configuration](en/wakamiti/architecture#global-configuration).


### 2. Scenario definition
When we do *Behaviour-Driven Development*, we specify what we want the software to do using concrete examples.
Scenarios are written before production code. They start their life as an executable specification. As the 
production code emerges, scenarios take on a role as living documentation and automated tests.

A scenario belongs to a specific software feature. Each feature can contain many scenarios, and are defined in `.feature` 
files that must be in our working directory (or subdirectory).

A specific example in this tutorial would be to consult a pet owner.

Create an empty file named `example.feature` with the following content:
```gherkin copy=true
Feature: Get the pets owners

  Scenario: An existing owner is consulted
    Given the REST service '/owners/{id}'
    And the path parameter 'id' with value '20'
    And the following user is inserted into the table owners:
      | ID  | FIRST_NAME | LAST_NAME      |
      | 20  | Pepe       | Perez Martínez |
    When the user is requested
    Then the response HTTP code is equal to 200
    And the response is:
      """json
      {
        "id": 20,
        "firstName": "Pepe",
        "lastName": "Perez Martínez"
      }
      """
```
The first line of this file starts with the keyword `Feature:` followed by a name. It is recommended to use a similar 
name to the file name.

The third line, `Scenario: An existing owner is queried`, is a scenario (i.e., a specific example that illustrates how 
the software should behave).

The rest of the lines starting with `Given`, `When`, `Then`, `And` are the steps of our scenario, and are what Wakamiti 
will execute.

[See more](https://cucumber.io/docs/gherkin/) in detail the `gherkin` syntax.

### 3. Run Wakamiti
Tests are executed with the terminal, from the working directory (the one containing the Wakamiti features and the 
`.feature` file we have created), with the following command:

* Windows:
```Shell copy=true
docker run --rm -v "%cd%:/wakamiti" wakamiti/wakamiti
```
* Linux:
```Shell copy=true
docker run --rm -v "$(pwd):/wakamiti" --add-host=host.docker.internal:host-gateway wakamiti/wakamiti
```
With this command, the latest version of Wakamiti will be downloaded. To work with a specific version, it should be
specified in the Docker command as follows: `wakamiti/wakamiti:version`. The available versions can be checked in the 
[Wakamiti dockerhub](https://hub.docker.com/r/wakamiti/wakamiti/tags) repository.


### 4.Reports
Once the tests are executed, the results are generated in two formats: `wakamiti.json` and `wakamiti.html`.

The current states available in Wakamiti are:

- <span style="color:#5fc95f">**PASSED**</span>: test case is correct, the same result as expected is received from the 
  system.
- <span style="color:#4fc3f7">**NOT IMPLEMENTED**</span>: test case exists, but its steps are not defined.
- <span style="color:#9e9e9e">**SKIPPED**</span>: test case has not been executed.
- <span style="color:#ffc107">**UNDEFINED**</span>: there is no such step in Wakamiti.
- <span style="color:#ff7b7e">**FAILED**</span>: there is a check error, it does not match what is expected from what the 
  system returns.
- <span style="color:#ff0000">**ERROR**</span>: there is an unexpected error in the system (connection error, database error, 
  time out error...).

### Here's the demo!

![demo](asciinema:/wakamiti_en.cast?poster=npt:2:25&cols=86&fit=width)