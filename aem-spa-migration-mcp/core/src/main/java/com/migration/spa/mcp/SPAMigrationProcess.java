package com.migration.spa.mcp;

import com.adobe.acs.commons.fam.ActionManager;
import com.adobe.acs.commons.mcp.ProcessDefinition;
import com.adobe.acs.commons.mcp.ProcessInstance;
import com.adobe.acs.commons.mcp.form.CheckboxComponent;
import com.adobe.acs.commons.mcp.form.FormField;
import com.adobe.acs.commons.mcp.form.PathfieldComponent;
import com.adobe.acs.commons.mcp.form.TextfieldComponent;
import com.migration.spa.services.ComponentMigrationService;
import com.migration.spa.services.ContentTypeMigrationService;
import com.migration.spa.services.ProjectDetectionService;
import com.migration.spa.services.ResourceTypeMappingService;
import com.migration.spa.services.SlingModelScannerService;
import com.migration.spa.services.SPAComponentGeneratorService;
import com.migration.spa.services.TemplateMigrationService;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ACS Commons MCP ProcessDefinition that drives the SPA migration workflow.
 *
 * <p>Can migrate ANY AEM project to a SPA project without manual OSGi configuration:
 * <ol>
 *   <li>Auto-detects the source app namespace from the /apps tree</li>
 *   <li>Derives the target SPA namespace (source + "-spa")</li>
 *   <li>Computes source→target resource type mappings dynamically</li>
 *   <li>Creates real {@code cq:Component} proxy nodes in /apps for the SPA namespace</li>
 *   <li>Rewrites sling:resourceType in content, templates and policies</li>
 * </ol>
 */
public class SPAMigrationProcess extends ProcessDefinition {

    private static final Logger log = LoggerFactory.getLogger(SPAMigrationProcess.class);

    public static final String PROCESS_NAME = "SPA Migration Process";

    private static final String PROP_RESOURCE_TYPE  = "sling:resourceType";
    private static final String PROP_PRIMARY_TYPE   = "jcr:primaryType";
    private static final String TYPE_CQ_PAGE        = "cq:Page";
    private static final String TYPE_NT_UNSTRUCTURED = "nt:unstructured";
    private static final String REPORT_BASE_PATH    = "/var/spa-migration/reports";
    private static final int    COMMIT_BATCH_SIZE   = 100;

    // -------------------------------------------------------------------------
    // Form Fields
    // -------------------------------------------------------------------------

    @FormField(
            name = "Content Root Path",
            description = "JCR path to scan and migrate (e.g. /content/wknd or /content/mysite)",
            required = true,
            component = PathfieldComponent.class,
            options = {"default=/content"}
    )
    public String contentRootPath = "/content";

    @FormField(
            name = "Apps Root Path",
            description = "JCR /apps path to scan for component definitions. "
                    + "Use /apps to scan all modules, or /apps/{module} (e.g. /apps/wknd) "
                    + "to target a specific module in a multimodule project.",
            required = true,
            component = PathfieldComponent.class,
            options = {"default=/apps"}
    )
    public String appsRootPath = "/apps";

    @FormField(
            name = "Conf Root Path",
            description = "JCR /conf path to scan for editable templates and policies. "
                    + "Use /conf/{site} (e.g. /conf/wknd) to scope template and policy "
                    + "migration to a single site in a multimodule project.",
            required = false,
            component = PathfieldComponent.class,
            options = {"default=/conf"}
    )
    public String confRootPath = "/conf";

    @FormField(
            name = "Source App Namespace",
            description = "Source project namespace (e.g. 'wknd'). Leave blank to auto-detect from /apps.",
            component = TextfieldComponent.class,
            options = {"default="}
    )
    public String sourceAppNamespace = "";

    @FormField(
            name = "Target App Namespace",
            description = "SPA target namespace (e.g. 'wknd-spa'). Leave blank to auto-derive (source + '-spa').",
            component = TextfieldComponent.class,
            options = {"default="}
    )
    public String targetAppNamespace = "";

    @FormField(
            name = "Dry Run",
            description = "Report planned changes without writing to JCR",
            component = CheckboxComponent.class,
            options = {"default=true"}
    )
    public Boolean dryRun = true;

    @FormField(
            name = "Auto-Generate SPA Components",
            description = "Create cq:Component JCR nodes under the target namespace so pages render immediately",
            component = CheckboxComponent.class,
            options = {"default=true"}
    )
    public Boolean autoGenerateComponents = true;

