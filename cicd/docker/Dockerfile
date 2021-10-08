FROM fantito/jdk11-maven-git:latest
EXPOSE 8080
ADD target/com.example.demo.jar    com.example.demo.jar
ENTRYPOINT ["java","-jar", "/com.example.demo.jar"]