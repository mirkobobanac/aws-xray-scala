#!/bin/bash

export MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
export MYSQL_PORT=${MYSQL_PORT:-3306}
export MYSQL_USER=${MYSQL_USER:-root}
export MYSQL_PASSWORD=${MYSQL_PASSWORD:-}
export MYSQL_DB_NAME=${MYSQL_DB_NAME:-aws_xray_test}

echo "DROP DATABASE IF EXISTS $MYSQL_DB_NAME" | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER"
echo "CREATE DATABASE $MYSQL_DB_NAME" | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER"
echo 'CREATE TABLE users ( id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id) )' | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DB_NAME"

sbt test