    @FormField(
            name = "Generate Dynamic Mappings",
            description = "Auto-compute source→target mappings from the /apps component tree (no OSGi config needed)",
            component = CheckboxComponent.class,
            options = {"default=true"}
    )
    public Boolean generateMappings = true;

    @FormField(
            name = "Migrate Templates",
            description = "Rewrite sling:resourceType in /conf editable template structure and policy nodes",
            component = CheckboxComponent.class,
            options = {"default=true"}
    )
    public Boolean migrateTemplates = true;

    @FormField(
            name = "Migrate Sling Models",
            description = "Report Sling Models missing @Exporter (needed for SPA model.json endpoint)",
            component = CheckboxComponent.class,
            options = {"default=true"}
    )
    public Boolean migrateSlingModels = true;

    @FormField(
            name = "Generate SPA Stubs",
            description = "Create stub nodes for resource types that have no mapping (advanced)",
            component = CheckboxComponent.class,
            options = {"default=false"}
    )
    public Boolean generateSPAStubs = false;

    // -------------------------------------------------------------------------
    // Runtime state (thread-safe)
    // -------------------------------------------------------------------------

    private final List<Map<String, String>> auditResults          = new CopyOnWriteArrayList<>();
    private final AtomicInteger totalPages                        = new AtomicInteger(0);
    private final AtomicInteger totalComponents                   = new AtomicInteger(0);
    private final AtomicInteger migratedComponentsCount           = new AtomicInteger(0);
    private final AtomicInteger migratedTemplatesCount            = new AtomicInteger(0);
    private final AtomicInteger generatedComponentsCount          = new AtomicInteger(0);
    private final AtomicInteger rewrittenTemplateNodesCount       = new AtomicInteger(0);
    private final AtomicInteger rewrittenPolicyNodesCount         = new AtomicInteger(0);
    private final AtomicInteger generatedStubsCount               = new AtomicInteger(0);
    private final List<String>  modelsNeedingExporter             = new CopyOnWriteArrayList<>();
    private final List<String>  migratedTemplatesList             = new CopyOnWriteArrayList<>();
    private final Set<String>   uniqueResourceTypes               = Collections.synchronizedSet(new HashSet<>());

    /** Populated by detectSourceProject; used by all downstream steps. */
    private volatile ProjectDetectionService.ProjectProfile detectedProject;
    /** Computed merged mappings (dynamic + static). Used in migrateResourceTypes, generateComponents, etc. */
    private volatile Map<String, String>                    computedMappings = Collections.emptyMap();

    // -------------------------------------------------------------------------
    // Service references (injected by factory)
    // -------------------------------------------------------------------------

    private ComponentMigrationService      componentMigrationService;
    private ContentTypeMigrationService    contentTypeMigrationService;
    private SlingModelScannerService       slingModelScannerService;
    private TemplateMigrationService       templateMigrationService;
    private ResourceTypeMappingService     resourceTypeMappingService;
    private ProjectDetectionService        projectDetectionService;
    private SPAComponentGeneratorService   spaComponentGeneratorService;

    // -------------------------------------------------------------------------
    // ProcessDefinition contract
    // -------------------------------------------------------------------------

    @Override
    public void init() throws RepositoryException {
        // Fields are populated by the MCP framework from form submission.
        // Null-safe defaults ensure the process never NPEs in action steps.
        if (contentRootPath == null || contentRootPath.trim().isEmpty()) {
            contentRootPath = "/content";
        }
        if (appsRootPath == null || appsRootPath.trim().isEmpty()) {
            appsRootPath = "/apps";
        }
        if (confRootPath == null || confRootPath.trim().isEmpty()) {
            confRootPath = "/conf";
        }
        if (sourceAppNamespace == null) {
            sourceAppNamespace = "";
        }
        if (targetAppNamespace == null) {
            targetAppNamespace = "";
        }
        if (dryRun == null)                  { dryRun = true; }
        if (autoGenerateComponents == null)   { autoGenerateComponents = true; }
        if (generateMappings == null)         { generateMappings = true; }
        if (migrateTemplates == null)         { migrateTemplates = true; }
        if (migrateSlingModels == null)       { migrateSlingModels = true; }
        if (generateSPAStubs == null)         { generateSPAStubs = false; }
    }

