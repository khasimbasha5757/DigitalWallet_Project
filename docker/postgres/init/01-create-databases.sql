SELECT 'CREATE DATABASE digital_wallet_auth'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digital_wallet_auth')\gexec

SELECT 'CREATE DATABASE digital_wallet_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digital_wallet_user')\gexec

SELECT 'CREATE DATABASE digital_wallet_wallet'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digital_wallet_wallet')\gexec

SELECT 'CREATE DATABASE digital_wallet_tx'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digital_wallet_tx')\gexec

SELECT 'CREATE DATABASE digital_wallet_rewards'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digital_wallet_rewards')\gexec

SELECT 'CREATE DATABASE digital_wallet_notification'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digital_wallet_notification')\gexec
