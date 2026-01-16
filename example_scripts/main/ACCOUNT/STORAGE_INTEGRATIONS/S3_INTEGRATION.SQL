CREATE OR REPLACE STORAGE INTEGRATION s3_integration
  TYPE = 'EXTERNAL_STAGE'
  ENABLED = FALSE
  STORAGE_PROVIDER = 'S3'
  STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/snowflake-role'
  STORAGE_ALLOWED_LOCATIONS = ('s3://my-bucket/data/')
  COMMENT = 'S3 storage integration for data ingestion';
