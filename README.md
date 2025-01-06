# DLSync

<img src="https://github.com/user-attachments/assets/24da3d86-f58e-4b55-8d9e-b3194117a566" height="300" title="logo" alt="logo">

---

DLSync is a database change management that deploys database changes to our database. 
Each object(view, table, udf ...) in our database will 
have a corresponding SQL script file where every change to this object is tracked in this file only. DLSync keeps track of what changes have been deployed to database 
by using hash. Hence DLSync is capable of identifying what scripts have changed in the current deployment.
Using this DLSync only deploys changed script to database objects.
DLSync also understands interdependency between different scripts, thus applies these changes
according their dependency.
Based on how we define the changes to database objects, DLSync divides database object scripts to 2 types, State and migration scripts.
## Key Features 
- It combines state based and migration based change management to manage database changes
- Each object will have it's corresponding unique Script file where we can define the change to the object
- It can detect change between previous deployment and current script state.
- It can reorder scripts based on their dependency before deploying to database.
- It supports parametrization of scripts where we can define variables that change between different database instances.
- It supports parameter config file where each parameter config file corresponds to an instance 
- It supports rollback to previous deployment state. 
- Rollback is very simple and intuitive. Only one needs to rollback git repository of the script and triggering rollback module.
- It supports verify module where each database object is checked with current script to check for deployment verification or tracking out of sync database changes.
- It supports create script where we can create script file for each database objects.

## Project structure
To use this tool first create your script root directory.
This directory will contain all scripts and configurations.
Inside this directory create a directory structure like:
```
/script-root                                        # Root directory for the scripts
├── /main                                           # Main scripts for deployment 
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
├── /tests                                          # SQL unit test scripts
├── config.yml                                      # configuration file
├── parameter-[profile-1].properties                # parameter property file  
├── parameter-[profile-2].properties                # parameter property file
└── parameter-[profile-3].properties                # parameter property file
```

Where 
- **database_name_*:** is the database name of your project, 
- **schema_name_*:** are schemas inside the database, 
- **object_type:** is type of the object only 1 of the following (VIEWS, FUNCTIONS, PROCEDURES, FILE_FORMATS, TABLES, SEQUENCES, STAGES, STREAMS, TASKS)
- **object_name_*.sql:** are individual database object scripts.
- **config.yml:** is a configuration file used to configure DLSync behavior.
- **parameter-[profile-*].properties:** is parameter to value map file. This is going to be used by corresponding individual instances of your database.
This property files will help you parametrize changing parameters and their value. For each deployment instance of your database(project) you should create a separate parameter profile property.
These property files should have names in the above format by replacing "format" by your deployment instance name.
where profile is the instance name of your database. you will provide the profile name in environment variable while running this tool.

### Script content
Each object will have a single SQL to track the changes applied to the given object. The SQL file is named using the object's name. 
For example if you have a view named `SAMPLE_VIEW` in schema `MY_SCHEMA` in database `MY_DATABASE`, then the script file should be named `SAMPLE_VIEW.SQL` and should be placed in the directory `[scripts_root]/main/MY_DATABASE/MY_SCHEMA/VIEWS/SAMPLE_VIEW.SQL`.
The structure and content of the scripts will defer based on the type of script. This tool categorizes script in to 2 types named State script and Migration script.
#### 1. State Script
This type of script is used for object types of Views, UDF, Stored Procedure and File formats.
In this type of script you define the current state(desired state) of the object.
When a change is made to the script, DLSync replaces the current object with the updated definition. 
These types of scripts must always have `create or replace` statement. Every time you make a change to the script DLSync will replace the object with the new definition.

The sql file should be named with the database object name. 
The State script file should adhere to the following rules
1. The file name should match database object name referenced by the `create or replace` statement.
2. The file should contain only one SQL DDL script that creates and replaces the specified object.
3. The create script should refer the object with its full qualified name (database.schema.object_name)

eg: view named SAMPLE_VIEW can have the following SQL statement in the `SAMPLE_VIEW.SQL` file.
```
create or replace view ${MY_DB}.{MY_SCHEMA}.SAMPLE_VIEW as select * from ${MY_DB}.{MY_SECOND_SCHEMA}.MY_TABLE;
```
#### 2. Migration Script
This type of script is used for object types of TABLES, SEQUENCES, STAGES, STREAMS and TASKS.
Here the script is treated as migration that will be applied to the object sequentially based on the version number. 
This type of script contains 1 or more migration versions. One migration versions contains version number, author(optional), content (DDL or DML SQL statement) , rollback statement(optional) and verify statement(optional).
Each migration version is immutable i.e Once the version is deployed you can not change the code of this version. Only you can add new versions.

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
1. Each change to database object should be wrapped in a migration format specified above.
2. Each migration version should contain migration header (version and author) and the content of the migration(single DDL or DML script), rollback(optional) and verify (optional).
3. migration header should start in a new line with three hyphens(---) and can contain only version and author.
4. Version should be unique number per each script file and should be in incremental order. And it is used to order the scripts migration sequence for that object.
5. author is optional alphanumeric characters used for informational purpose only to track who added the changes.
6. Content of the change (migration) should be specified after migration header in a new line. And it can span multiple lines.
7. Content should always be terminated by semi-colon (`;`). 
8. Rollback if specified should start in a new line with `---rollback: `. The rollback script should be on a single line and must be terminated with semi-colon (;);
9. Verify if specified should start in a new line with `---verify:`. The verify script should be on a single line and must be terminated with semi-colon (;);
10. Migration versions are immutable. Once a version is deployed, it cannot be changed. Only new versions can be added or existing versions can be rolled back.

