FROM gradle:8.10.2-jdk17 AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon clean war

FROM tomcat:9.0-jdk17-temurin-jammy

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /workspace/build/libs/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["sh", "-c", "sed -i \"s/port=\\\"8080\\\" protocol=\\\"HTTP\\/1.1\\\"/port=\\\"${PORT:-8080}\\\" protocol=\\\"HTTP\\/1.1\\\"/\" /usr/local/tomcat/conf/server.xml && exec catalina.sh run"]
