CREATE OR REPLACE SEMANTIC VIEW ${EXAMPLE_DB}.${MAIN_SCHEMA}.SALES_ANALYTICS
  TABLES (
    products AS ${EXAMPLE_DB}.${MAIN_SCHEMA}.PRODUCTS
      PRIMARY KEY (ID)
      COMMENT = 'Product catalog table',
    orders AS ${EXAMPLE_DB}.${MAIN_SCHEMA}.ORDERS
      PRIMARY KEY (ID)
      COMMENT = 'Customer orders table'
  )
  RELATIONSHIPS (
    orders (PRODUCT_ID) REFERENCES products
  )
  DIMENSIONS (
    products.product_name AS products.PRODUCT_NAME
      WITH SYNONYMS = ('product', 'item')
      COMMENT = 'Name of the product',
    orders.order_date AS orders.ORDER_DATE
      WITH SYNONYMS = ('date', 'purchase date')
      COMMENT = 'Date when order was placed'
  )
  METRICS (
    orders.total_quantity AS SUM(orders.QUANTITY)
      WITH SYNONYMS = ('quantity', 'units sold')
      COMMENT = 'Total quantity ordered',
    orders.order_count AS COUNT(orders.ID)
      WITH SYNONYMS = ('number of orders', 'count')
      COMMENT = 'Total number of orders'
  )
  COMMENT = 'Semantic view for sales analytics';
