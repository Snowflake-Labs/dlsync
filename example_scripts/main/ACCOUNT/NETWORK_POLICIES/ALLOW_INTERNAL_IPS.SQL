CREATE OR REPLACE NETWORK POLICY allow_internal_ips
  ALLOWED_IP_LIST = ('10.0.0.0/8', '172.16.0.0/12')
  BLOCKED_IP_LIST = ('192.168.1.100')
  COMMENT = 'Network policy for internal access only';