    @Override
    public void buildProcess(ProcessInstance instance, ResourceResolver rr)
            throws LoginException, RepositoryException {
        instance.defineAction("Detect Source Project",            rr, this::detectSourceProject);
        instance.defineAction("Compute Dynamic Mappings",         rr, this::computeDynamicMappings);
        instance.defineAction("Audit Content Tree",               rr, this::auditContentTree);
        instance.defineAction("Generate SPA Component Nodes",    rr, this::generateSPAComponentNodes);
        instance.defineAction("Migrate Component Resource Types", rr, this::migrateResourceTypes);
        instance.defineAction("Migrate Template Structure Nodes", rr, this::migrateTemplateStructureNodes);
        instance.defineAction("Migrate Template Policy Nodes",    rr, this::migrateTemplatePolicyNodes);
        instance.defineAction("Scan Sling Models",                rr, this::scanSlingModels);
        instance.defineAction("Generate SPA Component Stubs",    rr, this::generateStubs);
        instance.defineAction("Produce Migration Report",         rr, this::produceMigrationReport);
    }

    @Override
    public void storeReport(ProcessInstance instance, ResourceResolver rr)
            throws RepositoryException, PersistenceException {
        // Report is persisted in produceMigrationReport action step
    }

    // -------------------------------------------------------------------------
    // Action 1: Detect Source Project
    // -------------------------------------------------------------------------