### Configurations
#### Parameter profile
Parameter files help you define parameters that change between different database instances. This is helpful if you have variables that change between different instances (like dev, staging and prod). 
Parameter files are defined per each instance. Parameter file are basically property files where you define parameter and their values. 
the parameter files should be placed in the root script directory and should be named in the following format:
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
dependencyOverride: # List of additional dependencies for the scripts
  - script: # script file name to override the dependencies 
    dependencies: List of dependencies to override
 ```
The `configTables` is used by create script module to add the data of the tables to the script file.
The `scriptExclusion` is used to exclude the script files from being processed by this tool. 
The `dependencyOverride` is used to override the dependencies of the script files. This can be used to add additional dependencies to the script files.
### How to use this tool
In order to run the application you need to provide the snowflake connection parameters in environment variables. The following environment variables are required to run the application:
```
account=my_account  #account used for connection
db=database  #your database
schema=dl_sync  #your dl_sync schema. It will use this schema to store neccessary tables for this tool
user=user_name  #user name of the database
password=password #password for the connection (optional)
authenticator=externalbrowser #authenticator used for the connection (optional)
warehouse=my_warehouse #warehouse to be used by the connection
role=my_role    #role used by this tool
```

You also need to provide the script root directory and which profile to use. This can be provided in the command line argument or in the environment variable.
Providing in the command line argument will override the environment variable. 
you can provide command line option using the following:
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
If you have already deployed the changes manually or though other tools, you can can mark the scripts as deployed without deploying the changes. This will only add the hashes to the script history table(`dl_sync_script_history`) without affecting the current database state. This can be very helpful while migrating from other tools. 
You can use the following command to mark the scripts as deployed without deploying the changes:
```
dlsync deploy --only-hashes -s path/to/db_scripts -p dev
```
or
```
dlsync deploy -o -s path/to/db_scripts -p dev
```

#### Rollback
This module is used to rollback changes to the previous deployment. It will rollback the changes to the database objects based on the script files. This should be triggered after you have rolled back the git repository of the script files. 
The rollback works first by identifying the changes between the current deployment and the previous deployment. For state scripts (views, udf, stored procedures and file formats) it will replace them with the current script(i.e previous version as you have already made git rollback). 
For migration scripts it will identify the versions need to be rolled back by checking the missing versions of current scripts but have been deployed previously. Then it will use the rollback script specified in the migration version to rollback the changes. 
This will be stored in the script history table.
To rollback the changes use the following command:
```
dlsync rollback -script-root path/to/db_scripts -profile dev

```
#### Verify
This module is used to verify the database scripts are in sync with the current database objects. For state scripts it will compare the content of script with the DDL of the database object. 
For Migration scripts it uses the verify script provided in the migration version. if the verify script throws error, then it will mark the migration version as out of sync. Since latest migration versions can change previous versions results, it only checks the latest migration version of each script for verification.
To verify the changes use the following command:
```
dlsync verify --script-root path/to/db_scripts --profile qa
```
#### Create script
This module is used to create script files for each database object. This can be used to create script files for the existing database objects. This might be helpful when you are migrating from other tools to DLSync. To achieve it first identifies the schemas inside the current database. Then for each schema retrieves the ddl of each object. Then based on the parameter profile provided it will replace the static values with the parameter keys. Then it will create the script file for each object. 
If you have configuration tables where you want the data also to be included in the script file, you can provide the list of table names in the config file. 
```
dlsync create_script --script-root path/to/db_scripts --profile uat
```

## Tables used by this tool
DLSync stores script meta data, deployment history and logs in the database.
DLSync will depend on these tables to track the changes and deployment history. If these tables are missing from the schema and database provided in the connection parameter, then DLSync will create these tables. 
Please make sure the role provided in the connection has the necessary privileges to create tables in the schema. 

**_N.B: Since DLSync uses these tables to track the changes, it is recommended not to delete or change these tables. It is also import not change the schema of the connection. If DLSync is not able to find these tables in the schema, it will create them and assume as if it is running first time._**

This tool uses the following tables to store important information:
### dl_sync_script_history
This table store the meta data for script files. It contains the following columns:
```
script_id: # for state script the script name, for migration script script name plus the version number
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
This table stores the logs of the each script activity. It contains the following columns:
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
To explore the tool you can use the example scripts provided in the directory `example_scripts` .
