# Sonar Setup

This project is now prepared for:

- SonarQube analysis from Maven
- SonarLint in connected mode from your IDE

## What was added

- Root Maven SonarQube plugin configuration
- Root JaCoCo configuration so test coverage reports can be produced during `verify`
- Root `sonar-project.properties` for common project metadata and exclusions

## How to run SonarQube analysis

### 1. Start or use a SonarQube server

If you already have a SonarQube server, keep its URL and token ready.

If you want to run one locally with Docker:

```powershell
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community
```

Then open:

- [http://localhost:9000](http://localhost:9000)

Default first login:

- Username: `admin`
- Password: `admin`

Create a user token from:

- `My Account` -> `Security` -> `Generate Tokens`

### 2. Run analysis from the project root

From [pom.xml](C:\Users\Lenovo\Desktop\SPRINT_DIGITALWALLET\DIGITALWALLET\pom.xml):

```powershell
mvn clean verify sonar:sonar -Dsonar.token=YOUR_TOKEN
```

If your SonarQube server is not local:

```powershell
mvn clean verify sonar:sonar -Dsonar.host.url=http://YOUR_SERVER:9000 -Dsonar.token=YOUR_TOKEN
```

## How to use SonarLint in IntelliJ or VS Code

### VS Code

1. Install the `SonarLint` extension.
2. Open this project folder.
3. Open the SonarLint command palette action:
   `SonarLint: Add SonarQube Connection`
4. Enter your SonarQube URL and token.
5. Bind the workspace/project to the SonarQube project key:
   `digital-wallet-system`

### IntelliJ IDEA

1. Install the `SonarLint` plugin.
2. Open:
   `Settings` -> `Tools` -> `SonarLint`
3. Add a SonarQube connection using your server URL and token.
4. Bind the project to:
   `digital-wallet-system`

## How to verify it is working

- In the IDE, SonarLint should start showing issues inline in Java files.
- After running Maven analysis, the project should appear in SonarQube dashboard.
- Coverage reports will be generated under each module's `target/site/jacoco/` folder after `mvn clean verify`.

## What you need to do

1. Make sure SonarQube server is running, either local or shared.
2. Generate a SonarQube token.
3. Run:
   `mvn clean verify sonar:sonar -Dsonar.token=YOUR_TOKEN`
4. Install SonarLint in your IDE and connect it to the same SonarQube server.
