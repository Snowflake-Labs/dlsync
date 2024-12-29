CREATE OR REPLACE PROCEDURE ${EXAMPLE_DB}.${MAIN_SCHEMA}.UPDATE_ORDER_SUMMARY()
  returns string not null
  language python
  runtime_version = '3.8'
  packages = ('snowflake-snowpark-python')
  handler = 'main'
  execute as caller
as
$$
def main(snowpark_session):

  ## Read the command into a Snowflake dataframe
  results_df = snowpark_session.sql("select * from ${EXAMPLE_DB}.${MAIN_SCHEMA}.USER_ORDER_SUMMARY")

  ## Write the results of the dataframe into a target table
  results_df.write.mode("overwrite").save_as_table("${EXAMPLE_DB}.${MAIN_SCHEMA}.MATERILIZED_ORDER_SUMMARY")

  return f"Succeeded: Results inserted into table ${EXAMPLE_DB}.${MAIN_SCHEMA}.MATERILIZED_ORDER_SUMMARY"
$$
;