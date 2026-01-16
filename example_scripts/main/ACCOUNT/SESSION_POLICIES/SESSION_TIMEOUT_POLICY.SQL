CREATE OR REPLACE SESSION POLICY session_timeout_policy
  SESSION_IDLE_TIMEOUT_MINS = 60
  SESSION_UI_IDLE_TIMEOUT_MINS = 15
  COMMENT = 'Session timeout policy for security';
