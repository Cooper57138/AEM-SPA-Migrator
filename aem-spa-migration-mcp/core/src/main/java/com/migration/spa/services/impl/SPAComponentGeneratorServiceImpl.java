package com.migration.spa.services.impl;

import com.migration.spa.services.SPAComponentGeneratorService;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(service = SPAComponentGeneratorService.class, immediate = true)
public class SPAComponentGeneratorServiceImpl implements SPAComponentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SPAComponentGeneratorServiceImpl.class);

    private static final String PROP_PRIMARY_TYPE        = "jcr:primaryType";
    private static final String PROP_TITLE               = "jcr:title";
    private static final String PROP_SUPER_TYPE          = "sling:resourceSuperType";
    private static final String PROP_COMPONENT_GROUP     = "componentGroup";
    private static final String TYPE_CQ_COMPONENT        = "cq:Component";
    private static final String TYPE_SLING_FOLDER        = "sling:Folder";
    private static final int    COMMIT_BATCH_SIZE        = 50;

    @Override
    public GenerationResult generateSPAComponent(ResourceResolver rr,
                                                 String sourceResourceType,
                                                 String targetResourceType,
                                                 String componentGroup,
                                                 boolean dryRun) throws PersistenceException {
        if (sourceResourceType == null || targetResourceType == null) {
            return new GenerationResult(GenerationStatus.FAILED, null, "Null resource type");
        }

        String targetNodePath = "/apps/" + targetResourceType;

        if (componentNodeExists(rr, targetResourceType)) {
            log.debug("Component already exists at {}, skipping.", targetNodePath);
            return new GenerationResult(GenerationStatus.ALREADY_EXISTS, targetNodePath, "Already exists");
        }

        if (dryRun) {
            log.info("[DRY RUN] Would create cq:Component at {} with superType={}", targetNodePath, sourceResourceType);
            return new GenerationResult(GenerationStatus.SKIPPED_DRY_RUN, targetNodePath, "Dry run");
        }

        // Ensure the parent folder chain exists up to the components/ level
        String parentPath = targetNodePath.substring(0, targetNodePath.lastIndexOf('/'));
        String componentName = targetNodePath.substring(targetNodePath.lastIndexOf('/') + 1);
        ensureFolderChain(rr, parentPath);

        Resource parentResource = rr.getResource(parentPath);
        if (parentResource == null) {
            log.error("Parent resource not found after ensureFolderChain: {}", parentPath);
            return new GenerationResult(GenerationStatus.FAILED, targetNodePath, "Parent not found: " + parentPath);
        }

        // Build the cq:Component node properties
        String title = deriveComponentTitle(componentName);
        Map<String, Object> props = new HashMap<>();
        props.put(PROP_PRIMARY_TYPE,    TYPE_CQ_COMPONENT);
        props.put(PROP_TITLE,           title);
        props.put(PROP_SUPER_TYPE,      sourceResourceType);
        props.put(PROP_COMPONENT_GROUP, componentGroup);

        rr.create(parentResource, componentName, props);
        log.info("Created cq:Component: {} (superType={})", targetNodePath, sourceResourceType);
        return new GenerationResult(GenerationStatus.CREATED, targetNodePath, "Created");
    }

    @Override
    public int generateAllSPAComponents(ResourceResolver rr,
                                        Map<String, String> resourceTypeMappings,
                                        String componentGroup,
                                        boolean dryRun) throws PersistenceException {
        int created = 0;
        int pending = 0;
        int failed  = 0;

        for (Map.Entry<String, String> entry : resourceTypeMappings.entrySet()) {
            String source = entry.getKey();
            String target = entry.getValue();

            // Skip built-in framework types that don't need proxy components
            if (isFrameworkType(source) || isFrameworkType(target)) {
                log.debug("Skipping framework resource type: {} -> {}", source, target);
                continue;
            }

            try {
                GenerationResult result = generateSPAComponent(rr, source, target, componentGroup, dryRun);

                if (result.status == GenerationStatus.CREATED) {
                    created++;
                    pending++;
                    if (!dryRun && pending >= COMMIT_BATCH_SIZE) {
                        rr.commit();
                        pending = 0;
                        log.info("Committed batch of {} components", COMMIT_BATCH_SIZE);
                    }
                }
            } catch (PersistenceException e) {
                failed++;
                log.error("Failed to generate component {} -> {}: {}", source, target, e.getMessage());
                try {
                    rr.revert();
                    pending = 0;
                } catch (Exception re) {
                    log.error("Revert failed after component generation error", re);
                }
            }
        }

        // Final commit
        if (!dryRun && pending > 0) {
            rr.commit();
        }

        log.info("Component generation complete: {} created, {} failed (dryRun={})", created, failed, dryRun);
        return created;
    }

    @Override
    public boolean componentNodeExists(ResourceResolver rr, String targetResourceType) {
        String path = "/apps/" + targetResourceType;
        Resource r = rr.getResource(path);
        if (r == null) {
            return false;
        }
        String primaryType = r.getValueMap().get(PROP_PRIMARY_TYPE, String.class);
        return TYPE_CQ_COMPONENT.equals(primaryType);
    }

    @Override
    public String deriveComponentGroup(String targetAppNamespace) {
        if (targetAppNamespace == null || targetAppNamespace.trim().isEmpty()) {
            return "SPA Components";
        }
        String[] tokens = targetAppNamespace.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) {
                continue;
            }
            if (i > 0) {
                sb.append(' ');
            }
            // Short acronyms (4 chars or fewer): uppercase; otherwise title-case
            if (token.length() <= 4) {
                sb.append(token.toUpperCase());
            } else {
                sb.append(Character.toUpperCase(token.charAt(0)));
                sb.append(token.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures every folder in the path chain exists, creating {@code sling:Folder}
     * nodes as needed. Stops one level above the final component node.
     */
    private void ensureFolderChain(ResourceResolver rr, String path) throws PersistenceException {
        if (rr.getResource(path) != null) {
            return; // already exists
        }
        // Ensure parent first (recursion bottoms out at /apps which always exists)
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String nodeName   = path.substring(path.lastIndexOf('/') + 1);

        ensureFolderChain(rr, parentPath);

        Resource parent = rr.getResource(parentPath);
        if (parent != null && rr.getResource(path) == null) {
            Map<String, Object> props = new HashMap<>();
            props.put(PROP_PRIMARY_TYPE, TYPE_SLING_FOLDER);
            rr.create(parent, nodeName, props);
            rr.commit(); // commit each folder immediately to avoid state issues
        }
    }

    /**
     * Derives a human-readable title from a component directory name.
     * "hero-banner" -> "Hero Banner", "text" -> "Text"
     */
    private String deriveComponentTitle(String componentName) {
        if (componentName == null || componentName.isEmpty()) {
            return "SPA Component";
        }
        String[] tokens = componentName.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String t = tokens[i];
            if (!t.isEmpty()) {
                sb.append(Character.toUpperCase(t.charAt(0)));
                sb.append(t.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /**
     * Returns true for AEM/Sling framework resource types that already have
     * real component nodes and don't need proxy components generated.
     */
    private boolean isFrameworkType(String resourceType) {
        if (resourceType == null) {
            return true;
        }
        return resourceType.startsWith("core/")
                || resourceType.startsWith("wcm/")
                || resourceType.startsWith("foundation/")
                || resourceType.startsWith("granite/")
                || resourceType.startsWith("cq/")
                || resourceType.startsWith("dam/")
                || resourceType.startsWith("acs-commons/");
    }
}
