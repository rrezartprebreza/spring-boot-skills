---
name: multi-module-maven
description: >
  Use when working in a multi-module Maven project. Covers parent POM conventions,
  shared dependency management, inter-module rules, build ordering, and Spring Boot 4
  modular starter selection.
---

# Multi-Module Maven

## Typical Structure

```
my-app/
├── pom.xml                  ← Parent POM (packaging = pom)
├── my-app-domain/           ← Pure Java domain — no Spring
│   └── pom.xml
├── my-app-application/      ← Use cases — depends on domain
│   └── pom.xml
├── my-app-infrastructure/   ← JPA, Redis, HTTP clients
│   └── pom.xml
└── my-app-web/              ← Spring Boot app, REST — depends on all above
    └── pom.xml
```

## Parent POM

```xml
<!-- pom.xml (parent) -->
<project>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>my-app-domain</module>
        <module>my-app-application</module>
        <module>my-app-infrastructure</module>
        <module>my-app-web</module>
    </modules>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
    </parent>

    <!-- Shared versions — all modules inherit -->
    <properties>
        <java.version>21</java.version>
        <mapstruct.version>1.6.0</mapstruct.version>
        <testcontainers.version>1.19.8</testcontainers.version>
    </properties>

    <!-- Dependency management — centralizes versions, NOT adding to classpath -->
    <dependencyManagement>
        <dependencies>
            <!-- Inter-module dependencies -->
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>my-app-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>my-app-application</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- Third-party versions -->
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- Shared plugins -->
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </path>
                            <path>
                                <groupId>org.mapstruct</groupId>
                                <artifactId>mapstruct-processor</artifactId>
                                <version>${mapstruct.version}</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

## Child Module POM (domain — no Spring)

```xml
<project>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>my-app</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>my-app-domain</artifactId>

    <dependencies>
        <!-- No Spring. No JPA. Pure Java only. -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

## Child Module POM (web — the runnable app)

```xml
<project>
    <parent>...</parent>
    <artifactId>my-app-web</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>my-app-application</artifactId>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>my-app-infrastructure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Only in the runnable module, not parent -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## Dependency Rules

| Module | Can depend on | Cannot depend on |
|--------|---------------|------------------|
| `domain` | Nothing | Everything |
| `application` | `domain` | `infrastructure`, `web` |
| `infrastructure` | `domain`, `application` | `web` |
| `web` | All modules | — |

## Boot 4 Modular Starters

Spring Boot 4 splits the framework into focused modules (`spring-boot-<technology>`, root
package `org.springframework.boot.<technology>`). Pick one starter per technology, per module:

- Several starters were renamed: `spring-boot-starter-web` → `spring-boot-starter-webmvc`,
  `spring-boot-starter-oauth2-client` → `spring-boot-starter-security-oauth2-client` (and the other
  OAuth2 starters gained the `security-` prefix); `spring-boot-starter-data-jpa` keeps its name
- Starters follow `spring-boot-starter-<technology>`; test starters follow
  `spring-boot-starter-<technology>-test` (no plain `spring-boot-starter-test` needed alongside them)
- Flyway/Liquibase no longer arrive transitively — add `spring-boot-starter-flyway` /
  `spring-boot-starter-liquibase` in the module that owns migrations (usually `infrastructure`)
- AOP starter is `spring-boot-starter-aspectj` (renamed from `spring-boot-starter-aop`)
- WAR deploys to external Tomcat use `spring-boot-starter-tomcat-runtime`
- `spring-boot-starter-classic` / `spring-boot-starter-test-classic` bundle the old monolithic
  set — transitional only, don't use in new modules
- Optional Maven dependencies are excluded from the repackaged jar by default — set
  `<includeOptional>true</includeOptional>` on `spring-boot-maven-plugin` if you rely on them

## Gotchas
- Agent puts `spring-boot-maven-plugin` in parent POM — only in the runnable module
- Agent adds `<dependencies>` in parent instead of `<dependencyManagement>` — adds to all modules' classpath
- Agent creates circular dependencies between modules — enforce the dependency direction above
- Agent imports Spring in `domain` module — domain must be framework-free
- Agent uses `${project.version}` for inter-module versions — correct, but update parent version to update all
- Agent adds `spring-boot-starter-web` — renamed `spring-boot-starter-webmvc` in Boot 4
- Agent adds `spring-boot-starter-aop` — renamed `spring-boot-starter-aspectj` in Boot 4
- Agent adds bare `flyway-core`/`liquibase-core` expecting Boot to configure them — use `spring-boot-starter-flyway` / `spring-boot-starter-liquibase`
- Agent adds `spring-boot-starter-test` next to `spring-boot-starter-webmvc-test` — the `-test` starters are self-contained in Boot 4
- Agent reaches into auto-configuration classes from shared modules — their members are no longer public API in Boot 4
