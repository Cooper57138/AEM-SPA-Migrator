package com.migration.spa;

import com.adobe.acs.commons.mcp.ProcessInstance;
import com.migration.spa.mcp.SPAMigrationProcess;
import com.migration.spa.services.ComponentMigrationService;
import com.migration.spa.services.ContentTypeMigrationService;
import com.migration.spa.services.ProjectDetectionService;
import com.migration.spa.services.ResourceTypeMappingService;
import com.migration.spa.services.SPAComponentGeneratorService;
import com.migration.spa.services.SlingModelScannerService;
import com.migration.spa.services.TemplateMigrationService;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SPAMigrationProcessTest {

    @Mock
    private ProcessInstance processInstance;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private ComponentMigrationService componentMigrationService;

    @Mock
    private ContentTypeMigrationService contentTypeMigrationService;

    @Mock
    private SlingModelScannerService slingModelScannerService;

    @Mock
    private TemplateMigrationService templateMigrationService;

    @Mock
    private ResourceTypeMappingService resourceTypeMappingService;

    @Mock
    private ProjectDetectionService projectDetectionService;

    @Mock
    private SPAComponentGeneratorService spaComponentGeneratorService;

    private SPAMigrationProcess process;

    @Before
    public void setUp() {
        process = new SPAMigrationProcess();
        // Factory normally calls setName() - simulate that here
        process.setName(SPAMigrationProcess.PROCESS_NAME);
        process.setComponentMigrationService(componentMigrationService);
        process.setContentTypeMigrationService(contentTypeMigrationService);
        process.setSlingModelScannerService(slingModelScannerService);
        process.setTemplateMigrationService(templateMigrationService);
        process.setResourceTypeMappingService(resourceTypeMappingService);
        process.setProjectDetectionService(projectDetectionService);
        process.setSpaComponentGeneratorService(spaComponentGeneratorService);
    }

    // --- Process name test ---

    @Test
    public void testGetName_returnsCorrectProcessName() {
        assertEquals("SPA Migration Process", process.getName());
    }

    @Test
    public void testProcessNameConstant() {
        assertEquals("SPA Migration Process", SPAMigrationProcess.PROCESS_NAME);
    }

    // --- buildProcess registers all 10 actions ---

    @Test
    public void testBuildProcess_registersAllTenActions() throws Exception {
        List<String> registeredActions = new ArrayList<>();

        doAnswer(invocation -> {
            String actionName = invocation.getArgument(0);
            registeredActions.add(actionName);
            return null;
        }).when(processInstance).defineAction(anyString(), any(ResourceResolver.class), any());

        process.buildProcess(processInstance, resourceResolver);

        assertEquals(10, registeredActions.size());
        assertTrue(registeredActions.contains("Detect Source Project"));
        assertTrue(registeredActions.contains("Compute Dynamic Mappings"));
        assertTrue(registeredActions.contains("Audit Content Tree"));
        assertTrue(registeredActions.contains("Generate SPA Component Nodes"));
        assertTrue(registeredActions.contains("Migrate Component Resource Types"));
        assertTrue(registeredActions.contains("Migrate Template Structure Nodes"));
        assertTrue(registeredActions.contains("Migrate Template Policy Nodes"));
        assertTrue(registeredActions.contains("Scan Sling Models"));
        assertTrue(registeredActions.contains("Generate SPA Component Stubs"));
        assertTrue(registeredActions.contains("Produce Migration Report"));
    }

    @Test
    public void testBuildProcess_actionsInCorrectOrder() throws Exception {
        List<String> registeredActions = new ArrayList<>();

        doAnswer(invocation -> {
            registeredActions.add(invocation.getArgument(0));
            return null;
        }).when(processInstance).defineAction(anyString(), any(ResourceResolver.class), any());

        process.buildProcess(processInstance, resourceResolver);

        assertEquals("Detect Source Project", registeredActions.get(0));
        assertEquals("Compute Dynamic Mappings", registeredActions.get(1));
        assertEquals("Audit Content Tree", registeredActions.get(2));
        assertEquals("Generate SPA Component Nodes", registeredActions.get(3));
        assertEquals("Migrate Component Resource Types", registeredActions.get(4));
        assertEquals("Migrate Template Structure Nodes", registeredActions.get(5));
        assertEquals("Migrate Template Policy Nodes", registeredActions.get(6));
        assertEquals("Scan Sling Models", registeredActions.get(7));
        assertEquals("Generate SPA Component Stubs", registeredActions.get(8));
        assertEquals("Produce Migration Report", registeredActions.get(9));
    }

    // --- dryRun field default ---

    @Test
    public void testDryRun_defaultIsTrue() throws Exception {
        Field dryRunField = SPAMigrationProcess.class.getDeclaredField("dryRun");
        dryRunField.setAccessible(true);
        Boolean dryRun = (Boolean) dryRunField.get(process);
        assertTrue("dryRun should default to true", dryRun);
    }

    @Test
    public void testContentRootPath_default() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("contentRootPath");
        field.setAccessible(true);
        assertEquals("/content", field.get(process));
    }

    @Test
    public void testAppsRootPath_default() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("appsRootPath");
        field.setAccessible(true);
        assertEquals("/apps", field.get(process));
    }

    @Test
    public void testConfRootPath_default() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("confRootPath");
        field.setAccessible(true);
        assertEquals("/conf", field.get(process));
    }

    @Test
    public void testMigrateTemplates_defaultIsTrue() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("migrateTemplates");
        field.setAccessible(true);
        assertTrue((Boolean) field.get(process));
    }

    @Test
    public void testGenerateSPAStubs_defaultIsFalse() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("generateSPAStubs");
        field.setAccessible(true);
        assertFalse((Boolean) field.get(process));
    }

    // --- Service injection ---

    @Test
    public void testServiceInjection_componentMigrationService() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("componentMigrationService");
        field.setAccessible(true);
        assertSame(componentMigrationService, field.get(process));
    }

    @Test
    public void testServiceInjection_templateMigrationService() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("templateMigrationService");
        field.setAccessible(true);
        assertSame(templateMigrationService, field.get(process));
    }

    @Test
    public void testServiceInjection_slingModelScannerService() throws Exception {
        Field field = SPAMigrationProcess.class.getDeclaredField("slingModelScannerService");
        field.setAccessible(true);
        assertSame(slingModelScannerService, field.get(process));
    }

    // --- storeReport does not throw ---

    @Test
    public void testStoreReport_doesNotThrow() throws Exception {
        process.storeReport(processInstance, resourceResolver);
        // No exception expected
    }
}
