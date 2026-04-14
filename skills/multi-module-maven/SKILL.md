---
name: multi-module-maven
description: >
  Use when working in a multi-module Maven project. Covers parent POM conventions,
  shared dependency management, inter-module rules, and build ordering.
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
        <version>3.3.0</version>
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
            <artifactId>spring-boot-starter-web</artifactId>
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

## Gotchas
- Agent puts `spring-boot-maven-plugin` in parent POM — only in the runnable module
- Agent adds `<dependencies>` in parent instead of `<dependencyManagement>` — adds to all modules' classpath
- Agent creates circular dependencies between modules — enforce the dependency direction above
- Agent imports Spring in `domain` module — domain must be framework-free
- Agent uses `${project.version}` for inter-module versions — correct, but update parent version to update all
