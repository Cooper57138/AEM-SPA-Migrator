package com.migration.spa.mcp;

import com.adobe.acs.commons.mcp.ProcessDefinition;
import com.adobe.acs.commons.mcp.ProcessDefinitionFactory;
import com.migration.spa.services.ComponentMigrationService;
import com.migration.spa.services.ContentTypeMigrationService;
import com.migration.spa.services.ProjectDetectionService;
import com.migration.spa.services.ResourceTypeMappingService;
import com.migration.spa.services.SPAComponentGeneratorService;
import com.migration.spa.services.SlingModelScannerService;
import com.migration.spa.services.TemplateMigrationService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Factory for creating SPAMigrationProcess instances.
 * Registered as a ProcessDefinitionFactory so that ACS Commons MCP
 * can discover and instantiate the process.
 */
@Component(service = ProcessDefinitionFactory.class)
public class SPAMigrationProcessFactory extends ProcessDefinitionFactory<SPAMigrationProcess> {

    @Reference
    private ComponentMigrationService componentMigrationService;

    @Reference
    private ContentTypeMigrationService contentTypeMigrationService;

    @Reference
    private SlingModelScannerService slingModelScannerService;

    @Reference
    private TemplateMigrationService templateMigrationService;

    @Reference
    private ResourceTypeMappingService resourceTypeMappingService;

    @Reference
    private ProjectDetectionService projectDetectionService;

    @Reference
    private SPAComponentGeneratorService spaComponentGeneratorService;

    @Override
    public String getName() {
        return SPAMigrationProcess.PROCESS_NAME;
    }

    @Override
    protected SPAMigrationProcess createProcessDefinitionInstance() {
        SPAMigrationProcess process = new SPAMigrationProcess();
        process.setComponentMigrationService(componentMigrationService);
        process.setContentTypeMigrationService(contentTypeMigrationService);
        process.setSlingModelScannerService(slingModelScannerService);
        process.setTemplateMigrationService(templateMigrationService);
        process.setResourceTypeMappingService(resourceTypeMappingService);
        process.setProjectDetectionService(projectDetectionService);
        process.setSpaComponentGeneratorService(spaComponentGeneratorService);
        return process;
    }
}
