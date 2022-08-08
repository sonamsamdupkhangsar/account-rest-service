drop table if exists Account;
drop table if exists Password_Secret;
CREATE TABLE if not exists Account (id UUID PRIMARY KEY, authentication_id varchar, email varchar, active boolean, access_date_time timestamp);
create table if not exists Password_Secret(authentication_id varchar primary key, user_id uuid, secret varchar, expire_date timestamp);