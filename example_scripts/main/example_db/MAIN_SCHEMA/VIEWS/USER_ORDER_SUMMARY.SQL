CREATE OR REPLACE VIEW ${EXAMPLE_DB}.${MAIN_SCHEMA}.USER_ORDER_SUMMARY AS
SELECT
    U.ID AS USER_ID,
    U.USER_NAME,
    COUNT(O.ID) AS TOTAL_ORDERS,
    SUM(P.PRICE * O.QUANTITY) AS TOTAL_SPENT
FROM ${EXAMPLE_DB}.${MAIN_SCHEMA}.USERS U
LEFT JOIN ${EXAMPLE_DB}.${MAIN_SCHEMA}.ORDERS O ON U.ID = O.USER_ID
LEFT JOIN ${EXAMPLE_DB}.${MAIN_SCHEMA}.PRODUCTS P ON O.PRODUCT_ID = P.ID
GROUP BY
    U.ID, U.USER_NAME;