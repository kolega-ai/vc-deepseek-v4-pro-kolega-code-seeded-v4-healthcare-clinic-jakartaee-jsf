# ---------------------------------------------------------------------
# Oakfield Clinic Patient Portal - WildFly runtime image
# Stage 1: build the WAR with the committed Maven wrapper (runs unit tests)
# Stage 2: assemble WildFly + PostgreSQL driver + datasource config
# ---------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -e -ntp -q verify

FROM eclipse-temurin:21-jre-jammy
ENV WILDFLY_VERSION=32.0.1.Final \
    WILDFLY_HOME=/opt/wildfly \
    CLINIC_DATA_DIR=/opt/clinic-data
ARG POSTGRES_DRIVER_VERSION=42.7.4

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home --uid 1001 --shell /bin/bash jboss

RUN curl -fsSL -o /tmp/wildfly.zip \
      "https://github.com/wildfly/wildfly/releases/download/${WILDFLY_VERSION}/wildfly-${WILDFLY_VERSION}.zip" \
 && unzip -q /tmp/wildfly.zip -d /opt \
 && rm /tmp/wildfly.zip \
 && mv "/opt/wildfly-${WILDFLY_VERSION}" "${WILDFLY_HOME}"

# PostgreSQL JDBC driver module
COPY docker/wildfly-modules/ ${WILDFLY_HOME}/modules/system/layers/base/
RUN curl -fsSL -o ${WILDFLY_HOME}/modules/system/layers/base/org/postgresql/main/postgresql-${POSTGRES_DRIVER_VERSION}.jar \
      "https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_DRIVER_VERSION}/postgresql-${POSTGRES_DRIVER_VERSION}.jar"

# Register the driver and the ClinicDS datasource (embedded CLI, offline)
COPY docker/configure.cli /tmp/configure.cli
RUN cd ${WILDFLY_HOME} && bin/jboss-cli.sh --file=/tmp/configure.cli && rm /tmp/configure.cli

# Deploy the application
COPY --from=build /build/target/clinic-portal.war ${WILDFLY_HOME}/standalone/deployments/

RUN mkdir -p ${CLINIC_DATA_DIR}/uploads ${CLINIC_DATA_DIR}/exports ${CLINIC_DATA_DIR}/imports \
 && chown -R jboss:jboss ${WILDFLY_HOME} ${CLINIC_DATA_DIR}

USER jboss
EXPOSE 8080
CMD ["/opt/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
