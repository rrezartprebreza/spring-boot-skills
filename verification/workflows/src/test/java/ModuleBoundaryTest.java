import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import static org.junit.jupiter.api.Assertions.*;

class ModuleBoundaryTest {
    @Test void publicModuleApiDependencyPasses() {
        var modules = ApplicationModules.of("fixtures.valid");
        assertEquals(2, modules.stream().count(), "must discover both fixture modules");
        assertDoesNotThrow(() -> { modules.verify(); });
    }

    @Test void accessingAnotherModulesInternalTypeFails() {
        var modules = ApplicationModules.of("fixtures.invalid");
        assertEquals(2, modules.stream().count());
        var violation = assertThrows(Violations.class, modules::verify);
        assertTrue(violation.getMessage().contains("InternalInventory"),
            "failure must concern the deliberate internal-type access");
    }
}
