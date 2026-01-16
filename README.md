# DLSync

<img src="https://github.com/user-attachments/assets/24da3d86-f58e-4b55-8d9e-b3194117a566" height="300" title="logo" alt="logo">

[![unit-test](https://github.com/Snowflake-Labs/dlsync/actions/workflows/test.yml/badge.svg)](https://github.com/Snowflake-Labs/dlsync/actions/workflows/test.yml)
[![release](https://img.shields.io/github/release/Snowflake-Labs/dlsync.svg?style=flat)](https://github.com/Snowflake-Labs/dlsync/releases/latest)
---
## Overview
DLSync is a database change management tool designed to streamline the development and deployment of Snowflake changes. 
By associating each database object (view, table, UDF, etc.) with a corresponding SQL script file, DLSync tracks every modification, ensuring efficient and accurate updates.
Each script can also have a corresponding test script that can be used to write unit tests for the database object. 
DLSync keeps track of what changes have been deployed to the database by using a hash. Hence, DLSync is capable of identifying what scripts have changed in the current deployment.
Because of this, DLSync only deploys changed scripts to database objects.
DLSync also understands interdependencies between different scripts, thus applying these changes according to their dependencies.

## Table of Contents

1. [Overview](#overview)
1. [Key Features](#key-features)
1. [Project structure](#project-structure)
   1. [Script content](#script-content)
      1. [Declarative Script](#1-declarative-script)
      1. [Migration Script](#2-migration-script)
      1. [Test Script](#3-test-script)
   1. [Configurations](#configurations)
      1. [Parameter profile](#parameter-profile)
      1. [config file](#config-file)
   1. [How to use this tool](#how-to-use-this-tool)
      1. [Deploy](#deploy)
      1. [Test](#test)
      1. [Rollback](#rollback)
      1. [Verify](#verify)
      1. [Create script](#create-script)
1. [Required Privileges](#required-privileges)
   1. [Warehouse & Database Access](#warehouse--database-access)
   1. [DLSync Schema (Metadata Tracking)](#dlsync-schema-metadata-tracking)
   1. [Database-Level Objects](#database-level-objects)
   1. [Account-Level Objects](#account-level-objects)
1. [DLSync Metadata Tables](#dlsync-metadata-tables)
   1. [dl_sync_script_history](#dl_sync_script_history)
   1. [dl_sync_change_sync](#dl_sync_change_sync)
   1. [dl_sync_script_event](#dl_sync_script_event)
1. [Example scripts](#example-scripts)

## Key Features 
- Hybrid Change Management: It combines declarative and migration based change management to manage database changes
- Account-Level and Database-Level Objects: DLSync supports both account-level objects (databases, schemas, roles, warehouses, security integrations, etc.) and database-level objects (views, tables, functions, procedures, etc.)
- Unique Script per Object: Each object has a corresponding unique script file where you can define changes to the object
- Unit Testing: It supports unit testing where you can write test scripts for each database object
- Change Detection: It can detect changes between the previous deployment and the current script state
- Dependency Resolution: It can reorder scripts based on their dependencies before deploying to the database
- Parametrization: It supports parametrization of scripts where you can define variables that change between different database instances. Each instance is associated with a parameter config file that lists the variables and their values for that instance
- Rollback: It supports rollback to the previous deployment state. Rollback is simple and intuitive. Only one needs to rollback the git repository of the scripts and trigger the rollback module
- Verification: It supports a verify module where each database object is checked against the current script to verify deployment or track out-of-sync database changes
- Script Creation: It supports creating script files for existing database objects

## Project structure
To use this tool, first create your script root directory.
This directory will contain all scripts and configurations.
Inside this directory, create a directory structure like this:
```
/script-root                                        # Root directory for the scripts
├── /main                                           # Main scripts for deployment 
│   ├── /ACCOUNT                                    # Account-level objects (databases, schemas, roles, warehouses)
│   │   ├── /DATABASES                              # Database definitions
│   │   │   ├── database_name_1.sql                 # Database creation script
│   │   │   ├── database_name_2.sql                 # Database creation script
│   │   ├── /SCHEMAS                                # Schema definitions
│   │   │   ├── database_name_1.schema_name_1.sql   # Schema creation script (database.schema format)
│   │   │   ├── database_name_2.schema_name_2.sql   # Schema creation script (database.schema format)
│   │   ├── /ROLES                                  # Role definitions
│   │   │   ├── role_name_1.sql                     # Role creation script
│   │   │   ├── role_name_2.sql                     # Role creation script
│   │   ├── /WAREHOUSES                             # Warehouse definitions
│   │   │   ├── warehouse_name.sql                  # Warehouse creation script
│   ├── /database_name_1                            # Database name 
│   │   ├── /schema_name_1                          # database Schema name
│   │   │   ├── /[object_type]_1                    # Database Object type like (VIEWS, FUNCTIONS, TABLES ...)
│   │   │   │   ├── object_name_1.sql               # The database object name(table name, view name, function name ...)
│   │   │   │   ├── object_name_2.sql               # The database object name(table name, view name, function name ...)
│   │   │   ├── /[object_type]_2                    # Database Object type like (VIEWS, FUNCTIONS, TABLES ...)
│   │   │   │   ├── object_name_3.sql               # The database object name(table name, view name, function name ...)
│   │   │   │   ├── object_name_4.sql               # The database object name(table name, view name, function name ...)
│   │   ├── /schema_name_2                          # database Schema name
│   │   │   ├── /[object_type]_1                    # Database Object type like (VIEWS, FUNCTIONS, TABLES ...)
│   │   │   │   ├── object_name_5.sql               # The database object name(table name, view name, function name ...)
│   │   │   │   ├── object_name_6.sql               # The database object name(table name, view name, function name ...)
│   │   │   ├── /[object_type]_2                    # Database Object type like (VIEWS, FUNCTIONS, TABLES ...)
│   │   │   │   ├── object_name_7.sql               # The database object name(table name, view name, function name ...)
│   │   │   │   ├── object_name_8.sql               # The database object name(table name, view name, function name ...)
├── /test                                           # SQL unit test scripts
│   ├── /database_name_1                            
│   │   ├── /schema_name_1                          
│   │   │   ├── /[object_type]_1                    
│   │   │   │   ├── object_name_1_test.sql          # unit test file for object object_name_1_test
│   │   │   │   ├── object_name_2_test.sql          # unit test file for object object_name_2_test
│   │   ├── /schema_name_2                          
│   │   │   ├── /[object_type]_1                    
│   │   │   │   ├── object_name_5_test.sql          # unit test file for object object_name_5_test
│   │   │   │   ├── object_name_6_test.sql          # unit test file for object object_name_6_test
├── config.yml                                      # configuration file
├── parameter-[profile-1].properties                # parameter property file  
├── parameter-[profile-2].properties                # parameter property file
└── parameter-[profile-3].properties                # parameter property file
```

Where 
- **ACCOUNT:** is a special directory for account-level objects that are not scoped to a specific database or schema. This includes:
  - **DATABASES:** Database creation and modification scripts
  - **SCHEMAS:** Schema creation and modification scripts (filenames should be in format `database_name.schema_name.sql`)
  - **ROLES:** Role creation and management scripts
  - **WAREHOUSES:** Warehouse creation and configuration scripts
  - Other account-level objects (users, resource monitors, integrations, etc.)
- **database_name_*:** is the database name of your project, 
- **schema_name_*:** are schemas inside the database, 
- **object_type:** is type of the object only 1 of the following (VIEWS, FUNCTIONS, PROCEDURES, FILE_FORMATS, TABLES, SEQUENCES, STAGES, STREAMS, TASKS, STREAMLITS, PIPES, ALERTS, DYNAMIC_TABLES, MASKING_POLICIES),
- **object_name_*.sql:** are individual database object scripts.
- **config.yml:** is a configuration file used to configure DLSync behavior.
- **parameter-[profile-*].properties:** is parameter to value map file. This is going to be used by corresponding individual instances of your database.
This property files will help you parametrize changing parameters and their value. For each deployment instance of your database(project) you should create a separate parameter profile property.
These property files should have names in the above format by replacing "format" by your deployment instance name.
where profile is the instance name of your database. you will provide the profile name in environment variable while running this tool.

### Script content
Each object will have a single SQL file to track the changes applied to that object. The SQL file is named using the object's name. 
For example, if you have a view named `SAMPLE_VIEW` in schema `MY_SCHEMA` in database `MY_DATABASE`, then the script file should be named `SAMPLE_VIEW.SQL` and should be placed in the directory `[scripts_root]/main/MY_DATABASE/MY_SCHEMA/VIEWS/SAMPLE_VIEW.SQL`.

**Account-level objects** (such as databases, roles, warehouses, security integrations, etc.) are placed directly in the `/ACCOUNT` directory. For example, a database creation script should be placed in `[scripts_root]/main/ACCOUNT/DATABASES/DATABASE_NAME.SQL` and a role creation script in `[scripts_root]/main/ACCOUNT/ROLES/ROLE_NAME.SQL`.

> **Note:** Schema object filenames should include the database name in the format `database_name.schema_name.sql`. For example, a schema named `MY_SCHEMA` in database `MY_DATABASE` should be placed in `[scripts_root]/main/ACCOUNT/SCHEMAS/MY_DATABASE.MY_SCHEMA.SQL`. This ensures the schema name is properly extracted and associated with its parent database.

The structure and content of the scripts differ based on the type of script. This tool categorizes scripts into 2 types: Declarative scripts and Migration scripts.
#### 1. Declarative Script
This type of script is used for object types of VIEWS, FUNCTIONS, PROCEDURES, FILE_FORMATS, PIPES, MASKING_POLICIES, STREAMLITS, RESOURCE_MONITORS, NETWORK_POLICIES, SESSION_POLICIES, PASSWORD_POLICIES, AUTHENTICATION_POLICIES, API_INTEGRATIONS, NOTIFICATION_INTEGRATIONS, SECURITY_INTEGRATIONS, STORAGE_INTEGRATIONS, and WAREHOUSES.
In this type of script, you define the current state (desired state) of the object.
When a change is made to the script, DLSync replaces the current object with the updated definition. 
These types of scripts must always have a `create or replace` statement. Every time you make a change to the script, DLSync will replace the object with the new definition.

The SQL file should be named with the database object name. 
The Declarative script file should adhere to the following rules:
1. The file name should match the database object name referenced by the `create or replace` statement.
2. The file should contain only one SQL DDL script that creates and replaces the specified object.
3. For database-level objects, the create script should refer to the object with its fully qualified name (database.schema.object_name)
4. For account-level objects, use the fully qualified name without database scope (e.g., just the object name for databases, roles, and warehouses)

> **Note:** If the object name in the SQL script is parametrized (e.g., `SAMPLE_${TENANT}_VIEW`), the file name should also use the same parameter syntax (e.g., `SAMPLE_${TENANT}_VIEW.SQL`). This allows DLSync to correctly match the physical file with the parametrized object name during deployment across different environments.

eg: view named SAMPLE_VIEW can have the following SQL statement in the `SAMPLE_VIEW.SQL` file.
```
create or replace view ${MY_DB}.${MY_SCHEMA}.SAMPLE_VIEW as select * from ${MY_DB}.${MY_SECOND_SCHEMA}.MY_TABLE;
```

eg: account-level database named MY_DATABASE can have the following SQL statement in the `MY_DATABASE.SQL` file.
```
create or replace database MY_DATABASE;
```
#### 2. Migration Script
This type of script is used for object types of TABLES, SEQUENCES, STAGES, STREAMS, TASKS, ALERTS, DYNAMIC_TABLES and account-level objects like DATABASES, SCHEMAS, and ROLES.
Here the script is treated as a migration that will be applied to the object sequentially based on the version number. 
This type of script contains 1 or more migration versions. Each migration version contains a version number, author (optional), content (DDL or DML SQL statement), rollback statement (optional), and verify statement (optional).
Each migration version is immutable—once the version is deployed, you cannot change the code of this version. You can only add new versions.

> **Note:** If the object name in the SQL script is parametrized (e.g., `${MY_SCHEMA}`), the file name should also use the same parameter syntax (e.g., `${MY_SCHEMA}.SQL`). This allows DLSync to correctly match the physical file with the parametrized object name during deployment across different environments.

eg: for the table named `SAMPLE_TABLE` you can have the following SQL statement in the `SAMPLE_TABLE.SQL` file.:
```
---version: 0, author: user1
create or replace table ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE(id varchar, my_column varchar);
---rollback: drop table if exists ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE;
---verify: select * from ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE limit 1;

---version: 1, author: user1
insert into ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE values('1', 'value');
---rollback: delete from ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE where id = '1';
---verify: select 1/count(*) from ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE where id = '1';

---version: 2, author: user2
alter table ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE add column my_new_column varchar;
---rollback: alter table ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE drop column my_new_column;
---verify: select my_new_column from ${MY_DB}.{MY_SCHEMA}.SAMPLE_TABLE limit 1;
```

eg: for the account-level role named `ANALYST` you can have the following SQL statement in the `ANALYST.SQL` file.:
```
---version: 0, author: admin
create or replace role ANALYST;
---rollback: drop role if exists ANALYST;

---version: 1, author: admin
grant role ANALYST to role ACCOUNTADMIN;
---rollback: revoke role ANALYST from role ACCOUNTADMIN;

---version: 2, author: admin
grant create database on account to role ANALYST;
---rollback: revoke create database on account from role ANALYST;
```

The migration script will have the following format:
```
---version: VERSION_NUMBER, author: NAME
CONTENT;
---rollback: ROLLBACK_CONTENT;
---verify: VERIFY_CONTENT;
```
where 
- ```VERSION_NUMBER``` is the version number of the migration script
- ```NAME``` is the author of the script,
- ```CONTENT``` is the DDL or DML script that changes the object, 
- ```ROLLBACK_CONTENT``` is the script that rolls back the changes made by the migration script 
- ```VERIFY_CONTENT``` is the script that verifies the changes made by the migration script.

The migration script should adhere to the following rules:
1. Each change to a database object should be wrapped in a migration format specified above.
2. Each migration version should contain a migration header (version and author) and the content of the migration (single DDL or DML script), rollback (optional), and verify (optional).
3. The migration header should start on a new line with three hyphens (---) and can contain only version and author.
4. The version should be a unique number per script file and should be in incremental order. It is used to order the scripts' migration sequence for that object.
5. The author is optional alphanumeric characters used for informational purposes only to track who added the changes.
6. The content of the change (migration) should be specified after the migration header on a new line. It can span multiple lines.
7. Content should always be terminated by a semicolon (`;`). 
8. If rollback is specified, it should start on a new line with `---rollback: `. The rollback script should be on a single line and must be terminated with a semicolon (;).
9. If verify is specified, it should start on a new line with `---verify:`. The verify script should be on a single line and must be terminated with a semicolon (;).
10. Migration versions are immutable. Once a version is deployed, it cannot be changed. Only new versions can be added or existing versions can be rolled back.

#### 3. Test Script
This type of script is used to write unit tests for your scripts. You can write and execute unit tests for your database objects using this tool.
Unit testing in DLSync allows you to validate the correctness of your database objects, such as views and UDFs, by writing test scripts. 
Writing unit tests follows a 3-step process:
- Mock your table dependencies using CTE by putting the table name as the CTE name.
- Add expected data in the CTE by putting `EXPECTED_DATA` as the CTE name.
- Add the query to refer to the database object with a select statement.
For example, if you have a view named `SAMPLE_VIEW`  with the following content:
```
create or replace view ${MY_DB}.{MY_SCHEMA}.SAMPLE_VIEW as 
select tb1.id, tb1.my_column || '->' || tb2.my_column as my_new_column from ${MY_DB}.{MY_SECOND_SCHEMA}.MY_TABLE_1 tb_1
join ${MY_DB}.{MY_SECOND_SCHEMA}.MY_TABLE_2 tb2 
    on tb1.id = tb2.id;
```

Then your test script can be placed on the file `SAMPLE_VIEW_TEST.SQL` and the content of the test script can be:
```
with MY_TABLE_1 as (
     SELECT * FROM VALUES
        (1, 'old_value1', 5),
        (2, 'old_value2', 20)
    AS T(ID, MY_COLUMN)
),
MY_TABLE_2 as (
    select '1' as id, 'new_value1' as my_column
),
expected_data as (
    select '1' as id, 'old_value1->new_value1' as my_new_column
)
select * from ${MY_DB}.{MY_SCHEMA}.SAMPLE_VIEW;
```
Then dlsync will generate a query with the following content to validate the test:
```
with MY_TABLE_1 as (
     SELECT * FROM VALUES
        (1, 'old_value1', 5),
        (2, 'old_value2', 20)
    AS T(ID, MY_COLUMN)
),
MY_TABLE_2 as (
    select '1' as id, 'new_value1' as my_column
),
expected_data as (
    select '1' as id, 'old_value1->new_value1' as my_new_column
),
ACTUAL_DATA as (
select tb1.id, tb1.my_column || '->' || tb2.my_column as my_new_column from ${MY_DB}.{MY_SECOND_SCHEMA}.MY_TABLE_1 tb_1
join ${MY_DB}.{MY_SECOND_SCHEMA}.MY_TABLE_2 tb2 
    on tb1.id = tb2.id
),
assertion as (
	select count(1) as result, 'rows missing from actual data' as message from (
		select * from actual_data
		except 
		select * from expected_data
	) having result > 0
	union 
	select count(1) as result, 'rows missing from expected data' as message from ( 
		select * from expected_data
		except
		select * from actual_data
	) having result > 0
)
select * from assertion;
```
If this query returns any rows, then the test will fail. Otherwise, the test will pass.
Currently, this tool supports unit testing for object types of Views and UDFs.
For UDFs please refer the example scripts provided in the `example_scripts` directory.

The test script should adhere to the following rules:
1. The file name should match database object name with `_TEST` suffix.
2. The file should be placed test directory with the same path as the corresponding object script.
3. The test script should be single query with CTE expressions to mock the dependencies and expected data.
4. Use the table name only as CTE name to mock data from that table.
5. You can use `MOCK_DATA` as CTE name to define input data for UDFs.
6. Your expected data should be in a CTE named `EXPECTED_DATA`.
7. your expected data should have the same schema as the actual data returned by the database object.
8. At the end of the test script you should have a select statement to select the actual data from the database object.
### Configurations
#### Parameter profile
Parameter files help you define parameters that change between different database instances. This is helpful if you have variables that change between different instances (like dev, staging, and prod). 
Parameter files are defined per each instance. Parameter files are basically property files where you define parameters and their values. 
The parameter files should be placed in the root script directory and should be named in the following format:
``` 
parameter-[profile].property
```
where `[profile]` is the instance name of your database. you will provide the profile name in the command line option or environment variable while running this tool.
Eg. if you have a dev instance of your database, then you should create a parameter file named `parameter-dev.property` in the root script directory. And the content of this file can be:
```
MY_DB=my_database_dev
MY_SCHEMA=my_schema_dev
other_param=other_value
```
And you can use these parameters in your script files. The format for using parameters is:
```
${parameter_name}
```
where parameter_name is the name of parameter defined in the parameter-[profile].property file with its value.

For example,
```
create or replace view ${MY_DB}.${MY_SCHEMA}.my_view as select * from ${MY_DB}.${MY_SCHEMA}.my_table;
```
#### config file
The config file is used to configure the behavior of this tool. The config file should be named `config.yml` and placed in the root script directory. The config file should have the following format:
```
version: #version of the config file
configTables:  # List of configuration tables, only used for create script module
scriptExclusion: # List of script files to be excluded from deploy, verify, rollback and create script module
continueOnFailure: "true" # "true" or "false, controls the error disposition of the tool.
dependencyOverride: # List of additional dependencies for the scripts
  - script: # script file name to override the dependencies 
    dependencies: List of dependencies to override
connection:
    account: # snowflake account name
    user: # snowflake user name
    password: # snowflake password
    role: # snowflake role
    warehouse: # snowflake warehouse
    db: # snowflake database
    schema: # snowflake schema
    authenticator: # snowflake authenticator(optional)
    private_key_file: # snowflake p8 file (optional)
    private_key_pwd: # password for private key file (optional)
 ```
The `configTables` is used by create script module to add the data of the tables to the script file.
The `scriptExclusion` is used to exclude the script files from being processed by this tool. 
The `continueOnFailure` is used to control error disposition, "true" will fail deployment on first failure or "false" will try to deploy all items in dependency tree before failing.
The `dependencyOverride` is used to override the dependencies of the script files. This can be used to add additional dependencies to the script files.
The `connection` is used to configure the connection to snowflake account. 
**Warning: Please use the connection property for local development and experimenting. Since the config file is checked in to your git repo please avoid adding any connection information to your config file. You can provide the connection details in environment variables.**
### How to use this tool
In order to run the application, you need to provide the Snowflake connection parameters in environment variables. The following environment variables are required to run the application:
```
account=my_account  #account used for connection
db=database  #your database
schema=dl_sync  #your dl_sync schema. It will use this schema to store neccessary tables for this tool
user=user_name  #user name of the database
password=password #password for the connection (optional)
authenticator=externalbrowser #authenticator used for the connection (optional)
warehouse=my_warehouse #warehouse to be used by the connection
role=my_role    #role used by this tool
private_key_file=my_private_key_file.p8     # private key file used for the connection (optional)
private_key_pwd=my_private_key_password  # password for the private key file (optional)
JAVA_TOOL_OPTIONS ="-Dnet.snowflake.jdbc.enableBouncyCastle=true" # This must be set if using encrypted key-pair authentication
```

You also need to provide the script root directory and which profile to use. This can be provided in the command line argument or in the environment variable.
Providing in the command line argument will override the environment variable. 
You can provide command line options using the following:
```
dlsync deploy --script-root path/to/db_scripts --profile dev
```
or
``` 
dlsync deploy -s path/to/db_scripts -p dev
```
or you can provide in environment variable as:
```
script_root=path/to/db_scripts
profile=dev
```

There are 4 main modules (commands). Each module of the tool can be triggered from the command line argument.
#### Deploy
This module is used to deploy the changes to the database. It will deploy the changes to the database objects based on the script files.
First DLSync will identify the changed scripts based on the hash of the script file and the hash stored in the database(`dl_sync_script_history` table). For migration scripts each migration version will have it's hash stored in the script history. Thus only newly added versions will be picked up for the changed scripts. After identifying the changes, it will order the scripts based on their dependency. Then it will deploy the changes to the database objects sequentially.
The deploy module can be triggered using the following command:
```
dlsync deploy -s path/to/db_scripts -p dev
```
If you have already deployed the changes manually or though other tools, you can mark the scripts as deployed without deploying the changes. This will only add the hashes to the script history table(`dl_sync_script_history`) without affecting the current database state. This can be very helpful while migrating from other tools. 
You can use the following command to mark the scripts as deployed without deploying the changes:
```
dlsync deploy --only-hashes -s path/to/db_scripts -p dev
```
or
```
dlsync deploy -o -s path/to/db_scripts -p dev
```
#### Test
This module is used to run the unit tests for the database objects. It will run the test scripts for the database objects based on the script files.
The test module can be triggered using the following command:
```
dlsync test -s path/to/db_scripts -p dev
```

#### Rollback
This module is used to rollback changes to the previous deployment. It will rollback the changes to the database objects based on the script files. This should be triggered after you have rolled back the git repository of the script files. 
The rollback works first by identifying the changes between the current deployment and the previous deployment. For declarative scripts (views, udf, stored procedures and file formats) it will replace them with the current script(i.e previous version as you have already made git rollback). 
For migration scripts it will identify the versions need to be rolled back by checking the missing versions of current scripts but have been deployed previously. Then it will use the rollback script specified in the migration version to rollback the changes. 
This will be stored in the script history table.
To rollback the changes use the following command:
```
dlsync rollback -script-root path/to/db_scripts -profile dev

```
#### Verify
This module is used to verify the database scripts are in sync with the current database objects. For declarative scripts it will compare the content of script with the DDL of the database object. 
For Migration scripts it uses the verify script provided in the migration version. if the verify script throws error, then it will mark the migration version as out of sync. Since latest migration versions can change previous versions results, it only checks the latest migration version of each script for verification.
To verify the changes use the following command:
```
dlsync verify --script-root path/to/db_scripts --profile qa
```
#### Create script
This module is used to create script files from database. This can be used to create script files for the existing database objects. This might be helpful when you are migrating from other tools to DLSync. To achieve it first identifies the schemas inside the current database. Then for each schema retrieves the ddl of each object. Then based on the parameter profile provided it will replace the static values with the parameter keys. Then it will create the script file for each object. 
If you have configuration tables where you want the data also to be included in the script file, you can provide the list of table names in the config file. 
```
dlsync create_script --script-root path/to/db_scripts --profile uat
```
You can also provide the list of schemas you want to create the script files for. If you don't provide any schemas, it will create the script files for all schemas. 
```
dlsync create_script --script-root path/to/db_scripts --profile uat --target-schemas schema1,schema2
```
## Required Privileges

In order to successfully run DLSync, the Snowflake role provided in the connection configuration must have the appropriate privileges. The required privileges depend on the types of objects you are deploying.

### Warehouse & Database Access
- `GRANT USAGE ON WAREHOUSE <warehouse_name> TO ROLE MY_ROLE`: The role must have usage privileges on the specified warehouse to execute SQL statements.
- `GRANT USAGE ON DATABASE <database_name> TO ROLE MY_ROLE`: The role must have usage privileges on the specified database to access its objects.
- `GRANT USAGE ON SCHEMA <database_name.schema_name> TO ROLE MY_ROLE`: The role must have usage privileges on the specified schema to access its objects.

### DLSync Schema (Metadata Tracking)
The role must have privileges to create and manage DLSync tracking tables in the designated schema:
- `GRANT CREATE TABLE ON SCHEMA <schema_name> TO ROLE MY_ROLE` **CREATE TABLE** on the DLSync schema
> **Note:** the database and schema specified in the connection will be used to store the DLSync tracking tables.

### Database-Level Objects
For managing database-level objects (VIEWS, FUNCTIONS, PROCEDURES, TABLES, SEQUENCES, STAGES, STREAMS, TASKS, ALERTS, DYNAMIC_TABLES, FILE_FORMATS, PIPES, MASKING_POLICIES), the role must have:
- **USAGE** on the target schema
- **CREATE** privileges for the specific object types being deployed (CREATE VIEW, CREATE FUNCTION, CREATE TABLE, etc.)
- **ALTER** privileges on existing objects in the schema
- **SELECT** privileges on tables (for queries and verification)

### Account-Level Objects
For managing account-level objects, the role must have:
- **CREATE DATABASE** (for DATABASES)
- **CREATE SCHEMA** on the target database (for SCHEMAS)
- **CREATE ROLE** and **MANAGE GRANTS** (for ROLES)
- **CREATE WAREHOUSE** (for WAREHOUSES)
- **CREATE INTEGRATION** (for SECURITY_INTEGRATIONS, STORAGE_INTEGRATIONS, NOTIFICATION_INTEGRATIONS, API_INTEGRATIONS)
- **CREATE RESOURCE MONITOR** (for RESOURCE_MONITORS)
- **CREATE NETWORK POLICY** (for NETWORK_POLICIES)
- **CREATE SESSION POLICY** (for SESSION_POLICIES)
- **CREATE PASSWORD POLICY** (for PASSWORD_POLICIES)
- **CREATE AUTHENTICATION POLICY** (for AUTHENTICATION_POLICIES)

## DLSync Metadata Tables
DLSync stores script metadata, deployment history, and logs in the database.
DLSync will depend on these tables to track the changes and deployment history. If these tables are missing from the schema and database provided in the connection parameter, then DLSync will create these tables. 
Please make sure the role provided in the connection has the necessary privileges to create tables in the schema. 

**_N.B: Since DLSync uses these tables to track the changes, it is recommended not to delete or change these tables. It is also important not to change the schema of the connection. If DLSync is not able to find these tables in the schema, it will create them and assume it is running for the first time._**

### dl_sync_script_history
This table stores the metadata for script files. It contains the following columns:
```
script_id: # for declarative script the script name, for migration script script name plus the version number
object_name: the object name of the script
object_type: the type of the object (VIEWS, FUNCTIONS, PROCEDURES, FILE_FORMATS, TABLES, SEQUENCES, STAGES, STREAMS, TASKS)
rollback_script: the rollback script for the migration version
script_hash: the hash of the script file
deployed_hash: the hash of the script file that has been deployed
change_sync_id: the id of the change sync
created_by: the db user who added this change
created_ts: the timestamp when was this change added
updated_by: the db user who updated this change
updated_ts: the timestamp when was this change updated

```
### dl_sync_change_sync
This table stores the deployment history of the scripts. It contains the following columns:
```
id: the id of the change sync
change_type: the type of the change (DEPLOY, ROLLBACK, VERIFY)
status: the status of the change (SUCCESS, FAILED)
log: the log of the change
change_count: the number of changes in this sync
start_time: the start time of the change
end_time: the end time of the change
```
### dl_sync_script_event
This table stores the logs of each script activity. It contains the following columns:
```
id: the id of the script event
script_id: the id of the script
object_name: the object name of the script
script_hash: the hash of the script
status: the status of the script (SUCCESS, FAILED)
log: the log of the script
changeSyncId: the id of the change sync
created_by: the db user who added this change
created_ts: the timestamp when was this change added
```
## Example scripts
To explore the tool, you can use the example scripts provided in the `example_scripts` directory.
