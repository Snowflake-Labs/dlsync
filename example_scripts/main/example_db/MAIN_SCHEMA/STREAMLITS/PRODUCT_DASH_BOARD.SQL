create or replace streamlit ${EXAMPLE_DB}.${MAIN_SCHEMA}.PRODUCT_DASH_BOARD
    root_location=@${EXAMPLE_DB}.${MAIN_SCHEMA}.PRODUCT_DATA_STAGE
	main_file='/streamlit_app.py'
	query_warehouse='${MY_WAREHOUSE}'
;