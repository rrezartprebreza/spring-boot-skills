// Adapt Application to the actual application root; requires spring-modulith-starter-test.
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleStructureTest {
    @Test
    void respectsModuleBoundaries() {
        ApplicationModules.of(Application.class).verify();
    }
}