    private void detectSourceProject(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            am.setCurrentItem(appsRootPath);
            log.info("Detecting project structure. contentRoot={}, appsRoot={}", contentRootPath, appsRootPath);

            // Always run detection (even if namespaces are manually supplied) for the
            // discovered component resource types — needed by computeDynamicMappings.
            detectedProject = projectDetectionService.detectProject(rr, contentRootPath, appsRootPath);

            // If user supplied namespaces manually, honour them; otherwise use detected values.
            if (sourceAppNamespace == null || sourceAppNamespace.trim().isEmpty()) {
                sourceAppNamespace = detectedProject.getSourceAppNamespace();
            }
            if (targetAppNamespace == null || targetAppNamespace.trim().isEmpty()) {
                targetAppNamespace = detectedProject.getTargetAppNamespace();
            }

            log.info("Project: source='{}', target='{}', confidence={}, discovered {} component types",
                    sourceAppNamespace, targetAppNamespace,
                    detectedProject.getDetectionConfidence(),
                    detectedProject.getDiscoveredResourceTypes().size());
        });
    }

    // -------------------------------------------------------------------------
    // Action 2: Compute Dynamic Mappings
    // -------------------------------------------------------------------------

    private void computeDynamicMappings(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (!Boolean.TRUE.equals(generateMappings)) {
                log.info("Dynamic mapping generation disabled. Using OSGi static mappings only.");
                computedMappings = resourceTypeMappingService.getMappings();
                componentMigrationService.injectDynamicMappings(Collections.emptyMap());
                return;
            }

            if (sourceAppNamespace == null || sourceAppNamespace.trim().isEmpty()) {
                log.warn("No source namespace available. Cannot compute dynamic mappings.");
                computedMappings = resourceTypeMappingService.getMappings();
                return;
            }

            am.setCurrentItem("Namespace: " + sourceAppNamespace + " -> " + targetAppNamespace);

            Set<String> discoveredTypes = (detectedProject != null)
                    ? detectedProject.getDiscoveredResourceTypes()
                    : projectDetectionService.discoverComponentResourceTypes(rr, appsRootPath);

            Map<String, String> dynamicMappings = resourceTypeMappingService.computeDynamicMappings(
                    discoveredTypes, sourceAppNamespace, targetAppNamespace);

            // Merge: dynamic as base, static OSGi config overrides
            computedMappings = resourceTypeMappingService.getMergedMappings(dynamicMappings);

            // Inject into ComponentMigrationService so it uses these for hasMappingFor / resolveTarget
            componentMigrationService.injectDynamicMappings(dynamicMappings);

            log.info("Computed {} total mappings ({} dynamic from /apps discovery)",
                    computedMappings.size(), dynamicMappings.size());

            // Log the full mapping table at DEBUG
            if (log.isDebugEnabled()) {
                computedMappings.forEach((src, tgt) -> log.debug("  Mapping: {} -> {}", src, tgt));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Action 3: Audit Content Tree
    // -------------------------------------------------------------------------

    private void auditContentTree(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            log.info("Starting content tree audit from: {}", contentRootPath);
            Resource root = rr.getResource(contentRootPath);
            if (root == null) {
                log.error("Content root path not found: {}", contentRootPath);
                return;
            }
            traverseForAudit(root, am);
            log.info("Audit complete. Pages={}, Components={}, Unique resource types={}",
                    totalPages.get(), totalComponents.get(), uniqueResourceTypes.size());
        });
    }

    private void traverseForAudit(Resource resource, ActionManager am) {
        if (resource == null) {
            return;
        }
        am.setCurrentItem(resource.getPath());

        String primaryType = resource.getValueMap().get(PROP_PRIMARY_TYPE, String.class);

        if (TYPE_CQ_PAGE.equals(primaryType)) {
            totalPages.incrementAndGet();
            Resource jcrContent = resource.getChild("jcr:content");
            if (jcrContent != null) {
                String resourceType = jcrContent.getValueMap().get(PROP_RESOURCE_TYPE, String.class);
                String title = jcrContent.getValueMap().get("jcr:title", String.class);
                Map<String, String> pageInfo = new HashMap<>();
                pageInfo.put("path", jcrContent.getPath());
                pageInfo.put("resourceType", resourceType != null ? resourceType : "");
                pageInfo.put("title", title != null ? title : "");
                pageInfo.put("type", "page");
                auditResults.add(pageInfo);
                if (resourceType != null && !resourceType.isEmpty()) {
                    uniqueResourceTypes.add(resourceType);
                }
                auditComponents(jcrContent);
            }
        }

        Iterator<Resource> children = resource.listChildren();
        while (children.hasNext()) {
            Resource child = children.next();
            if (TYPE_CQ_PAGE.equals(child.getValueMap().get(PROP_PRIMARY_TYPE, String.class))) {
                traverseForAudit(child, am);
            }
        }
    }

    private void auditComponents(Resource resource) {
        if (resource == null) {
            return;
        }
        String primaryType = resource.getValueMap().get(PROP_PRIMARY_TYPE, String.class);
        if (TYPE_NT_UNSTRUCTURED.equals(primaryType)) {
            String resourceType = resource.getValueMap().get(PROP_RESOURCE_TYPE, String.class);
            if (resourceType != null && !resourceType.isEmpty()) {
                totalComponents.incrementAndGet();
                uniqueResourceTypes.add(resourceType);
                Map<String, String> compInfo = new HashMap<>();
                compInfo.put("path", resource.getPath());
                compInfo.put("resourceType", resourceType);
                compInfo.put("type", "component");
                auditResults.add(compInfo);
            }
        }
        Iterator<Resource> children = resource.listChildren();
        while (children.hasNext()) {
            auditComponents(children.next());
        }
    }

    // -------------------------------------------------------------------------
    // Action 4: Generate SPA Component Nodes
    // -------------------------------------------------------------------------

    private void generateSPAComponentNodes(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (!Boolean.TRUE.equals(autoGenerateComponents)) {
                log.info("SPA component node generation disabled, skipping.");
                return;
            }

            Map<String, String> mappings = computedMappings;
            if (mappings.isEmpty()) {
                log.warn("No resource type mappings available — cannot generate SPA component nodes.");
                return;
            }

            String componentGroup = spaComponentGeneratorService.deriveComponentGroup(targetAppNamespace);
            am.setCurrentItem("/apps/" + targetAppNamespace);

            log.info("Generating SPA component nodes. mappings={}, group='{}', dryRun={}",
                    mappings.size(), componentGroup, dryRun);

            try {
                int count = spaComponentGeneratorService.generateAllSPAComponents(
                        rr, mappings, componentGroup, Boolean.TRUE.equals(dryRun));
                generatedComponentsCount.set(count);
                log.info("SPA component generation complete: {} created", count);
            } catch (PersistenceException e) {
                log.error("SPA component generation failed", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Action 5: Migrate Content Resource Types
    // -------------------------------------------------------------------------

    private void migrateResourceTypes(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (Boolean.TRUE.equals(dryRun)) {
                log.info("[DRY RUN] Would rewrite resource types in {} content nodes.", auditResults.size());
                // Count how many would be migrated for the report
                for (Map<String, String> entry : auditResults) {
                    String rt = entry.get("resourceType");
                    if (rt != null && computedMappings.containsKey(rt)) {
                        migratedComponentsCount.incrementAndGet();
                    }
                }
                return;
            }

            log.info("Starting content resource type migration for {} audit entries", auditResults.size());
            int pendingCommit = 0;

            for (Map<String, String> entry : auditResults) {
                String path = entry.get("path");
                String currentResourceType = entry.get("resourceType");
                am.setCurrentItem(path);

                if (currentResourceType == null || currentResourceType.isEmpty()) {
                    continue;
                }

                // Use the dynamically computed mappings (not just the static ComponentMigrationService defaults)
                String targetResourceType = computedMappings.get(currentResourceType);
                if (targetResourceType == null) {
                    // Fall back to ComponentMigrationService (includes built-in foundation defaults)
                    targetResourceType = componentMigrationService.resolveTargetResourceType(currentResourceType);
                }
                if (targetResourceType == null) {
                    continue;
                }

                Resource resource = rr.getResource(path);
                if (resource == null) {
                    log.warn("Resource not found during migration: {}", path);
                    continue;
                }

                try {
                    ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
                    if (mvm == null) {
                        log.warn("Cannot get ModifiableValueMap for: {}", path);
                        continue;
                    }
                    mvm.put(PROP_RESOURCE_TYPE, targetResourceType);
                    migratedComponentsCount.incrementAndGet();
                    log.info("Migrated: {} -> {} at {}", currentResourceType, targetResourceType, path);

                    pendingCommit++;
                    if (pendingCommit >= COMMIT_BATCH_SIZE) {
                        rr.commit();
                        pendingCommit = 0;
                    }
                } catch (Exception e) {
                    log.error("Error migrating resource type at path: {}", path, e);
                    try { rr.revert(); } catch (Exception re) { log.error("Revert failed", re); }
                }
            }

            if (pendingCommit > 0) {
                try { rr.commit(); } catch (PersistenceException e) {
                    log.error("Final commit failed", e);
                    rr.revert();
                }
            }
            log.info("Content resource type migration complete. Migrated {} nodes.", migratedComponentsCount.get());
        });
    }

    // -------------------------------------------------------------------------
    // Action 6: Migrate Template Structure Nodes
    // -------------------------------------------------------------------------

    private void migrateTemplateStructureNodes(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (!Boolean.TRUE.equals(migrateTemplates)) {
                log.info("Template migration disabled, skipping structure node rewrite.");
                return;
            }

            if (computedMappings.isEmpty()) {
                log.warn("No mappings available for template structure migration.");
                return;
            }

            am.setCurrentItem(confRootPath + " template structure");
            log.info("Rewriting sling:resourceType in {} template structure nodes (dryRun={})", confRootPath, dryRun);
            try {
                int count = templateMigrationService.migrateTemplateResourceTypes(
                        rr, confRootPath, computedMappings, Boolean.TRUE.equals(dryRun));
                rewrittenTemplateNodesCount.set(count);
                log.info("Template structure migration: {} nodes rewritten", count);
            } catch (PersistenceException e) {
                log.error("Template structure migration failed", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Action 7: Migrate Template Policy Nodes
    // -------------------------------------------------------------------------

    private void migrateTemplatePolicyNodes(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (!Boolean.TRUE.equals(migrateTemplates)) {
                log.info("Template migration disabled, skipping policy node rewrite.");
                return;
            }

            if (computedMappings.isEmpty()) {
                log.warn("No mappings available for policy migration.");
                return;
            }

            am.setCurrentItem(confRootPath + " policies");
            log.info("Rewriting sling:resourceType in {} policy nodes (dryRun={})", confRootPath, dryRun);
            try {
                int count = templateMigrationService.migratePolicyResourceTypes(
                        rr, confRootPath, computedMappings, Boolean.TRUE.equals(dryRun));
                rewrittenPolicyNodesCount.set(count);
                log.info("Policy migration: {} nodes rewritten", count);
            } catch (PersistenceException e) {
                log.error("Policy migration failed", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Action 8: Scan Sling Models
    // -------------------------------------------------------------------------

    private void scanSlingModels(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (!Boolean.TRUE.equals(migrateSlingModels)) {
                log.info("Sling Model scanning disabled, skipping.");
                return;
            }
            am.setCurrentItem(appsRootPath);
            List<String> results = slingModelScannerService.findModelsWithoutExporter(rr, appsRootPath);
            modelsNeedingExporter.addAll(results);
            log.info("Sling Model scan: {} models need @Exporter for model.json SPA endpoint.", results.size());
        });
    }

    // -------------------------------------------------------------------------
    // Action 9: Generate SPA Component Stubs
    // -------------------------------------------------------------------------

    private void generateStubs(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            if (!Boolean.TRUE.equals(generateSPAStubs)) {
                log.info("SPA stub generation disabled, skipping.");
                return;
            }

            log.info("Generating stubs for {} resource types without mappings", uniqueResourceTypes.size());
            String stubsBase = "/apps/spa-migration/stubs";

            for (String resourceType : uniqueResourceTypes) {
                am.setCurrentItem(resourceType);
                if (computedMappings.containsKey(resourceType)) {
                    continue; // already mapped
                }

                String sanitizedName = sanitizeResourceType(resourceType);
                String stubPath = stubsBase + "/" + sanitizedName;

                if (Boolean.TRUE.equals(dryRun)) {
                    log.info("[DRY RUN] Would create stub at: {}", stubPath);
                    generatedStubsCount.incrementAndGet();
                    continue;
                }

                try {
                    ensureParentExists(rr, stubsBase);
                    if (rr.getResource(stubPath) == null) {
                        Resource base = rr.getResource(stubsBase);
                        if (base != null) {
                            Map<String, Object> props = new HashMap<>();
                            props.put(PROP_PRIMARY_TYPE, TYPE_NT_UNSTRUCTURED);
                            props.put("resourceType", resourceType);
                            props.put("spaComponent", "TODO");
                            rr.create(base, sanitizedName, props);
                            generatedStubsCount.incrementAndGet();
                        }
                    }
                } catch (PersistenceException e) {
                    log.error("Failed to create stub for: {}", resourceType, e);
                    try { rr.revert(); } catch (Exception re) { /* ignore */ }
                }
            }

            if (!Boolean.TRUE.equals(dryRun)) {
                try { rr.commit(); } catch (PersistenceException e) {
                    log.error("Failed to commit stubs", e);
                    rr.revert();
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Action 10: Produce Migration Report
    // -------------------------------------------------------------------------

    private void produceMigrationReport(ActionManager am) throws Exception {
        am.withResolver(rr -> {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String reportPath = REPORT_BASE_PATH + "/" + timestamp;
            am.setCurrentItem(reportPath);

            log.info("Writing migration report to: {}", reportPath);
            String reportStatus = Boolean.TRUE.equals(dryRun) ? "DRY_RUN" : "COMPLETE";

            try {
                ensureParentExists(rr, REPORT_BASE_PATH);
                Resource reportsBase = rr.getResource(REPORT_BASE_PATH);
                if (reportsBase == null) {
                    log.error("Could not access report base path: {}", REPORT_BASE_PATH);
                    return;
                }

                Map<String, Object> reportProps = new HashMap<>();
                reportProps.put(PROP_PRIMARY_TYPE,        TYPE_NT_UNSTRUCTURED);
                reportProps.put("report:status",          reportStatus);
                reportProps.put("timestamp",              timestamp);
                reportProps.put("dryRun",                 dryRun);
                reportProps.put("contentRootPath",        contentRootPath != null ? contentRootPath : "");
                reportProps.put("appsRootPath",           appsRootPath != null ? appsRootPath : "");
                reportProps.put("confRootPath",           confRootPath != null ? confRootPath : "");
                reportProps.put("sourceAppNamespace",     sourceAppNamespace != null ? sourceAppNamespace : "");
                reportProps.put("targetAppNamespace",     targetAppNamespace != null ? targetAppNamespace : "");
                reportProps.put("totalPages",             (long) totalPages.get());
                reportProps.put("totalComponents",        (long) totalComponents.get());
                reportProps.put("migratedComponents",     (long) migratedComponentsCount.get());
                reportProps.put("generatedComponents",    (long) generatedComponentsCount.get());
                reportProps.put("rewrittenTemplateNodes", (long) rewrittenTemplateNodesCount.get());
                reportProps.put("rewrittenPolicyNodes",   (long) rewrittenPolicyNodesCount.get());
                reportProps.put("totalMappings",          (long) computedMappings.size());
                reportProps.put("generatedStubs",         (long) generatedStubsCount.get());
                if (detectedProject != null) {
                    reportProps.put("detectionConfidence", detectedProject.getDetectionConfidence());
                }

                Resource reportResource = rr.create(reportsBase, timestamp, reportProps);

                // Store Sling Models needing @Exporter
                if (!modelsNeedingExporter.isEmpty()) {
                    Map<String, Object> mProps = new HashMap<>();
                    mProps.put(PROP_PRIMARY_TYPE, TYPE_NT_UNSTRUCTURED);
                    Resource mNode = rr.create(reportResource, "modelsNeedingExporter", mProps);
                    for (int i = 0; i < modelsNeedingExporter.size(); i++) {
                        Map<String, Object> eProps = new HashMap<>();
                        eProps.put(PROP_PRIMARY_TYPE, TYPE_NT_UNSTRUCTURED);
                        eProps.put("className", modelsNeedingExporter.get(i));
                        rr.create(mNode, "model_" + i, eProps);
                    }
                }

                // Store resource type mappings that were applied
                if (!computedMappings.isEmpty()) {
                    Map<String, Object> mapProps = new HashMap<>();
                    mapProps.put(PROP_PRIMARY_TYPE, TYPE_NT_UNSTRUCTURED);
                    Resource mapNode = rr.create(reportResource, "appliedMappings", mapProps);
                    int i = 0;
                    for (Map.Entry<String, String> e : computedMappings.entrySet()) {
                        Map<String, Object> eProps = new HashMap<>();
                        eProps.put(PROP_PRIMARY_TYPE, TYPE_NT_UNSTRUCTURED);
                        eProps.put("source", e.getKey());
                        eProps.put("target", e.getValue());
                        rr.create(mapNode, "mapping_" + i++, eProps);
                    }
                }

                rr.commit();
                log.info("Migration report written to: {}. Status={}", reportPath, reportStatus);

                // Log summary to server log
                log.info("=== SPA Migration Summary ===");
                log.info("  Source namespace  : {}", sourceAppNamespace);
                log.info("  Target namespace  : {}", targetAppNamespace);
                log.info("  Pages scanned     : {}", totalPages.get());
                log.info("  Components scanned: {}", totalComponents.get());
                log.info("  Mappings used     : {}", computedMappings.size());
                log.info("  Content migrated  : {}", migratedComponentsCount.get());
                log.info("  Components created: {}", generatedComponentsCount.get());
                log.info("  Template nodes    : {}", rewrittenTemplateNodesCount.get());
                log.info("  Policy nodes      : {}", rewrittenPolicyNodesCount.get());
                log.info("  Models needing @Exporter: {}", modelsNeedingExporter.size());
                log.info("  Status            : {}", reportStatus);
                log.info("=============================");

            } catch (PersistenceException e) {
                log.error("Failed to write migration report", e);
                try { rr.revert(); } catch (Exception re) { /* ignore */ }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void ensureParentExists(ResourceResolver rr, String path) throws PersistenceException {
        if (rr.getResource(path) != null) {
            return;
        }
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String nodeName   = path.substring(path.lastIndexOf('/') + 1);
        ensureParentExists(rr, parentPath);
        Resource parent = rr.getResource(parentPath);
        if (parent != null && rr.getResource(path) == null) {
            Map<String, Object> props = new HashMap<>();
            props.put(PROP_PRIMARY_TYPE, "sling:Folder");
            rr.create(parent, nodeName, props);
            rr.commit();
        }
    }

    private String sanitizeResourceType(String resourceType) {
        return resourceType == null ? "unknown"
                : resourceType.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // -------------------------------------------------------------------------
    // Setter injection (called by SPAMigrationProcessFactory)
    // -------------------------------------------------------------------------

    public void setComponentMigrationService(ComponentMigrationService s)     { this.componentMigrationService = s; }
    public void setContentTypeMigrationService(ContentTypeMigrationService s) { this.contentTypeMigrationService = s; }
    public void setSlingModelScannerService(SlingModelScannerService s)       { this.slingModelScannerService = s; }
    public void setTemplateMigrationService(TemplateMigrationService s)       { this.templateMigrationService = s; }
    public void setResourceTypeMappingService(ResourceTypeMappingService s)   { this.resourceTypeMappingService = s; }
    public void setProjectDetectionService(ProjectDetectionService s)         { this.projectDetectionService = s; }
    public void setSpaComponentGeneratorService(SPAComponentGeneratorService s){ this.spaComponentGeneratorService = s; }
}
