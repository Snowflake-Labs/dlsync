---version: 0, author: DlSync
create or replace sequence ${EXAMPLE_DB}.${MAIN_SCHEMA}.ORDER_SEQ start with 1 increment by 1;
---rollback: DROP SEQUENCE IF EXISTS ${EXAMPLE_DB}.${MAIN_SCHEMA}.ORDER_SEQ;
---verify: SHOW SEQUENCES LIKE 'ORDER_SEQ' IN ${EXAMPLE_DB}.${MAIN_SCHEMA};