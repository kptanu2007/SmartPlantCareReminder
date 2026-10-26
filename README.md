# Smart Plant Care Reminder

Verdant is a local, full-stack plant-care application. Create a private account, keep a collection of houseplants, set watering intervals, record care, review reminders and history, and ask the built-in plant-care guide general questions.

## What it uses

- **Java 17 or later and Spring Boot 3.4.2** serve the application and its JSON API.
- **Spring Web, Spring Security, and Bean Validation** provide the API, server-side session authentication, CSRF protection, and request validation.
- **BCrypt** stores a one-way password hash instead of a plaintext password.
- **Jackson and a JSON snapshot** persist users, plants, reminders, and care history in one application-data file. A single snapshot makes each change commit atomically as a unit.
- **HTML, CSS, and vanilla JavaScript** provide the responsive website. Spring Boot serves it directly; no separate frontend server is needed.
- The plant-care guide is a built-in rule-based helper. It does not call a paid AI service and does not diagnose plant disease.

## Requirements

- Java 17 or newer. Java 23 was available while this project was prepared.
- Internet access for the first Maven Wrapper run (to download Maven and project dependencies) and optionally for Google Fonts. The app itself does not require a database or a separate frontend server.

## Open and run on Windows

1. Open the `SmartPlantCareReminder` folder in VS Code.
2. Open **Terminal → New Terminal** in VS Code.
3. From the project folder, start the app:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

4. Open [http://localhost:8080](http://localhost:8080).
5. Choose **Create account**, register with a full name, email, username, and password of at least 10 characters, then log in.
6. Add plants and set their watering intervals. Use the collection controls to edit, water, search, filter, or remove plants; check Reminders and Care history for the resulting records.
7. Use **Log out** in the lower-left account area. Log in again to see the same saved garden.

The wrapper can use Java from `JAVA_HOME` or from `PATH`. On the first run, it downloads Apache Maven 3.9.9 into the user's Maven Wrapper cache and Maven downloads the declared Java dependencies. No database, Docker, or separately installed Maven is needed.

To build and run the automated tests:

```powershell
.\mvnw.cmd test
```

To create a runnable package:

```powershell
.\mvnw.cmd package
java -jar .\target\smart-plant-care-reminder-1.0.0.jar
```

### Port and startup troubleshooting

- If port 8080 is already in use, start the application on another port:

  ```powershell
  $env:PORT = "8081"
  .\mvnw.cmd spring-boot:run
  ```

  Then open `http://localhost:8081`.
- If the application cannot write its data, check the Windows account's permissions on the data directory or set a writable alternate location before starting:

  ```powershell
  $env:PLANTCARE_DATA_DIR = "D:\PlantCareData"
  .\mvnw.cmd spring-boot:run
  ```

- If Java is not found or is older than 17, install/configure a Java 17+ JDK and restart the VS Code terminal.
- On the first run, allow Maven time to download dependencies. If it fails, check the network/proxy and retry the same command.

## Data, privacy, and backups

By default on Windows, application data is stored outside the project at:

```text
%LOCALAPPDATA%\SmartPlantCareReminder\data\store.json
```

The app uses the `LOCALAPPDATA` environment variable and does not hardcode a Windows user name. Set `PLANTCARE_DATA_DIR` to override the directory. The JSON snapshot contains account profile details, BCrypt password hashes, plants, reminders, and care history. API responses never include password hashes.

To back up your garden, stop the application and copy the entire `data` directory to a safe location. To restore it, stop the application and copy the backed-up directory to the same location (or set `PLANTCARE_DATA_DIR` to the restored directory) before starting the app. Keep backups private because they contain personal account information and plant notes.

If `store.json` is missing, the app creates a new empty data file. If it is malformed or unreadable, startup stops with the file path and a useful error; the app does **not** replace it with empty data. Preserve the damaged file, restore a known-good backup, or repair a copy before trying again.

The file store serializes writes inside this application process and replaces a complete snapshot using a temporary file and an atomic move when supported. It is suitable for a local college project, not a production database, multi-process or robust concurrent multi-user storage, cloud backup, or a high-availability service. Protect the computer and backups; local JSON storage does not encrypt the file. Passwords are BCrypt-hashed, but other saved content is readable by someone with filesystem access.

## Images and connectivity

Four cropped Unsplash photographs are bundled under `src/main/resources/static/images/` and served locally: `plant-leaves.jpg`, `potted-plant.jpg`, `sunlit-foliage.jpg`, and `tropical-foliage.jpg`. Plant cards use a bundled-image fallback if a saved remote image cannot load. The photographs were sourced from Unsplash (`photo-1501004318641-b39e6451bec6`, `photo-1485955900006-10f4d324d411`, `photo-1497250681960-ef046c08a56e`, and `photo-1463936575829-25148e1db1b8`). The Unsplash License does not require attribution; the source and license are documented here. Google Fonts are requested externally, with system font fallbacks provided.

## Main source files

- `src/main/java/com/smartplantcare/SmartPlantCareApplication.java` — Spring Boot entry point.
- `src/main/java/com/smartplantcare/model/PlantData.java` — user, plant, reminder, and care-history records.
- `src/main/java/com/smartplantcare/storage/JsonStore.java` — configurable path, JSON loading, synchronized transactional snapshots, and safe file replacement.
- `src/main/java/com/smartplantcare/security/` — BCrypt authentication integration, account principal, and session/CSRF security configuration.
- `src/main/java/com/smartplantcare/service/PlantCareService.java` — plant lifecycle, scheduling, dashboard calculations, care history, and the built-in guide.
- `src/main/java/com/smartplantcare/web/` — authentication, plant-care API endpoints, validation, and API error responses.
- `src/main/resources/application.properties` — server/session settings and the data-directory override.
- `src/main/resources/static/index.html` — public landing page.
- `src/main/resources/static/login.html` and `register.html` — account forms.
- `src/main/resources/static/app.html` — protected plant-care workspace.
- `src/main/resources/static/css/style.css` — shared responsive botanical styling.
- `src/main/resources/static/js/auth.js` and `app.js` — browser form handling and API interactions.
- `src/test/java/com/smartplantcare/` — persistence, service, authentication, and account-isolation tests.

## Replacing JSON storage with MySQL later

The controllers use `PlantCareService`, while persistence is isolated behind `JsonStore`; controllers do not read or write JSON files. For a future database version, introduce repository interfaces for users, plants, reminders, and care history, implement those interfaces with Spring Data JPA, and adapt the service to use them. Add database credentials through environment variables or a secret manager rather than source code. The current local project deliberately does not require MySQL or any database software.
