CREATE OR REPLACE VIEW ${EXAMPLE_DB}.${MAIN_SCHEMA}.DATA_SUMMARY AS
    SELECT ORD_SMY.ORDER_ID, STK_SMY.PRODUCT_ID, USR_ORD_SMY.USER_ID
FROM ${EXAMPLE_DB}.${MAIN_SCHEMA}.ORDER_SUMMARY ORD_SMY
LEFT OUTER JOIN ${EXAMPLE_DB}.${MAIN_SCHEMA}.STOCK_SUMMARY STK_SMY
    ON ORD_SMY.PRODUCT_ID = STK_SMY.PRODUCT_ID
LEFT OUTER JOIN ${EXAMPLE_DB}.${MAIN_SCHEMA}.USER_ORDER_SUMMARY USR_ORD_SMY
    ON ORD_SMY.USER_ID = USR_ORD_SMY.USER_ID
LEFT OUTER JOIN ${EXAMPLE_DB}.${AUDIT_SCHEMA}.PRODUCT_AUDIT PRD_ADT
    ON PRD_ADT.PRODUCT_ID = ORD_SMY.PRODUCT_ID
LEFT OUTER JOIN ${EXAMPLE_DB}.${AUDIT_SCHEMA}.USER_AUDIT USR_ADT
    ON USR_ADT.USER_ID = ORD_SMY.USER_ID;
