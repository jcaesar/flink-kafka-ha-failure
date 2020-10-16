# Beware that this dockerfile is made to be run from the parent directory.
# keep the last steps in sync with usejar (if only docker-compose had functionality to build images separately from services)

FROM maven:3.5.3-jdk-8 as build

WORKDIR /core
ARG http_proxy
ARG https_proxy
ARG mavensonatypenexus

COPY ./pom.xml ./docker/configuremavenproxy ./
RUN mkdir ~/.m2 && ./configuremavenproxy >~/.m2/settings.xml \
	&& mkdir -p src/test/java/ \
	&& echo 'public class Dummy { void dumm() { } }' >> src/test/java/Dummy.java \
	&& mvn -B clean package \
	&& rm -rf target src
COPY . .
RUN mvn -B clean package \
	&& mv target/electric* /core/job.jar

FROM maven:3.5.3-jdk-8
RUN apt-get update && apt-get -y install --no-install-recommends kafkacat && rm -rf /lib/dpkg

RUN mkdir /job && useradd -d /job job && chown job:job /job
USER job

WORKDIR /job/
ENTRYPOINT ["entrypoint"]

COPY docker/entrypoint /usr/local/bin/
COPY --from=build /core/job.jar /job/
