# krakow-java-app

Java 21 streaming web app: a silent cartoon video of Krakow (pictures only, no text).
Streams with HTTP range requests (206 Partial Content).

Pipeline: GitHub -> CodePipeline -> CodeBuild (Maven + tests) -> CodeDeploy -> EC2

- Video: src/main/resources/videos/krakow.mp4
- Pictures: tools/art/ (AI-generated)
- Run locally: mvn package && java -jar target/krakow-app.jar -> http://localhost:8080
