FROM gradle:8.10.2-jdk17 AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon clean war

FROM tomcat:9.0.115-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
COPY --from=build /workspace/build/libs/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
