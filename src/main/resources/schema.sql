DROP TABLE IF EXISTS Customer;
CREATE TABLE customer (id SERIAL PRIMARY KEY, first_name VARCHAR(255), last_name VARCHAR(255));
DROP TABLE IF EXISTS Account;
CREATE TABLE Account (id UUID PRIMARY KEY, user_id UUID, active boolean, access_date_time datetime);