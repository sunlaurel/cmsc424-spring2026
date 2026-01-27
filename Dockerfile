FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# Update and install system dependencies
RUN apt-get -y update && \
    apt-get -y upgrade && \
    apt-get install -y python3-pip python3-dev python3-venv postgresql-16 postgresql-contrib-16 libpq-dev vim openjdk-21-jdk sudo curl wget gnupg ca-certificates git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Python packages with pinned versions
RUN pip3 install --no-cache-dir --break-system-packages \
        jupyterlab==4.3.4 \
        ipython-sql==0.5.0 \
        psycopg2-binary==2.9.10 \
        flask==3.1.0 \
        flask-restful==0.3.10 \
        flask-cors==5.0.0 \
        pymongo==4.10.1 \
        nbconvert==7.16.4

# Copy data files
ADD Assignment-0/smallRelationsInsertFile.sql \
    Assignment-0/largeRelationsInsertFile.sql \
    Assignment-0/DDL.sql \
    Assignment-0/postgresql.conf \
    /datatemp/

ADD Assignment-0/sample_analytics/customers.json \
    Assignment-0/sample_analytics/accounts.json \
    Assignment-0/sample_analytics/transactions.json \
    Assignment-0/users_with_badges.json \
    Assignment-0/posts.json \
    Assignment-0/users.csv \
    Assignment-0/posts.csv \
    Assignment-0/badges.csv \
    /datatemp/

ADD Assignment-0/zips.json /datatemp/
ADD Assignment-1/populate-se.sql /datatemp/
ADD Assignment-0/log4j2.properties /datatemp/

# Configure PostgreSQL
RUN cp /datatemp/postgresql.conf /etc/postgresql/16/main/postgresql.conf

# Set up PostgreSQL databases as postgres user
USER postgres
RUN /etc/init.d/postgresql start && \
    createdb university && \
    psql --command "\i /datatemp/DDL.sql;" university && \
    psql --command "\i /datatemp/smallRelationsInsertFile.sql;" university && \
    psql --command "alter user postgres with password 'postgres';" university && \
    psql --command "create user root;" university && \
    psql --command "alter user root with password 'root';" university && \
    psql --command "alter user root with superuser;" && \
    createdb stackexchange && \
    psql --command "\i /datatemp/populate-se.sql" stackexchange && \
    /etc/init.d/postgresql stop

# Install MongoDB 8.0 (latest stable)
USER root
RUN curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc | \
    gpg -o /usr/share/keyrings/mongodb-server-8.0.gpg --dearmor && \
    echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" | \
    tee /etc/apt/sources.list.d/mongodb-org-8.0.list && \
    apt-get update && \
    apt-get install -y mongodb-org && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Import MongoDB data
RUN (/usr/bin/mongod --config /etc/mongod.conf --fork --logpath /var/log/mongodb/mongod.log) && \
    sleep 5 && \
    mongoimport --db "analytics" --collection "customers" /datatemp/customers.json && \
    mongoimport --db "analytics" --collection "accounts" /datatemp/accounts.json && \
    mongoimport --db "analytics" --collection "transactions" /datatemp/transactions.json && \
    mongoimport --db "zips" --collection "examples" /datatemp/zips.json && \
    mongoimport --db "stackexchange" --collection "posts" /datatemp/posts.json && \
    mongoimport --db "stackexchange" --collection "users" /datatemp/users_with_badges.json && \
    mongoimport --db "zips" --collection "examples" /datatemp/zips.json && \
    mongosh --eval "db.getSiblingDB('admin').shutdownServer()" || true

# Download and install Apache Spark 3.5.4 (latest stable release)
RUN cd /tmp && \
    wget -q https://archive.apache.org/dist/spark/spark-3.5.4/spark-3.5.4-bin-hadoop3.tgz && \
    tar -xzf spark-3.5.4-bin-hadoop3.tgz && \
    mv spark-3.5.4-bin-hadoop3 /spark && \
    rm spark-3.5.4-bin-hadoop3.tgz
RUN cp /datatemp/log4j2.properties /spark/conf/


# Set environment variables
ENV SPARK_HOME=/spark
ENV PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin
ENV PYSPARK_PYTHON=python3

# Expose ports
EXPOSE 8888 5432 27017

# Create data directory for Jupyter
RUN mkdir -p /data && chmod 777 /data

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD pg_isready -U postgres && pgrep mongod > /dev/null

ENV SPARKHOME=/spark/

# Entry point - start all services
ENTRYPOINT service postgresql start && \
    (/usr/bin/mongod --config /etc/mongod.conf --fork --logpath /var/log/mongodb/mongod.log) && \
    sleep 3 && \
    (jupyter lab --port=8888 --allow-root --no-browser --ip=0.0.0.0 \
        --ServerApp.root_dir='/data' \
        --ServerApp.token='' \
        --ServerApp.password='' \
        --ServerApp.allow_origin='*' \
        2>/dev/null &) && \
    /bin/bash
