FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# === Etape 1 : telecharger les deps Maven (mise en cache tant que pom.xml ne change pas) ===
# Cette layer est reutilisee a chaque build qui ne touche pas au pom.xml -> gain ~2 min.
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# === Etape 2 : build avec les sources ===
# Re-execute uniquement si src/ change. Les deps sont deja la.
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# === Stage final : image runtime ===
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
